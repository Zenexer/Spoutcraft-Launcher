package org.spoutcraft.launcher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.spoutcraft.launcher.async.Download;
import org.spoutcraft.launcher.async.DownloadListener;


public class DownloadUtils
{
	public static Download downloadFile(String url, String output, String cacheName, String md5, DownloadListener listener) throws IOException
	{
		if (Main.isOffline)
		{
			return null;
		}
		int tries = SettingsUtil.getLoginTries();
		File outputFile = new File(output);
		File tempfile = File.createTempFile("file", null, GameUpdater.tempDir);
		tempfile.mkdirs();
		Download download = null;
		boolean areFilesIdentical = tempfile.getPath().equalsIgnoreCase(outputFile.getPath());
		while (tries > 0)
		{
			Util.logi("Starting download of '%s', with %s trie(s) remaining", url, tries);
			tries--;
			download = new Download(url, tempfile.getPath());
			download.setListener(listener);
			download.run();
			if (!download.isSuccess())
			{
				if (download.getOutFile() != null)
				{
					download.getOutFile().delete();
				}
				Util.log("Download of " + url + " Failed!");
				if (listener != null)
				{
					listener.stateChanged("Download Failed, retries remaining: " + tries, 0F);
				}
			}
			else
			{
				String fileMD5 = MD5Utils.getMD5(download.getOutFile());
				if (md5 == null || fileMD5.equals(md5))
				{
					Util.logi("Copying: %s to: %s", tempfile, outputFile);
					if (!areFilesIdentical)
					{
						GameUpdater.copy(tempfile, outputFile);
					}
					Util.logi("File Downloaded: %s", outputFile);
					break;
				}
				else if (md5 != null && !fileMD5.equals(md5))
				{
					Util.log("Expected MD5: %s Calculated MD5: %s", md5, fileMD5);
				}
			}
		}

		if (cacheName != null)
		{
			if (tempfile.exists())
			{
				GameUpdater.copy(tempfile, new File(GameUpdater.cacheDir, cacheName));
			}
			else
			{
				Util.log("Could not copy file to cache: %s", tempfile);
			}
		}

		if (!areFilesIdentical)
		{
			tempfile.delete();
		}
		return download;
	}

	public static Download downloadFile(String url, String output, String cacheName) throws IOException
	{
		return downloadFile(url, output, cacheName, null, null);
	}

	public static Download downloadFile(String url, String output) throws IOException
	{
		return downloadFile(url, output, null, null, null);
	}
	private static int filesToDownload = 0;
	private static int filesDownloaded = 0;

	public static int downloadFiles(Map<String, String> downloadFileList, long timeout, TimeUnit unit)
	{
		if (Main.isOffline)
		{
			return 0;
		}
		filesToDownload = downloadFileList.size();
		filesDownloaded = 0;

		ExecutorService es = Executors.newCachedThreadPool();
		for (final Map.Entry<String, String> file : downloadFileList.entrySet())
		{
			es.execute(new Runnable()
			{
				@Override
				public void run()
				{
					Download downloadFile = null;
					try
					{
						downloadFile = downloadFile(file.getKey(), file.getValue());
						if (downloadFile != null && downloadFile.isSuccess())
						{
							filesDownloaded++;
							return;
						}
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					Util.log("File '%s' failed to download.", downloadFile.getOutFile());
				}
			});
		}
		es.shutdown();
		try
		{
			if (es.awaitTermination(timeout, unit))
			{
				return filesDownloaded;
			}
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}

		return 0;
	}
}
