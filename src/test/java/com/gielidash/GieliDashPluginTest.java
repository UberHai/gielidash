package com.gielidash;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GieliDashPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GieliDashPlugin.class);
		RuneLite.main(args);
	}
}
