/*
 * This file is part of Spoutcraft Launcher (http://wiki.getspout.org/).
 * 
 * Spoutcraft Launcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Spoutcraft Launcher is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.spoutcraft.launcher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.spoutcraft.launcher.async.Download;
import org.spoutcraft.launcher.async.DownloadListener;
import org.spoutcraft.launcher.exception.UnsupportedOSException;
import org.spoutcraft.launcher.logs.SystemConsoleListener;

import SevenZip.LzmaAlone;

public class GameUpdater implements DownloadListener {
	public static final String	LAUNCHER_DIRECTORY	= "launcher";
	public static final File		WORKING_DIRECTORY		= PlatformUtils.getWorkingDirectory();

	/* Minecraft Updating Arguments */
	public String								user								= "Player";
	public String								downloadTicket			= "1";

	/* Files */
	public static final File		binDir							= new File(WORKING_DIRECTORY, "bin");
	public static final File		cacheDir						= new File(WORKING_DIRECTORY, "cache");
	public static final File		tempDir							= new File(WORKING_DIRECTORY, "temp");
	public static final File		backupDir						= new File(WORKING_DIRECTORY, "backups");
	public static final File		workDir							= new File(WORKING_DIRECTORY, LAUNCHER_DIRECTORY);
	public static final File		savesDir						= new File(WORKING_DIRECTORY, "saves");
	public static final File		modsDir							= new File(WORKING_DIRECTORY, "mods");
	public static final File		modpackModsDir			= new File(WORKING_DIRECTORY, "mods-modpack");
	public static final File		modpackSavesDir			= new File(WORKING_DIRECTORY, "saves-modpack");
	public static final File		modconfigsDir				= new File(WORKING_DIRECTORY, "config");
	public static final File		resourceDir					= new File(WORKING_DIRECTORY, "resources");

	/* Minecraft Updating Arguments */
	public final String					baseURL							= "http://s3.amazonaws.com/MinecraftDownload/";
	public final String					latestLWJGLURL			= "http://www.minedev.net/spout/lwjgl/";
	public final String					spoutcraftMirrors		= "http://cdn.getspout.org/mirrors.html";

	private DownloadListener		listener;

	public GameUpdater() {
	}

	public void updateMC() throws Exception {

		binDir.mkdir();
		cacheDir.mkdirs();
		// if (tempDir.exists()) FileUtils.deleteDirectory(tempDir);
		tempDir.mkdirs();

		String minecraftMD5 = MD5Utils.getMD5(FileType.minecraft);
		String jinputMD5 = MD5Utils.getMD5(FileType.jinput);
		String lwjglMD5 = MD5Utils.getMD5(FileType.lwjgl);
		String lwjgl_utilMD5 = MD5Utils.getMD5(FileType.lwjgl_util);

		ModpackBuild build = ModpackBuild.getSpoutcraftBuild();

		// Processs minecraft.jar \\
		File mcCache = new File(cacheDir, "minecraft_" + build.getMinecraftVersion() + ".jar");
		if (!mcCache.exists() || !minecraftMD5.equals(MD5Utils.getMD5(mcCache))) {
			String minecraftURL = baseURL + "minecraft.jar?user=" + user + "&ticket=" + downloadTicket;
			String output = tempDir + File.separator + "minecraft.jar";
			MinecraftDownloadUtils.downloadMinecraft(minecraftURL, output, build, listener);
		}
		copy(mcCache, new File(binDir, "minecraft.jar"));

		File nativesDir = new File(binDir.getPath(), "natives");
		nativesDir.mkdir();

		// Process other Downloads
		mcCache = new File(cacheDir, "jinput.jar");
		if (!mcCache.exists() || !jinputMD5.equals(MD5Utils.getMD5(mcCache))) {
			DownloadUtils.downloadFile(getNativesUrl() + "jinput.jar", binDir.getPath() + File.separator + "jinput.jar", "jinput.jar", jinputMD5, listener);
		} else {
			copy(mcCache, new File(binDir, "jinput.jar"));
		}

		mcCache = new File(cacheDir, "lwjgl.jar");
		if (!mcCache.exists() || !lwjglMD5.equals(MD5Utils.getMD5(mcCache))) {
			DownloadUtils.downloadFile(getNativesUrl() + "lwjgl.jar", binDir.getPath() + File.separator + "lwjgl.jar", "lwjgl.jar", lwjglMD5, listener);
		} else {
			copy(mcCache, new File(binDir, "lwjgl.jar"));
		}

		mcCache = new File(cacheDir, "lwjgl_util.jar");
		if (!mcCache.exists() || !lwjgl_utilMD5.equals(MD5Utils.getMD5(mcCache))) {
			DownloadUtils.downloadFile(getNativesUrl() + "lwjgl_util.jar", binDir.getPath() + File.separator + "lwjgl_util.jar", "lwjgl_util.jar", lwjgl_utilMD5, listener);
		} else {
			copy(mcCache, new File(binDir, "lwjgl_util.jar"));
		}

		getNatives();

		stateChanged("Extracting Files...", 0);
		// Extract Natives
		try {
			extractNatives(nativesDir, new File(GameUpdater.tempDir.getPath() + File.separator + "natives.zip"));
		} catch (FileNotFoundException inUse) {
			// If we previously loaded this dll with a failed launch, we will be
			// unable to access the files
			// This is because the previous classloader opened them with the
			// parent classloader, and while the mc classloader
			// has been gc'd, the parent classloader is still around, holding
			// the file open. In that case, we have to assume
			// the files are good, since they got loaded last time...
		}

		MinecraftYML.setInstalledVersion(build.getMinecraftVersion());
	}

	public String getNativesUrl() {
		if (SettingsUtil.isLatestLWJGL()) { return latestLWJGLURL; }
		return baseURL;
	}

	public String getNativesUrl(String fileName) {
		if (SettingsUtil.isLatestLWJGL()) { return latestLWJGLURL + fileName + ".zip"; }
		return baseURL + fileName + ".jar.lzma";
	}

	public boolean checkMCUpdate() {
		if (!GameUpdater.binDir.exists()) return true;
		if (!new File(binDir, "natives").exists()) return true;
		File minecraft = new File(binDir, "minecraft.jar");
		if (!minecraft.exists()) return true;

		File lib = new File(binDir, "jinput.jar");
		if (!lib.exists()) return true;

		lib = new File(binDir, "lwjgl.jar");
		if (!lib.exists()) return true;

		lib = new File(binDir, "lwjgl_util.jar");
		if (!lib.exists()) return true;

		ModpackBuild build = ModpackBuild.getSpoutcraftBuild();
		String installed = MinecraftYML.getInstalledVersion();
		String required = build.getMinecraftVersion();
		return !installed.equals(required);
	}

	private void extractNatives(File nativesDir, File nativesJar) throws Exception {

		if (!nativesDir.exists()) nativesDir.mkdir();

		JarFile jar = new JarFile(nativesJar);
		Enumeration<JarEntry> entries = jar.entries();

		float progressStep = 100F / jar.size();
		float progress = 0;

		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			String name = entry.getName();
			if (entry.isDirectory()) continue;
			if (name.startsWith("META-INF")) continue;
			InputStream inputStream = jar.getInputStream(entry);
			File outFile = new File(nativesDir.getPath() + File.separator + name);
			if (!outFile.exists()) outFile.createNewFile();
			OutputStream out = new FileOutputStream(new File(nativesDir.getPath() + File.separator + name));

			int read;
			byte[] bytes = new byte[1024];

			while ((read = inputStream.read(bytes)) != -1) {
				out.write(bytes, 0, read);
			}

			progress += progressStep;
			stateChanged("Extracting Files...", progress);

			inputStream.close();
			out.flush();
			out.close();
		}

	}

	// Extracts zip to the folder
	protected void extractNatives2(File nativesDir, File nativesJar) {
		String name = null;

		if (!nativesDir.exists()) nativesDir.mkdirs();

		try {
			JarFile jar = new JarFile(nativesJar);
			Enumeration<JarEntry> entries = jar.entries();

			float progressStep = 100F / jar.size();
			float progress = 0;

			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				name = entry.getName();
				if (entry.isDirectory()) {
					(new File(nativesDir.getPath() + File.separator + entry.getName())).mkdirs();
					continue;
				}
				InputStream inputStream = jar.getInputStream(entry);
				File outFile = new File(nativesDir.getPath() + File.separator + name);
				if (!outFile.exists()) outFile.createNewFile();
				OutputStream out;
				out = new FileOutputStream(new File(nativesDir.getPath() + File.separator + name));

				int read;
				byte[] bytes = new byte[1024];

				while ((read = inputStream.read(bytes)) != -1) {
					out.write(bytes, 0, read);
				}

				progress += progressStep;
				stateChanged(String.format("Extracting '%s'...", nativesJar.getName()), progress);

				inputStream.close();
				out.flush();
				out.close();
			}
			stateChanged("Extracted Files...", 100f);
		} catch (IOException e) {
			// Zip failed to extract properly"
			System.out.println(String.format("'%' failed to decompress properly for entry '%'", nativesJar.getName(), name));
			e.printStackTrace();
		}

	}

	private File getNatives() throws Exception {
		String osName = System.getProperty("os.name").toLowerCase();
		String fname;

		if (osName.contains("win")) {
			fname = "windows_natives";
		} else if (osName.contains("mac")) {
			fname = "macosx_natives";
		} else if (osName.contains("solaris") || osName.contains("sunos")) {
			fname = "solaris_natives";
		} else if (osName.contains("linux") || osName.contains("unix")) {
			fname = "linux_natives";
		} else {
			throw new UnsupportedOSException();
		}

		if (!tempDir.exists()) tempDir.mkdir();

		DownloadUtils.downloadFile(getNativesUrl(fname), tempDir.getPath() + File.separator + (!SettingsUtil.isLatestLWJGL() ? "natives.jar.lzma" : "natives.zip"));

		if (!SettingsUtil.isLatestLWJGL()) extractLZMA(GameUpdater.tempDir.getPath() + File.separator + "natives.jar.lzma", GameUpdater.tempDir.getPath() + File.separator + "natives.zip");

		return new File(tempDir.getPath() + File.separator + "natives.jar.lzma");
	}

	public void updateSpoutcraft() throws Exception {
		performBackup();
		ModpackBuild build = ModpackBuild.getSpoutcraftBuild();

		tempDir.mkdirs();
		workDir.mkdirs();

		File mcCache = new File(cacheDir, "minecraft_" + build.getMinecraftVersion() + ".jar");
		File updateMC = new File(tempDir.getPath() + File.separator + "minecraft.jar");
		if (mcCache.exists()) {
			copy(mcCache, updateMC);
		}

		File libDir = new File(binDir, "lib");
		libDir.mkdir();

		Map<String, Object> libraries = build.getLibraries();
		Iterator<Entry<String, Object>> i = libraries.entrySet().iterator();
		while (i.hasNext()) {
			Entry<String, Object> lib = i.next();
			String version = String.valueOf(lib.getValue());
			String name = lib.getKey() + "-" + version;

			File libraryFile = new File(libDir, lib.getKey() + ".jar");
			String MD5 = LibrariesYML.getMD5(lib.getKey(), version);

			if (libraryFile.exists()) {
				String computedMD5 = MD5Utils.getMD5(libraryFile);
				if (!computedMD5.equals(MD5)) {
					libraryFile.delete();
				}
			}

			if (!libraryFile.exists()) {
				String mirrorURL = "Libraries/" + lib.getKey() + "/" + name + ".jar";
				String fallbackURL = "http://spouty.org/Libraries/" + lib.getKey() + "/" + name + ".jar";
				String url = MirrorUtils.getMirrorUrl(mirrorURL, fallbackURL, this);
				Download download = DownloadUtils.downloadFile(url, libraryFile.getPath(), lib.getKey() + ".jar", MD5, this);
			}
		}

		build.install();

		// TODO: remove this once this build has been out for a few weeks
		File spoutcraftVersion = new File(GameUpdater.workDir, "versionLauncher");
		spoutcraftVersion.delete();

	}

	public boolean isSpoutcraftUpdateAvailable() {
		if (!WORKING_DIRECTORY.exists()) return true;
		if (!GameUpdater.workDir.exists()) return true;

		ModpackBuild build = ModpackBuild.getSpoutcraftBuild();

		if (!build.getBuild().equalsIgnoreCase(build.getInstalledBuild())) return true;

		File libDir = new File(binDir, "lib");
		libDir.mkdir();

		Map<String, Object> libraries = build.getLibraries();
		Iterator<Entry<String, Object>> i = libraries.entrySet().iterator();
		while (i.hasNext()) {
			Entry<String, Object> lib = i.next();
			File libraryFile = new File(libDir, lib.getKey() + ".jar");
			if (!libraryFile.exists()) { return true; }
		}
		return false;
	}

	public static long copy(InputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[1024 * 4];
		long count = 0;
		int n = 0;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
			count += n;
		}
		return count;
	}

	public static void copy(File input, File output) throws IOException {
		FileInputStream inputStream = null;
		FileOutputStream outputStream = null;
		try {
			inputStream = new FileInputStream(input);
			outputStream = new FileOutputStream(output);
			copy(inputStream, outputStream);
		} finally {
			if (inputStream != null) inputStream.close();
			if (outputStream != null) outputStream.close();
		}
	}

	public void performBackup() throws IOException {
		if (!backupDir.exists()) {
			backupDir.mkdir();
		}

		ModpackBuild build = ModpackBuild.getSpoutcraftBuild();

		File zip = new File(GameUpdater.backupDir, build.getBuild() + "-backup.zip");

		if (!zip.exists()) {
			String rootDir = WORKING_DIRECTORY + File.separator;
			HashSet<File> exclude = new HashSet<File>();
			exclude.add(GameUpdater.backupDir);
			if (!SettingsUtil.isWorldBackup()) {
				exclude.add(GameUpdater.savesDir);
				exclude.add(GameUpdater.modpackSavesDir);
			}
			exclude.add(GameUpdater.tempDir);
			exclude.add(SystemConsoleListener.logDir);

			File[] existingBackups = backupDir.listFiles();
			(new BackupCleanupThread(existingBackups)).start();
			zip.createNewFile();
			addFilesToExistingZip(zip, getFiles(WORKING_DIRECTORY, exclude, rootDir), rootDir, false);

			if (modpackModsDir.exists()) FileUtils.deleteDirectory(modpackModsDir);

			if (modsDir.exists()) FileUtils.deleteDirectory(modsDir);

			if (modconfigsDir.exists()) FileUtils.deleteDirectory(modconfigsDir);

			if (resourceDir.exists()) FileUtils.deleteDirectory(resourceDir);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean canPlayOffline() {
		try {
			String path = (String) AccessController.doPrivileged(new PrivilegedExceptionAction() {
				@Override
				public Object run() throws Exception {
					return WORKING_DIRECTORY + File.separator + "bin" + File.separator;
				}
			});
			File dir = new File(path);
			if (!dir.exists()) { return false; }

			dir = new File(dir, "minecraft.jar");
			if (!dir.exists()) { return false; }
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return false;
	}

	public void addFilesToExistingZip(File zipFile, Set<ClassFile> files, String rootDir, boolean progressBar) throws IOException {
		File tempFile = File.createTempFile(zipFile.getName(), null, zipFile.getParentFile());
		tempFile.delete();

		copy(zipFile, tempFile);
		boolean renameOk = zipFile.renameTo(tempFile);
		if (!renameOk) {
			if (tempFile.exists()) {
				zipFile.delete();
			} else {
				throw new RuntimeException("could not rename the file " + zipFile.getAbsolutePath() + " to " + tempFile.getAbsolutePath());
			}
		}
		byte[] buf = new byte[1024];

		float progress = 0F;
		float progressStep = 0F;
		if (progressBar) {
			int jarSize = new JarFile(tempFile).size();
			progressStep = 100F / (files.size() + jarSize);
		}

		ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(tempFile)));
		ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
		ZipEntry entry = zin.getNextEntry();
		while (entry != null) {
			String name = entry.getName();
			ClassFile entryFile = new ClassFile(name);
			if (!name.contains("META-INF") && !files.contains(entryFile)) {
				out.putNextEntry(new ZipEntry(name));
				int len;
				while ((len = zin.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
			}
			entry = zin.getNextEntry();

			progress += progressStep;
			if (progressBar) {
				stateChanged("Merging Modpack Files Into Minecraft Jar...", progress);
			}
		}
		zin.close();
		for (ClassFile file : files) {
			try {
				InputStream in = new FileInputStream(file.getFile());

				String path = file.getPath();
				path = path.replace(rootDir, "");
				path = path.replaceAll("\\\\", "/");
				out.putNextEntry(new ZipEntry(path));

				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}

				progress += progressStep;
				if (progressBar) {
					stateChanged("Merging Modpack Files Into Minecraft Jar...", progress);
				}

				out.closeEntry();
				in.close();
			} catch (IOException e) {
			}
		}

		out.close();
	}

	// I know that is is not the best method but screw it, I am tired of trying
	// to do it myself :P
	private void extractLZMA(String in, String out) throws Exception {
		String[] args = { "d", in, out };
		LzmaAlone.main(args);
	}

	// @SuppressWarnings("unused")
	private void extractPack(String in, String out) throws Exception {
		File f = new File(in);
		if (!f.exists()) return;

		FileOutputStream fostream = new FileOutputStream(out);
		JarOutputStream jostream = new JarOutputStream(fostream);

		Pack200.Unpacker unpacker = Pack200.newUnpacker();
		unpacker.unpack(f, jostream);
		jostream.close();

		f.delete();
	}

	public Set<ClassFile> getFiles(File dir, String rootDir) {
		return getFiles(dir, new HashSet<File>(), rootDir);
	}

	public Set<ClassFile> getFiles(File dir, Set<File> exclude, String rootDir) {
		HashSet<ClassFile> result = new HashSet<ClassFile>();
		for (File file : dir.listFiles()) {
			if (!exclude.contains(dir)) {
				if (file.isDirectory()) {
					result.addAll(this.getFiles(file, exclude, rootDir));
					continue;
				}
				result.add(new ClassFile(file, rootDir));
			}
		}
		return result;
	}

	@Override
	public void stateChanged(String fileName, float progress) {
		fileName = fileName.replace(WORKING_DIRECTORY.getPath(), "");
		this.listener.stateChanged(fileName, progress);
	}

	public void setListener(DownloadListener listener) {
		this.listener = listener;
	}
}
