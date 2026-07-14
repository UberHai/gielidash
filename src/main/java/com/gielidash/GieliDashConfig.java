package com.gielidash;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GieliDashConfig.GROUP)
public interface GieliDashConfig extends Config
{
	String GROUP = "gielidash";

	@ConfigItem(
		keyName = "enableSync",
		name = "Enable order sync",
		description = "Connect to the GieliDash server to post and browse delivery orders.<br>"
			+ "WARNING: When enabled, your account hash, display name, location, and<br>"
			+ "self-reported stats (levels, quest points, unlocks) are sent to the GieliDash server.",
		position = 0
	)
	default boolean enableSync()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Delivery overlay",
		description = "Show the active delivery (status, goods, fee, distance, ETA) as an on-screen overlay.",
		position = 1
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useShortestPath",
		name = "Route via Shortest Path",
		description = "Draw the route to the delivery destination using the Shortest Path plugin, if it is installed.",
		position = 2
	)
	default boolean useShortestPath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "baseUrlOverride",
		name = "Server URL override",
		description = "Point the plugin at a self-hosted GieliDash server. Leave blank for the official one.",
		position = 3
	)
	default String baseUrlOverride()
	{
		return "";
	}
}
