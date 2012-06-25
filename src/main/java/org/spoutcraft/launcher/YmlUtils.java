package org.spoutcraft.launcher;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import org.yaml.snakeyaml.Yaml;


public class YmlUtils
{
	public static boolean downloadRelativeYmlFile(String ymlUrl, String relativePath)
	{
		return downloadYmlFile(ymlUrl, null, new File(GameUpdater.workDir, relativePath));
	}

	public static boolean downloadYmlFile(String ymlUrl, String fallbackUrl, File ymlFile)
	{
		if (Main.isOffline)
		{
			return false;
		}

		GameUpdater.tempDir.mkdirs();

		if (ymlFile.exists() && MD5Utils.checksumPath(ymlUrl))
		{
			return true;
		}

		URL url = null;
		InputStream io = null;
		OutputStream out = null;
		try
		{
			ymlUrl = MirrorUtils.getMirrorUrl(ymlUrl, fallbackUrl);
			if (ymlUrl == null)
			{
				if (GameUpdater.canPlayOffline())
				{
					Main.isOffline = true;
				}
				return false;
			}

			Util.log("[Info] Downloading '%s' from '%s'.", ymlFile.getName(), ymlUrl);

			url = new URL(ymlUrl);
			URLConnection con = (url.openConnection());

			System.setProperty("http.agent", "");
			con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/534.30 (KHTML, like Gecko) Chrome/12.0.742.100 Safari/534.30");

			// Download to temporary file
			File tempFile = new File(GameUpdater.tempDir, ymlFile.getName());
			out = new BufferedOutputStream(new FileOutputStream(tempFile));

			if (GameUpdater.copy(con.getInputStream(), out) <= 0)
			{
				Util.log("Download URL was empty: '%s'/n", url);
				return false;
			}

			out.flush();

			// Test yml loading
			Yaml yamlFile = new Yaml();
			io = new BufferedInputStream(new FileInputStream(tempFile));
			yamlFile.load(io);

			// If no Exception then file loaded fine, copy to output file
			GameUpdater.copy(tempFile, ymlFile);
			tempFile.delete();

			return true;
		}
		catch (MalformedURLException e)
		{
			Util.log("Download URL badly formed: '%s'/n", url);
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (Exception e)
		{
			Util.log("Yaml File has error's badly formed: '%s'/n", url);
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if (io != null)
				{
					io.close();
				}
				if (out != null)
				{
					out.close();
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		return false;
	}
}
