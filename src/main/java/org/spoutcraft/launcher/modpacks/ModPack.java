package org.spoutcraft.launcher.modpacks;

import javax.swing.ImageIcon;


/**
 * @author Zenexer
 */
public final class ModPack
{
	public String label;
	public String url;
	
	private transient ImageIcon icon;
	
	public ImageIcon getIcon()
	{
		return icon;
	}
	
	public void setIcon(final ImageIcon icon)
	{
		this.icon = icon;
	}
}
