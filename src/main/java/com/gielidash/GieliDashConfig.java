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
}
