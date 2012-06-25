package org.spoutcraft.launcher.modpacks;

import java.awt.Image;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.swing.ImageIcon;
import org.spoutcraft.launcher.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;


public class ModPackListYML
{
	public static final File ORIGINAL_PROPERTIES = new File(GameUpdater.cacheDir, "launcher.properties");
	private static final String RESOURCES_PATH = "resources";
	private static final String ICON_ICNS = "icon.icns";
	private static final String ICON_PNG = "icon.png";
	private static final String FAVICON_PNG = "favicon.png";
	private static final String LOGO_PNG = "logo.png";
	private static final String MODPACKS_YML = "modpacks.yml";
	private static final List<String> RESOURCES = new LinkedList();
	private static final File MODPACKS_YML_FILE = new File(GameUpdater.workDir, MODPACKS_YML);
	private static final Object key = new Object();
	public static ModPackConfig config;
	private static volatile boolean updated = false;
	private static String currentModPackName = null;
	public static String currentModPackLabel = null;
	public static File currentModPackDirectory = null;
	public static Image favIcon = null;
	public static Image icon = null;
	public static Image logo = null;
	private final static Yaml yaml;
	private static volatile boolean updateRequired = true;

	static
	{
		RESOURCES.add(FAVICON_PNG);
		RESOURCES.add(LOGO_PNG);
		RESOURCES.add(getIconName());
		
		final Representer representer = new Representer();
		representer.addClassTag(ModPackConfig.class, new Tag("ModPackConfig"));
		representer.addClassTag(ModPack.class, new Tag("ModPack"));
		yaml = new Yaml(representer);
		
		update();
	}

	public static String getIconName()
	{
		if (PlatformUtils.getPlatform() == PlatformUtils.OS.windows)
		{
			return ICON_PNG;
		}
		else if (PlatformUtils.getPlatform() == PlatformUtils.OS.macos)
		{
			return ICON_ICNS;
		}
		return ICON_PNG;
	}

	public synchronized static boolean update()
	{
		if (updateRequired)
		{
			updateModPacksYMLCache();
			
			if (!MODPACKS_YML_FILE.exists())
			{
				return false;
			}
			
			Object obj;
			try
			{
				obj = yaml.load(new FileInputStream(MODPACKS_YML_FILE));
			}
			catch (FileNotFoundException ex)
			{
				Util.log(ex.toString());
				return false;
			}
			
			if (!(obj instanceof ModPackConfig))
			{
				return false;
			}
			
			config = (ModPackConfig)obj;
			updateRequired = false;
		}
		
		return true;
	}

	public static void updateModPacksYMLCache()
	{
		if (!updated)
		{
			synchronized (key)
			{
				YmlUtils.downloadRelativeYmlFile("http://mc.earth2me.com/x/technic/" + MODPACKS_YML, MODPACKS_YML);
				updated = true;
			}
		}
	}
	
	public static ModPack getCurrentModPack()
	{
		if (config.modpacks.containsKey(currentModPackName))
		{
			return config.modpacks.get(currentModPackName);
		}
		else
		{
			return null;
		}
	}
	
	public static String getCurrentModPackName()
	{
		return currentModPackName;
	}
	
	public static Map<String, ModPack> getModPacks()
	{
		return Collections.unmodifiableMap(config.modpacks);
	}

	public static void setCurrentModPack()
	{
		setCurrentModPack(SettingsUtil.getModPackSelection(), false);
		File propFile = new File(GameUpdater.modpackDir, "launcher.properties");
		if (!ORIGINAL_PROPERTIES.exists())
		{
			GameUpdater.copy(SettingsUtil.settingsFile, ORIGINAL_PROPERTIES);
		}

		if (!propFile.exists())
		{
			GameUpdater.copy(ORIGINAL_PROPERTIES, SettingsUtil.settingsFile);
			SettingsUtil.reload();
			SettingsUtil.setCurrentModPack(ModPackListYML.currentModPackName);
			GameUpdater.copy(SettingsUtil.settingsFile, propFile);
		}
		else
		{
			GameUpdater.copy(propFile, SettingsUtil.settingsFile);
			SettingsUtil.reload();
		}
	}

