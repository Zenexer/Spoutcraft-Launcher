package org.spoutcraft.launcher;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import org.bukkit.util.config.Configuration;
import org.spoutcraft.launcher.async.DownloadListener;


public class MirrorUtils
{
	public static File mirrorsYML = new File(GameUpdater.workDir, "mirrors.yml");

	public static String getMirrorUrl(String mirrorUrl, String fallbackUrl, DownloadListener listener)
	{
		try
		{
			if (Main.isOffline)
			{
				return null;
			}
			
			if (isAddressReachable(mirrorUrl))
			{
				return mirrorUrl;
			}
		}
		catch (Exception ex)
		{
		}
		System.err.println("All mirrors failed, reverting to default");
		return fallbackUrl;
	}

	public static String getMirrorUrl(String mirrorURI, String fallbackUrl)
	{
		return getMirrorUrl(mirrorURI, fallbackUrl, null);
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Integer> getMirrors()
	{
		Configuration config = getMirrorsYML();
		return (Map<String, Integer>)config.getProperty("mirrors");
	}

	public static boolean isAddressReachable(String url)
	{
		try
		{
			final URLConnection urlConnection = new URL(url).openConnection();
			if (url.contains("https"))
			{
				HttpsURLConnection urlConnect = (HttpsURLConnection)urlConnection;
				urlConnect.setConnectTimeout(5000);
				urlConnect.setReadTimeout(30000);
				urlConnect.setInstanceFollowRedirects(false);
				urlConnect.setRequestMethod("HEAD");
				int responseCode = urlConnect.getResponseCode();
				urlConnect.disconnect();
				return (responseCode == HttpURLConnection.HTTP_OK);
			}
			else
			{
				HttpURLConnection urlConnect = (HttpURLConnection)urlConnection;
				urlConnect.setConnectTimeout(5000);
				urlConnect.setReadTimeout(30000);
				urlConnect.setInstanceFollowRedirects(false);
				urlConnect.setRequestMethod("HEAD");
				int responseCode = urlConnect.getResponseCode();
				urlConnect.disconnect();
				return (responseCode == HttpURLConnection.HTTP_OK);
			}
		}
		catch (Exception e)
		{
		}
		return false;
	}

	public static Configuration getMirrorsYML()
	{
		Configuration config = new Configuration(mirrorsYML);
		config.load();
		return config;
	}
}
