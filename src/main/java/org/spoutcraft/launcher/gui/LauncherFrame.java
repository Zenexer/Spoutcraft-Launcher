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
package org.spoutcraft.launcher.gui;

import java.applet.Applet;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.spoutcraft.launcher.*;
import org.spoutcraft.launcher.exception.CorruptedMinecraftJarException;
import org.spoutcraft.launcher.exception.MinecraftVerifyException;
import org.spoutcraft.launcher.modpacks.ModPackListYML;


public class LauncherFrame extends JFrame implements WindowListener
{
	private static final long serialVersionUID = 4524937541564722358L;
	private MinecraftAppletEnglober minecraft;
	private LoginForm loginForm = null;
	public static final int RETRYING_LAUNCH = -1;
	public static final int ERROR_IN_LAUNCH = 0;
	public static final int SUCCESSFUL_LAUNCH = 1;
	public static final String WINDOW_TITLE = "Technic Launcher";

	public LauncherFrame()
	{
		super(WINDOW_TITLE);
		
	}

	@Override
	protected void frameInit()
	{
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension dim = new Dimension(871, 519);
		setLocation((screen.width - dim.width) / 2, (screen.height - dim.height) / 2);
		setSize(dim);
		
		setResizable(true);
		addWindowListener(this);
		setVisible(true);
		
		super.frameInit();
	}

	public void setLoginForm(LoginForm form)
	{
		loginForm = form;
	}

	public LoginForm getLoginForm()
	{
		return loginForm;
	}

	public int runGame(String user, String session, String downloadTicket, String mcpass)
	{
		Applet applet = null;
		try
		{
			applet = Launcher.getMinecraftApplet();
		}
		catch (CorruptedMinecraftJarException corruption)
		{
		}
		catch (MinecraftVerifyException verify)
		{
			OptionDialog.clearCache();
			JOptionPane.showMessageDialog(getParent(), "The minecraft installation was corrupted. \nThe minecraft installation has been cleaned. \nTry to login again. If that fails, close and \nrestart the appplication.");
			this.setVisible(false);
			this.dispose();
			return ERROR_IN_LAUNCH;
		}
		if (applet == null)
		{
			String message = "Failed to launch Launcher!";
			this.setVisible(false);
			JOptionPane.showMessageDialog(getParent(), message);
			this.dispose();
			return ERROR_IN_LAUNCH;
		}

		String launcherPath = String.format("%s/%s", PlatformUtils.APPLICATION_NAME, ModPackListYML.getCurrentModPackName());

		minecraft = new MinecraftAppletEnglober(Thread.currentThread(), applet);
		minecraft.addParameter("username", user);
		minecraft.addParameter("sessionid", session);
		minecraft.addParameter("downloadticket", downloadTicket);
		minecraft.addParameter("mppass", mcpass);
		minecraft.addParameter("spoutcraftlauncher", "true");
		minecraft.addParameter("stand-alone", String.valueOf(MinecraftUtils.getOptions().isPortable()));
		minecraft.addParameter("portable", String.valueOf(MinecraftUtils.getOptions().isPortable()));
		minecraft.addParameter("directory", launcherPath);
		Util.log("Loading Launcher from '%s'", launcherPath);
		if (MinecraftUtils.getOptions().getServer() != null)
		{
			minecraft.addParameter("server", MinecraftUtils.getOptions().getServer());
			if (MinecraftUtils.getOptions().getPort() != null)
			{
				minecraft.addParameter("port", MinecraftUtils.getOptions().getPort());
			}
		}

		applet.setStub(minecraft);

		this.add(minecraft);
		validate();

		minecraft.init();
		minecraft.setSize(getWidth(), getHeight());

		minecraft.start();

		this.setVisible(true);
		return SUCCESSFUL_LAUNCH;
	}

	@Override
	public void windowActivated(WindowEvent e)
	{
	}

	@Override
	public void windowClosed(WindowEvent e)
	{
	}

	@Override
	public void windowClosing(WindowEvent e)
	{
		if (minecraft != null)
		{
			minecraft.stop();
			try
			{
				Thread.sleep(1000L);
			}
			catch (InterruptedException ex)
			{
			}
			finally
			{
				minecraft.destroy();
				minecraft = null;
			}
		}
		
		System.out.println("Exiting Launcher");
		System.exit(0);
	}

	@Override
	public void windowDeactivated(WindowEvent e)
	{
	}

	@Override
	public void windowDeiconified(WindowEvent e)
	{
	}

	@Override
	public void windowIconified(WindowEvent e)
	{
	}

	@Override
	public void windowOpened(WindowEvent e)
	{
	}
}
