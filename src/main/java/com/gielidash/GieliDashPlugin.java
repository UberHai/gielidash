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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
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
	private TradeObserver tradeObserver;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DeliveryOverlay deliveryOverlay;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private net.runelite.client.game.WorldService worldService;

	@Inject
	private net.runelite.client.Notifier notifier;

	@Inject
	private ConfigManager configManager;

	@Inject
	private com.google.gson.Gson gson;

	private GieliDashPanel panel;
	private NavigationButton navButton;
	private BufferedImage pinIcon;
	private BufferedImage dasherIcon;
	private BufferedImage requesterIcon;

	/**
	 * The PRIMARY active order - oldest accepted first ("finish what you
	 * started") - gets the full overlay, the Shortest Path route, and the
	 * counterpart pin. Read from any thread.
	 */
	@Getter
	@Nullable
	private volatile Order activeOrder;

	/** All active orders (server caps a dasher at 3). Read from any thread. */
	@Getter
	private volatile List<Order> activeOrders = List.of();

	/** Poll-diff signature so guidance only rebuilds when something changed. */
	private volatile String activeSignature = "";

	/** Last known player position, cached on the client thread each tick. */
	@Getter
	@Nullable
	private volatile WorldPoint lastLocation;
	private volatile int lastWorld;

	/** Board tab fetch throttle (30s) - the tab refetches on every select. */
	private volatile long lastLeaderboardFetch;

	/** For panel-side guards (EDT can't touch Client). */
	@Getter
	private volatile boolean loggedIn;

	/** Cached on GameTick for off-thread total-world checks. */
	private volatile int myTotalLevel;

	/** Previous poll snapshots for event notifications. */
	private Map<Integer, Order> prevMine;
	private java.util.Set<Integer> prevRequestIds;

	/** One destination pin per active order. Client thread only. */
	private final List<WorldMapPoint> destPoints = new java.util.ArrayList<>();
	private WorldMapPoint counterpartPoint;
	private boolean routeShown;

	@Override
	protected void startUp() throws Exception
	{
		panel = new GieliDashPanel(this, itemManager);
		pinIcon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		// Map-icon language: brand red-orange = destination, green = the moving
		// Dasher, gold = the waiting requester (user-approved scheme)
		dasherIcon = ImageUtil.recolorImage(pinIcon, ColorScheme.PROGRESS_COMPLETE_COLOR);
		requesterIcon = ImageUtil.recolorImage(pinIcon, new java.awt.Color(255, 184, 63)); // RS gold

		navButton = NavigationButton.builder()
			.tooltip("GieliDash")
			.icon(pinIcon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(deliveryOverlay);
		eventBus.register(tradeObserver);

		prevMine = null;
		prevRequestIds = null;

		// Covers install / plugin re-enable while already logged in
		if (config.enableSync())
		{
			sessionService.register();
		}

		log.debug("GieliDash started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(deliveryOverlay);
		eventBus.unregister(tradeObserver);
		clearGuidance();
		activeOrder = null;
		activeOrders = List.of();
		activeSignature = "";
		navButton = null;
		panel = null;
		log.debug("GieliDash stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		loggedIn = gameStateChanged.getGameState() == GameState.LOGGED_IN;
		if (loggedIn && config.enableSync())
		{
			sessionService.register();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!GieliDashConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("enableSync".equals(event.getKey()) && config.enableSync())
		{
			sessionService.register();
		}
		if ("showLeaderboard".equals(event.getKey()))
		{
			boolean show = config.showLeaderboard();
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setLeaderboardVisible(show);
				}
			});
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getLocalPlayer() != null)
		{
			lastLocation = client.getLocalPlayer().getWorldLocation();
			lastWorld = client.getWorld();
			myTotalLevel = client.getTotalLevel();
		}
	}

	/**
	 * Item names come from the LOCAL game cache, never the server - no
	 * player-authored text can reach the UI. Client thread only.
	 */
	private void resolveItemName(OrderItem item)
	{
		try
		{
			item.setName(itemManager.getItemComposition(item.getId()).getName());
		}
		catch (RuntimeException e)
		{
			item.setName("Item " + item.getId());
		}
	}

	/** Total level needed to enter a world (0 = open to everyone). Any thread. */
	private int worldTotalRequirement(int worldId)
	{
		net.runelite.http.api.worlds.WorldResult worlds = worldService.getWorlds();
		if (worlds == null)
		{
			return 0;
		}
		net.runelite.http.api.worlds.World world = worlds.findWorld(worldId);
		if (world == null || !world.getTypes().contains(net.runelite.http.api.worlds.WorldType.SKILL_TOTAL))
		{
			return 0;
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(world.getActivity());
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	/** Poll everything (one /sync round trip) while sync is on. */
	@Schedule(period = 8, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void pollOrders()
	{
		if (!config.enableSync() || panel == null)
		{
			return;
		}
		if (!api.hasToken())
		{
			// Self-heal: registration can miss the login event (timing, or sync
			// enabled mid-session) - keep retrying until the token exists
			sessionService.register();
			SwingUtilities.invokeLater(() -> panel.setSyncStatus("registering..."));
			return;
		}
		try
		{
			ApiClient.SyncResponse s = api.sync();
			List<Order> mine = s.mine;
			List<Order> requests = s.requests;
			List<com.gielidash.api.DasherPost> posts = s.posts;
			com.gielidash.api.Metrics metrics = s.metrics;

			// Skill-total worlds: flag (requests) or hide (board) what I can't enter,
			// plus the configured risk filters (front cost / verified / min ratings)
			int total = myTotalLevel;
			int hidden = 0;
			List<Order> open = new java.util.ArrayList<>();
			for (Order order : s.orders)
			{
				int required = worldTotalRequirement(order.getWorld());
				if (total > 0 && required > total)
				{
					if (config.hideLockedWorlds())
					{
						hidden++;
						continue;
					}
					order.setLockedRequirement(required);
				}
				if (config.verifiedRequestersOnly()
					&& (order.getRequesterVerified() == null || order.getRequesterVerified() != 1))
				{
					hidden++;
					continue;
				}
				if (config.minRequesterRatings() > 0
					&& (order.getRequesterRatingCount() == null
						|| order.getRequesterRatingCount() < config.minRequesterRatings()))
				{
					hidden++;
					continue;
				}
				open.add(order);
			}
			for (Order request : requests)
			{
				int required = worldTotalRequirement(request.getWorld());
				if (total > 0 && required > total)
				{
					request.setLockedRequirement(required);
				}
			}
			notifyChanges(mine, requests);
			final int hiddenCount = hidden;

			// Server sorts newest-first; primary = OLDEST active so queueing more
			// work never yanks the route away from the delivery in progress
			List<Order> actives = new java.util.ArrayList<>();
			for (Order order : mine)
			{
				if (order.isActive())
				{
					actives.add(order);
				}
			}
			java.util.Collections.reverse(actives);
			Order next = actives.isEmpty() ? null : actives.get(0);
			updateActiveOrders(actives, next);
			updateCounterpartPin(next);

			// Price lookups assert the client thread - compute front costs there,
			// THEN hand everything to Swing
			clientThread.invokeLater(() ->
			{
				WorldPoint me = lastLocation;
				int myWorld = lastWorld;
				for (List<Order> list : List.of(open, requests, mine))
				{
					for (Order order : list)
					{
						long cost = 0;
						for (OrderItem item : order.getItems())
						{
							resolveItemName(item);
							cost += (long) itemManager.getItemPrice(item.getId()) * item.getQty();
						}
						order.setFrontCostGp(cost);
						if (me != null && order.getWorld() == myWorld)
						{
							order.setDistanceTiles(me.distanceTo2D(
								new WorldPoint(order.getDestX(), order.getDestY(), order.getDestPlane())));
						}
					}
				}

				// Front-cost risk filter needs the GE costs computed just above
				int frontHidden = 0;
				if (config.maxFrontCost() > 0)
				{
					java.util.Iterator<Order> it = open.iterator();
					while (it.hasNext())
					{
						Order order = it.next();
						if (order.getFrontCostGp() != null && order.getFrontCostGp() > config.maxFrontCost())
						{
							it.remove();
							frontHidden++;
						}
					}
				}
				final int totalHidden = hiddenCount + frontHidden;
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.setMarket(s.market);
						panel.setOrders(open);
						panel.setMyOrders(mine);
						panel.setRequests(requests);
						panel.setPosts(posts);
						panel.setMetrics(metrics, config.businessStats());
						panel.setSyncStatus(requests.isEmpty()
							? open.size() + " open" + (totalHidden > 0 ? " · " + totalHidden + " hidden" : "")
							: requests.size() + (requests.size() == 1 ? " request!" : " requests!"));
					}
				});
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

	/** Heartbeat my position to the server for every active order. */
	@Schedule(period = 3, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void sendLocationHeartbeat()
	{
		List<Order> orders = activeOrders;
		WorldPoint location = lastLocation;
		if (orders.isEmpty() || location == null || !config.enableSync() || !api.hasToken())
		{
			return;
		}
		for (Order order : orders)
		{
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
	}

	/** Diff this poll against the last one and fire notifications. Any thread. */
	private void notifyChanges(List<Order> mine, List<Order> requests)
	{
		Map<Integer, Order> mineNow = new HashMap<>();
		for (Order order : mine)
		{
			mineNow.put(order.getId(), order);
		}
		java.util.Set<Integer> requestIdsNow = new java.util.HashSet<>();
		for (Order request : requests)
		{
			requestIdsNow.add(request.getId());
		}

		boolean ready = prevMine != null && prevRequestIds != null;
		if (ready && config.notifyEvents())
		{
			for (Order request : requests)
			{
				if (!prevRequestIds.contains(request.getId()))
				{
					notifier.notify("GieliDash: new request from " + request.getRequesterName()
						+ " (" + com.gielidash.ui.Gp.format(request.getFeeGp()) + " gp)");
				}
			}
			for (Order now : mine)
			{
				Order before = prevMine.get(now.getId());
				if (before == null || !"requester".equals(now.getRole()))
				{
					continue;
				}
				if ("open".equals(before.getStatus()) && "claimed".equals(now.getStatus()))
				{
					notifier.notify("GieliDash: " + now.getDasherName() + " accepted your order #" + now.getId());
				}
				else if (!"arrived".equals(before.getStatus()) && "arrived".equals(now.getStatus()))
				{
					notifier.notify("GieliDash: your dasher has arrived (order #" + now.getId() + ")");
				}
				else if (!"delivered".equals(before.getStatus()) && "delivered".equals(now.getStatus()))
				{
					notifier.notify("GieliDash: order #" + now.getId() + " delivered");
				}
				else if ("open".equals(now.getStatus())
					&& before.getDirectedTo() != null && now.getDirectedTo() == null)
				{
					notifier.notify("GieliDash: " + before.getDirectedTo()
						+ " declined - order #" + now.getId() + " is now public");
				}
				else if (before.isActive() && "open".equals(now.getStatus()))
				{
					notifier.notify("GieliDash: your dasher bailed - order #"
						+ now.getId() + " was re-posted");
				}
			}
		}
		prevMine = mineNow;
		prevRequestIds = requestIdsNow;
	}

	/** Fetch a player's profile off-thread, hand it to the EDT. Called from cards. */
	public void fetchProfile(String displayName, Consumer<com.gielidash.api.Profile> callback)
	{
		executor.execute(() ->
		{
			try
			{
				com.gielidash.api.Profile profile = api.getProfile(displayName);
				SwingUtilities.invokeLater(() -> callback.accept(profile));
			}
			catch (ApiException e)
			{
				log.debug("Profile fetch failed for {}: {}", displayName, e.getMessage());
			}
		});
	}

	/** Panel-side world access for the "My world" filter (EDT-safe). */
	public int getCurrentWorld()
	{
		return lastWorld;
	}

	// ---- Presets ("CoX kit") + favorite dashers, persisted in plugin config ----

	public List<com.gielidash.api.Preset> getPresets()
	{
		String json = configManager.getConfiguration(GieliDashConfig.GROUP, "presets");
		if (json == null || json.isEmpty())
		{
			return new java.util.ArrayList<>();
		}
		try
		{
			com.gielidash.api.Preset[] presets = gson.fromJson(json, com.gielidash.api.Preset[].class);
			return new java.util.ArrayList<>(java.util.Arrays.asList(presets));
		}
		catch (com.google.gson.JsonSyntaxException e)
		{
			return new java.util.ArrayList<>();
		}
	}

	/** Newest first, capped at 8 (oldest evicted). */
	public void savePreset(com.gielidash.api.Preset preset)
	{
		List<com.gielidash.api.Preset> presets = getPresets();
		presets.removeIf(p -> p.getName().equals(preset.getName()));
		presets.add(0, preset);
		while (presets.size() > 8)
		{
			presets.remove(presets.size() - 1);
		}
		configManager.setConfiguration(GieliDashConfig.GROUP, "presets", gson.toJson(presets));
	}

	/** Loads a preset into the Create basket with fresh GE prices (client thread). */
	public void loadPreset(com.gielidash.api.Preset preset)
	{
		clientThread.invokeLater(() ->
		{
			Map<Integer, Long> prices = new HashMap<>();
			for (OrderItem item : preset.getItems())
			{
				resolveItemName(item);
				prices.put(item.getId(), (long) itemManager.getItemPrice(item.getId()));
			}
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.loadPreset(preset, prices);
				}
			});
		});
	}

	public java.util.Set<String> getFavorites()
	{
		String csv = configManager.getConfiguration(GieliDashConfig.GROUP, "favorites");
		java.util.Set<String> favorites = new java.util.LinkedHashSet<>();
		if (csv != null && !csv.isEmpty())
		{
			for (String name : csv.split("\n"))
			{
				if (!name.isEmpty())
				{
					favorites.add(name);
				}
			}
		}
		return favorites;
	}

	public boolean isFavorite(String name)
	{
		return getFavorites().contains(name);
	}

	public void toggleFavorite(String name)
	{
		java.util.Set<String> favorites = getFavorites();
		if (!favorites.remove(name))
		{
			favorites.add(name);
		}
		configManager.setConfiguration(GieliDashConfig.GROUP, "favorites",
			String.join("\n", favorites));
	}

	/**
	 * Called from the panel (EDT). Computes the ETA commitment from my position
	 * (straight-line x1.5 + 60s, floor 2 min; 8 min default cross-world), then
	 * claims off-thread.
	 */
	public void acceptOrder(Order order)
	{
		// Friendly pre-check; the server enforces the same cap atomically
		long delivering = 0;
		for (Order active : activeOrders)
		{
			if ("dasher".equals(active.getRole()))
			{
				delivering++;
			}
		}
		if (delivering >= 3)
		{
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setSyncStatus("max 3 active deliveries");
				}
			});
			return;
		}
		clientThread.invokeLater(() ->
		{
			int eta = 480;
			WorldPoint me = lastLocation;
			if (me != null && order.getWorld() == lastWorld)
			{
				int tiles = me.distanceTo2D(new WorldPoint(
					order.getDestX(), order.getDestY(), order.getDestPlane()));
				eta = Math.max(120, (int) (tiles * 0.3 * 1.5) + 60);
			}
			final int commitment = eta;
			// Jump the panel to Mine on success - that's where Start delivery
			// lives, and the order just vanished from the board (user feedback)
			runApi("accept", () -> api.acceptOrder(order.getId(), commitment), () ->
			{
				if (panel != null)
				{
					panel.selectMineTab();
				}
			});
		});
	}

	/** Called from the Mine tab's fee nudge (EDT). Bumps the fee by 25% (min +1K). */
	public void raiseFee(Order order)
	{
		long newFee = Math.max(order.getFeeGp() + 1000, (long) (order.getFeeGp() * 1.25));
		runApi("raise fee", () -> api.raiseFee(order.getId(), newFee));
	}

	/** Called from the Requests tab (EDT). Sends the order to the public board. */
	public void declineOrder(Order order)
	{
		runApi("decline", () -> api.declineOrder(order.getId()));
	}

	/** Called from the Mine tab (EDT). */
	public void updateOrderStatus(Order order, String newStatus)
	{
		runApi("status " + newStatus, () -> api.updateStatus(order.getId(), newStatus));
	}

	/**
	 * Advance an order to any later phase - the server only accepts sequential
	 * transitions, so this chains through the intermediate steps. Lets the
	 * overlay offer "Mark delivered" straight from claimed (user feedback).
	 */
	public void advanceOrderTo(Order order, String target)
	{
		runApi("status " + target, () ->
		{
			String status = order.getStatus();
			while (!status.equals(target))
			{
				String next = nextStatus(status);
				if (next == null)
				{
					break;
				}
				api.updateStatus(order.getId(), next);
				status = next;
			}
		});
	}

	@Nullable
	private static String nextStatus(String status)
	{
		switch (status)
		{
			case "claimed":
				return "in_transit";
			case "in_transit":
				return "arrived";
			case "arrived":
				return "delivered";
			default:
				return null;
		}
	}

	/** Called from the Mine tab (EDT). */
	public void cancelOrder(Order order)
	{
		runApi("cancel", () -> api.cancelOrder(order.getId(), "cancelled_from_panel"));
	}

	/** Called from the Mine tab rating row (EDT). */
	public void rateOrder(Order order, int stars)
	{
		runApi("rate", () -> api.submitRating(order.getId(), stars, null));
	}

	/** Called from a finished order's Reorder button (EDT). Prices need the client thread. */
	public void reorder(Order order)
	{
		final List<OrderItem> items = order.getItems();
		clientThread.invokeLater(() ->
		{
			Map<Integer, Long> prices = new HashMap<>();
			for (OrderItem item : items)
			{
				resolveItemName(item);
				prices.put(item.getId(), (long) itemManager.getItemPrice(item.getId()));
			}
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.reorderInto(items, prices);
				}
			});
		});
	}

	/** Called from the Posts tab (EDT). Structured values only - no free text. */
	public void createDasherPost(String service, String region, long baseFeeGp)
	{
		// Stamp where the ad was posted from (readers see "posted from X · N tiles")
		WorldPoint at = lastLocation;
		int world = lastWorld;
		runApi("post ad", () -> api.createPost(service, region, baseFeeGp, at, world));
	}

	/** For panel construction and toggles (EDT reads). */
	public boolean leaderboardEnabled()
	{
		return config.showLeaderboard();
	}

	/** Called when the Board tab is selected (EDT). Throttled to one fetch per 30s. */
	public void fetchLeaderboard()
	{
		long now = System.currentTimeMillis();
		if (now - lastLeaderboardFetch < 30_000 || !api.hasToken())
		{
			return;
		}
		lastLeaderboardFetch = now;
		executor.execute(() ->
		{
			try
			{
				com.gielidash.api.ApiClient.Leaderboard board = api.leaderboard();
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.setLeaderboard(board);
					}
				});
			}
			catch (ApiException e)
			{
				log.debug("Leaderboard fetch failed: {}", e.getMessage());
			}
		});
	}

	/** Called from the Posts tab (EDT). */
	public void deactivateDasherPost()
	{
		runApi("take down ad", () -> api.deactivatePost());
	}

	private void runApi(String what, Runnable call)
	{
		runApi(what, call, null);
	}

	/** onSuccess runs on the EDT after the call succeeds, before the re-poll. */
	private void runApi(String what, Runnable call, @Nullable Runnable onSuccess)
	{
		executor.execute(() ->
		{
			try
			{
				call.run();
				if (onSuccess != null)
				{
					SwingUtilities.invokeLater(onSuccess);
				}
			}
			catch (ApiException e)
			{
				log.warn("GieliDash {} failed: {}", what, e.getMessage());
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						// Next successful poll overwrites this with the open count
						panel.setSyncStatus(what + " failed: " + e.getMessage());
					}
				});
			}
			pollOrders();
		});
	}

	/**
	 * Called from the Create tab (EDT). Reads the player's location on the client
	 * thread, then posts the order off it. Callback receives "#<id>" on success
	 * or a user-facing error message.
	 */
	public void createOrderAtMyLocation(List<OrderItem> items, long feeGp,
		@Nullable String directedTo, Consumer<String> callback)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
			{
				callback.accept("Log in first");
				return;
			}
			WorldPoint location = client.getLocalPlayer().getWorldLocation();
			int world = client.getWorld();

			executor.execute(() ->
			{
				try
				{
					int id = api.createOrder(items, location.getX(), location.getY(),
						location.getPlane(), world, feeGp, null, directedTo);
					int required = worldTotalRequirement(world);
					callback.accept("#" + id + (required > 0
						? " (world needs " + required + "+ total)" : ""));
					pollOrders();
				}
				catch (ApiException e)
				{
					callback.accept("Posting failed: " + e.getMessage());
				}
			});
		});
	}

	/** Reconcile overlay/map-pin/route state with the current active orders. Any thread. */
	private void updateActiveOrders(List<Order> actives, @Nullable Order primary)
	{
		activeOrder = primary;
		activeOrders = List.copyOf(actives);

		// Rebuild guidance only when the set, order, or a status changed
		StringBuilder sig = new StringBuilder();
		for (Order order : actives)
		{
			sig.append(order.getId()).append(':').append(order.getStatus()).append(';');
		}
		String signature = sig.toString();
		if (signature.equals(activeSignature))
		{
			return;
		}
		activeSignature = signature;

		clientThread.invokeLater(() ->
		{
			clearGuidance();
			if (primary == null)
			{
				return;
			}

			// A destination pin for EVERY active order; the queue is visible on
			// the map without touching the single-order experience
			for (Order order : actives)
			{
				WorldMapPoint pin = WorldMapPoint.builder()
					.worldPoint(new WorldPoint(order.getDestX(), order.getDestY(), order.getDestPlane()))
					.image(pinIcon)
					.tooltip("GieliDash delivery #" + order.getId()
						+ (order.getId() == primary.getId() || actives.size() == 1 ? "" : " (queued)"))
					.jumpOnClick(true)
					.snapToEdge(true)
					.name("GieliDash")
					.build();
				destPoints.add(pin);
				worldMapPointManager.add(pin);
			}

			// Only route the Dasher - the requester is being delivered to -
			// and only to the primary destination (Shortest Path draws one path)
			if (config.useShortestPath() && "dasher".equals(primary.getRole()))
			{
				Map<String, Object> data = new HashMap<>();
				data.put("target", new WorldPoint(primary.getDestX(), primary.getDestY(), primary.getDestPlane()));
				eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, "path", data));
				routeShown = true;
			}
		});
	}

	/** Moves the counterpart's live map icon to their last heartbeat. Any thread. */
	private void updateCounterpartPin(@Nullable Order order)
	{
		clientThread.invokeLater(() ->
		{
			if (counterpartPoint != null)
			{
				worldMapPointManager.remove(counterpartPoint);
				counterpartPoint = null;
			}
			if (order == null || order.getCpX() == null || order.getCpY() == null)
			{
				return;
			}
			boolean isDasher = "dasher".equals(order.getRole());
			String who = isDasher ? order.getRequesterName() : order.getDasherName();
			counterpartPoint = WorldMapPoint.builder()
				.worldPoint(new WorldPoint(order.getCpX(), order.getCpY(),
					order.getCpPlane() != null ? order.getCpPlane() : 0))
				.image(isDasher ? requesterIcon : dasherIcon)
				.tooltip((isDasher ? "Requester: " : "Dasher: ") + who)
				.snapToEdge(true)
				.name("GieliDash " + (isDasher ? "requester" : "dasher"))
				.build();
			worldMapPointManager.add(counterpartPoint);
		});
	}

	/** Client thread only. */
	private void clearGuidance()
	{
		for (WorldMapPoint pin : destPoints)
		{
			worldMapPointManager.remove(pin);
		}
		destPoints.clear();
		if (counterpartPoint != null)
		{
			worldMapPointManager.remove(counterpartPoint);
			counterpartPoint = null;
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
