package com.gielidash;

import com.gielidash.api.ApiClient;
import com.gielidash.api.ApiException;
import com.gielidash.api.Order;
import com.gielidash.api.OrderItem;
import com.gielidash.ui.GieliDashPanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import javax.swing.SwingUtilities;

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
	private ClientThread clientThread;

	@Inject
	private GieliDashConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ApiClient api;

	@Inject
	private SessionService sessionService;

	private GieliDashPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception
	{
		panel = new GieliDashPanel(this, itemManager);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("GieliDash")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		log.debug("GieliDash started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		log.debug("GieliDash stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN && config.enableSync())
		{
			sessionService.register();
		}
	}

	/** Poll the open order book while sync is on. */
	@Schedule(period = 15, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void pollOrders()
	{
		if (!config.enableSync() || panel == null)
		{
			return;
		}
		if (!api.hasToken())
		{
			// Not registered yet - happens before first login with sync enabled
			SwingUtilities.invokeLater(() -> panel.setSyncStatus("log in to register"));
			return;
		}
		try
		{
			List<Order> orders = api.getOpenOrders();
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setOrders(orders);
					panel.setSyncStatus(orders.size() + " open");
				}
			});
		}
		catch (ApiException e)
		{
			log.debug("Order poll failed: {}", e.getMessage());
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setSyncStatus("offline");
				}
			});
		}
	}

	/** Called from the panel (EDT). Claims an order off-thread, then refreshes. */
	public void acceptOrder(Order order)
	{
		executor.execute(() ->
		{
			try
			{
				api.acceptOrder(order.getId());
				log.debug("Accepted order {}", order.getId());
			}
			catch (ApiException e)
			{
				log.warn("Accept failed for order {}: {}", order.getId(), e.getMessage());
			}
			pollOrders();
		});
	}

	/**
	 * Called from the Create tab (EDT). Reads the player's location on the client
	 * thread, then posts the order off it. Callback receives "#<id>" on success
	 * or a user-facing error message.
	 */
	public void createOrderAtMyLocation(List<OrderItem> items, long feeGp, Consumer<String> callback)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
			{
				callback.accept("Log in first - the order needs your location");
				return;
			}
			WorldPoint location = client.getLocalPlayer().getWorldLocation();
			int world = client.getWorld();

			executor.execute(() ->
			{
				try
				{
					int id = api.createOrder(items, location.getX(), location.getY(),
						location.getPlane(), world, feeGp, null);
					callback.accept("#" + id);
					pollOrders();
				}
				catch (ApiException e)
				{
					callback.accept("Posting failed: " + e.getMessage());
				}
			});
		});
	}

	@Provides
	GieliDashConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GieliDashConfig.class);
	}
}