	public static boolean setCurrentModPack(String modPack)
	{
		return setCurrentModPack(modPack, false);
	}

	public static boolean setCurrentModPack(String name, boolean ignoreCheck)
	{
		if (name.equals(currentModPackName))
		{
			return true;
		}

		if (!ignoreCheck)
		{
			if (!config.modpacks.containsKey(name))
			{
				// Mod Pack not in list
				Util.log("ModPack '%s' not in '%s' file.", name, MODPACKS_YML);
				return false;
			}
		}

		SettingsUtil.setCurrentModPack(name);

		currentModPackName = name;
		currentModPackLabel = config.modpacks.get(name).label;

		GameUpdater.setModpackDirectory(name);

		currentModPackDirectory = new File(GameUpdater.workDir, name);
		currentModPackDirectory.mkdirs();

		ModPackYML.updateModPackYML(true);

		return true;
	}

	public static void downloadModPackResources()
	{
		downloadModPackResources(currentModPackName, currentModPackLabel, currentModPackDirectory);
	}

	public static void downloadModPackResources(String name, String label, File path)
	{
		Map<String, String> downloadFileList = getModPackResources(name, path);

		if (downloadFileList.size() > 0 && DownloadUtils.downloadFiles(downloadFileList, 30, TimeUnit.SECONDS) != downloadFileList.size())
		{
			Util.log("Could not download all resources for modpack '%s'.", label);
		}
	}

	private static Map<String, String> getModPackResources(String name, File path)
	{
		return getModPackResources(name, path, true);
	}

	private static Map<String, String> getModPackResources(String name, File path, boolean doCheck)
	{
		Map<String, String> fileMap = new HashMap<String, String>();

		for (String resource : RESOURCES)
		{
			String relativeFilePath = name + "/resources/" + resource;

			if (doCheck && MD5Utils.checksumPath(relativeFilePath))
			{
				continue;
			}

			File dir = new File(path, RESOURCES_PATH);
			dir.mkdirs();
			File file = new File(dir, resource);
			String filePath = file.getAbsolutePath();

			String fileURL = MirrorUtils.getMirrorUrl(relativeFilePath, null);
			if (fileURL == null)
			{
				continue;
			}
			fileMap.put(fileURL, filePath);
		}

		return fileMap;
	}

	public static void getAllModPackResources()
	{
		if (Main.isOffline)
		{
			return;
		}
		Map<String, String> fileMap = new HashMap<String, String>();
		for (String modPack : config.modpacks.keySet())
		{
			File modPackDir = new File(GameUpdater.workDir, modPack);
			modPackDir.mkdirs();
			fileMap.putAll(getModPackResources(modPack, modPackDir));
		}
		downloadAllFiles(fileMap);
	}

	public static void loadModpackLogos()
	{
		for (String modPack : config.modpacks.keySet())
		{
			File modPackDir = new File(GameUpdater.workDir, modPack);
			File resourcesPath = new File(modPackDir, RESOURCES_PATH);
			File modPackLogo = new File(resourcesPath, LOGO_PNG);
			if (!modPackLogo.exists())
			{
				continue;
			}
			config.modpacks.get(modPack).setIcon(new ImageIcon(Toolkit.getDefaultToolkit().getImage(modPackLogo.getAbsolutePath())));
		}
	}

	public static void downloadAllFiles(Map<String, String> fileMap)
	{
		int size = fileMap.size();
		if (size > 0 && DownloadUtils.downloadFiles(fileMap, 30, TimeUnit.SECONDS) != size)
		{
			Util.log("Could not download all files");
		}
	}

	public static ImageIcon getModPackLogo(String item)
	{
		if (config.modpacks.containsKey(item))
		{
			return config.modpacks.get(item).getIcon();
		}
		else
		{
			return null;
		}
	}
}
