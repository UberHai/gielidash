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
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
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
		keyName = "hideLockedWorlds",
		name = "Hide locked total worlds",
		description = "Hide orders posted on skill-total worlds (1250/1500/2000...) your total level can't enter.",
		position = 3
	)
	default boolean hideLockedWorlds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyEvents",
		name = "Notifications",
		description = "Notify (per your RuneLite notification settings) on new requests, accepts, arrivals, deliveries and declines.",
		position = 4
	)
	default boolean notifyEvents()
	{
		return true;
	}

	@ConfigItem(
		keyName = "maxFrontCost",
		name = "Max front cost (gp)",
		description = "Hide orders whose item cost exceeds what you're willing to front as a Dasher. 0 = no limit.",
		position = 5
	)
	default int maxFrontCost()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "verifiedRequestersOnly",
		name = "Verified requesters only",
		description = "Hide orders from requesters who aren't hiscores-verified.",
		position = 6
	)
	default boolean verifiedRequestersOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minRequesterRatings",
		name = "Min requester ratings",
		description = "Hide orders from requesters with fewer than this many ratings. 0 = show everyone.",
		position = 7
	)
	default int minRequesterRatings()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "showLeaderboard",
		name = "Leaderboard tab",
		description = "Add a Board tab with weekly courier rankings (most deliveries, most gp earned, best rated).",
		position = 8
	)
	default boolean showLeaderboard()
	{
		return false;
	}

	@ConfigItem(
		keyName = "businessStats",
		name = "Business stats",
		description = "Show pro-Dasher numbers on the Stats tab: total active delivery time and gp per active hour.<br>"
			+ "Active time only counts accept-to-delivered, never idle time.",
		position = 9
	)
	default boolean businessStats()
	{
		return false;
	}

	@ConfigItem(
		keyName = "baseUrlOverride",
		name = "Server URL override",
		description = "Point the plugin at a self-hosted GieliDash server. Leave blank for the official one.",
		position = 10
	)
	default String baseUrlOverride()
	{
		return "";
	}
}
