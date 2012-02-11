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

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

import org.spoutcraft.launcher.gui.LoadingScreen;
import org.spoutcraft.launcher.gui.LoginForm;
import org.spoutcraft.launcher.logs.SystemConsoleListener;

import com.beust.jcommander.JCommander;

public class Main {

	static String[]					args_temp;
	public static String		build	= "0.5.0";
	public static String		currentPack;
	static File							recursion;
	public static LoginForm	loginForm;

	public Main() throws Exception {
		main(new String[0]);
	}

	public static void reboot(String memory) {
		try {
			int memorySelection = SettingsUtil.getMemorySelection();
			int mem = (1024) * memorySelection;
			if (!System.getProperty("sun.arch.data.model").contains("64") && memorySelection > 1) {
				Util.log("32-bit Vm being used. Max memory is 1.5Gb");
				mem = 1280;
			}
			String pathToJar = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			ArrayList<String> params = new ArrayList<String>();
			params.add("java"); // Linux/Mac/whatever
			if (memorySelection > 1) params.add("-Xincgc");
			if (memory.contains("-Xmx")) {
				params.add(memory);
			} else {
				params.add("-Xmx" + mem + "m");
			}

			if (PlatformUtils.getPlatform() != PlatformUtils.OS.windows) {
				params.add("-classpath");
				params.add(pathToJar);
				params.add("org.spoutcraft.launcher.Main");
			} else {
				params.add("-jar");
				params.add(String.format("\"%s\"", pathToJar.substring(1)));
			}

			params.addAll(Arrays.asList(args_temp));

			if (PlatformUtils.getPlatform() == PlatformUtils.OS.macos) {
				params.add("-Xdock:name=\"Technic Launcher\"");

				try {
					File icon = new File(PlatformUtils.getWorkingDirectory(), "launcher_icon.icns");
					GameUpdater.copy(Main.class.getResourceAsStream("/org/spoutcraft/launcher/launcher_icon.icns"), new FileOutputStream(icon));
					params.add("-Xdock:icon=" + icon.getCanonicalPath());
				} catch (Exception ignore) {
				}
			}
			ProcessBuilder pb = new ProcessBuilder(params);

			Util.log("Rebooting with %s", Arrays.toString(pb.command().toArray()));
			try {
				Process process = pb.start();
			} catch (IOException e) {
				Util.log("Failed to load reboot Process");
				e.printStackTrace();
				SettingsUtil.setMemorySelection(1024);
			}
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static boolean isDebug() {
		return java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("-agentlib:jdwp");
	}

	public static void main(String[] args) throws Exception {
		LoadingScreen ls = new LoadingScreen();
		if (!isDebug()) ls.setVisible(true);
		Options options = new Options();
		try {
			new JCommander(options, args);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		MinecraftUtils.setOptions(options);
		recursion = new File(PlatformUtils.getWorkingDirectory(), "rtemp");

		args_temp = args;
		boolean relaunch = false;
		try {
			if (!recursion.exists()) {
				relaunch = true;
			} else {
				recursion.delete();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (relaunch) {
			ls.close();
			if (SettingsUtil.getMemorySelection() < 6) {
				int mem = 1024 * SettingsUtil.getMemorySelection();
				recursion.createNewFile();
				if (isDebug()) System.exit(0);
				else reboot("-Xmx" + mem + "m");
			}
		}

		if (PlatformUtils.getPlatform() == PlatformUtils.OS.macos) {
			try {
				System.setProperty("apple.laf.useScreenMenuBar", "true");
				System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Technic Launcher");
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ignore) {
			}
		}
		PlatformUtils.getWorkingDirectory().mkdirs();
		new File(PlatformUtils.getWorkingDirectory(), "launcher").mkdir();

		SystemConsoleListener listener = new SystemConsoleListener();

		listener.initialize();

		Util.log("------------------------------------------");
		Util.log("Launcher is starting....");
		Util.log("Launcher Build: " + getBuild());

		try {
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");

			UIDefaults defaults = UIManager.getLookAndFeelDefaults();
			defaults.put("nimbusOrange", defaults.get("nimbusBase"));
			UIManager.put("ProgressBar.selectionForeground", Color.white);
			UIManager.put("ProgressBar.selectionBackground", Color.black);
		} catch (Exception e) {
			Util.log("Warning: Can't get system LnF: " + e);
		}

		if (GameUpdater.tempDir.exists()) FileUtils.cleanDirectory(GameUpdater.tempDir);

		JFrame.setDefaultLookAndFeelDecorated(true);
		loginForm = new LoginForm();
		loginForm.setLocationByPlatform(true);
		ls.close();
		loginForm.setVisible(true);
	}

	private static String getBuild() {
		if (build == null) {
			File buildInfo = new File(PlatformUtils.getWorkingDirectory(), "launcherVersion");
			if (buildInfo.exists()) {
				try {
					BufferedReader bf = new BufferedReader(new FileReader(buildInfo));
					build = bf.readLine();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return build;
	}
}
