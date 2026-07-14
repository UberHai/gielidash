package com.gielidash;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "GieliDash",
	description = "Delivery marketplace for Gielinor - Requesters post item orders, Dashers deliver them for a gp fee",
	tags = {"delivery", "marketplace", "order", "courier", "dasher"}
)
public class GieliDashPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private GieliDashConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("GieliDash started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("GieliDash stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			log.debug("Logged in on world {}", client.getWorld());
		}
	}

	@Provides
	GieliDashConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GieliDashConfig.class);
	}
}
