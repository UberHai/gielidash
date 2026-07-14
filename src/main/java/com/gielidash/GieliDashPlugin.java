package com.gielidash;

import com.gielidash.api.ApiClient;
import com.gielidash.api.ApiException;
import com.gielidash.api.Order;
import com.gielidash.api.OrderItem;
import com.gielidash.ui.GieliDashPanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "GieliDash",
	description = "Delivery marketplace for Gielinor - Requesters post item orders, Dashers deliver them for a gp fee",
	tags = {"delivery", "marketplace", "order", "courier", "dasher"}
)
public class GieliDashPlugin extends Plugin
{
	private static final String SHORTEST_PATH_NAMESPACE = "shortestpath";

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

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DeliveryOverlay deliveryOverlay;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private EventBus eventBus;

	private GieliDashPanel panel;
	private NavigationButton navButton;
	private BufferedImage pinIcon;

	/** The one order currently guiding overlay/pin/route. Read from any thread. */
	@Getter
	@Nullable
	private volatile Order activeOrder;

	/** Last known player position, cached on the client thread each tick. */
	@Nullable
	private volatile WorldPoint lastLocation;
	private volatile int lastWorld;

	private WorldMapPoint mapPoint;
	private boolean routeShown;

	@Override
	protected void startUp() throws Exception
	{
		panel = new GieliDashPanel(this, itemManager);
		pinIcon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");

		navButton = NavigationButton.builder()
			.tooltip("GieliDash")
			.icon(pinIcon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(deliveryOverlay);

		log.debug("GieliDash started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(deliveryOverlay);
		clearGuidance();
		activeOrder = null;
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

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getLocalPlayer() != null)
		{
			lastLocation = client.getLocalPlayer().getWorldLocation();
			lastWorld = client.getWorld();
		}
	}

	/** Poll the order book + my orders while sync is on. */
	@Schedule(period = 15, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void pollOrders()
	{
		if (!config.enableSync() || panel == null)
		{
			return;
		}
		if (!api.hasToken())
		{
			SwingUtilities.invokeLater(() -> panel.setSyncStatus("log in to register"));
			return;
		}
		try
		{
			List<Order> open = api.getOpenOrders();
			List<Order> mine = api.getMyOrders();

			Order next = mine.stream().filter(Order::isActive).findFirst().orElse(null);
			updateActiveOrder(next);

			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setOrders(open);
					panel.setMyOrders(mine);
					panel.setSyncStatus(open.size() + " open");
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

	/** Heartbeat my position to the server while I'm on an active order. */
	@Schedule(period = 5, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void sendLocationHeartbeat()
	{
		Order order = activeOrder;
		WorldPoint location = lastLocation;
		if (order == null || location == null || !config.enableSync() || !api.hasToken())
		{
			return;
		}
		try
		{
			api.sendLocation(order.getId(), location.getX(), location.getY(),
				location.getPlane(), lastWorld);
		}
		catch (ApiException e)
		{
			log.debug("Heartbeat failed: {}", e.getMessage());
		}
	}

	/** Called from the panel (EDT). Claims an order off-thread, then refreshes. */
	public void acceptOrder(Order order)
	{
		runApi("accept", () -> api.acceptOrder(order.getId()));
	}

	/** Called from the Mine tab (EDT). */
	public void updateOrderStatus(Order order, String newStatus)
	{
		runApi("status " + newStatus, () -> api.updateStatus(order.getId(), newStatus));
	}

	/** Called from the Mine tab (EDT). */
	public void cancelOrder(Order order)
	{
		runApi("cancel", () -> api.cancelOrder(order.getId(), "cancelled_from_panel"));
	}

	private void runApi(String what, Runnable call)
	{
		executor.execute(() ->
		{
			try
			{
				call.run();
			}
			catch (ApiException e)
			{
				log.warn("GieliDash {} failed: {}", what, e.getMessage());
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

	/** Reconcile overlay/map-pin/route state with the current active order. Any thread. */
	private void updateActiveOrder(@Nullable Order next)
	{
		Order previous = activeOrder;
		activeOrder = next;

		boolean changed = (previous == null) != (next == null)
			|| (previous != null && next != null
				&& (previous.getId() != next.getId() || !previous.getStatus().equals(next.getStatus())));
		if (!changed)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			clearGuidance();
			if (next == null)
			{
				return;
			}

			WorldPoint dest = new WorldPoint(next.getDestX(), next.getDestY(), next.getDestPlane());

			mapPoint = WorldMapPoint.builder()
				.worldPoint(dest)
				.image(pinIcon)
				.tooltip("GieliDash delivery #" + next.getId())
				.jumpOnClick(true)
				.snapToEdge(true)
				.name("GieliDash")
				.build();
			worldMapPointManager.add(mapPoint);

			// Only route the Dasher - the requester is being delivered to
			if (config.useShortestPath() && "dasher".equals(next.getRole()))
			{
				Map<String, Object> data = new HashMap<>();
				data.put("target", dest);
				eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, "path", data));
				routeShown = true;
			}
		});
	}

	/** Client thread only. */
	private void clearGuidance()
	{
		if (mapPoint != null)
		{
			worldMapPointManager.remove(mapPoint);
			mapPoint = null;
		}
		if (routeShown)
		{
			eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, "clear"));
			routeShown = false;
		}
	}

	@Provides
	GieliDashConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GieliDashConfig.class);
	}
}
