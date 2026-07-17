package com.gielidash.ui;

import com.gielidash.GieliDashPlugin;
import com.gielidash.api.DasherPost;
import com.gielidash.api.Order;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

public class GieliDashPanel extends PluginPanel
{
	private final JPanel ordersList = new JPanel();
	private final JPanel mineList = new JPanel();
	private final JPanel requestsList = new JPanel();
	private final PluginErrorPanel emptyOrders = new PluginErrorPanel();
	private final PluginErrorPanel emptyMine = new PluginErrorPanel();
	private final PluginErrorPanel emptyRequests = new PluginErrorPanel();
	private final JLabel syncStatus = new JLabel(" ");
	private final GieliDashPlugin plugin;
	private final ItemManager itemManager;
	private PostsPanel postsPanel;
	private CreateOrderPanel createPanel;
	private MetricsPanel metricsPanel;
	private LeaderboardPanel leaderboardPanel;
	private MaterialTabGroup tabGroup;
	private MaterialTab createTab;
	private MaterialTab ordersTab;
	private MaterialTab boardTab;
	private boolean boardVisible;
	private javax.swing.JComboBox<String> sortCombo;
	private javax.swing.JCheckBox myWorldBox;
	private List<Order> lastOpen = java.util.List.of();
	private com.gielidash.api.ApiClient.Market market;

	public GieliDashPanel(GieliDashPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;

		// Title
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		JLabel title = new JLabel("GieliDash");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		titleRow.add(title, BorderLayout.WEST);
		syncStatus.setFont(FontManager.getRunescapeSmallFont());
		syncStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		titleRow.add(syncStatus, BorderLayout.EAST);
		add(titleRow);
		add(Box.createVerticalStrut(8));

		// Tabs. The display panel MUST stretch content to the panel width -
		// FlowLayout (Swing default) lets children overflow and clip
		JPanel display = new JPanel(new BorderLayout());
		display.setOpaque(false);
		tabGroup = new MaterialTabGroup(display);

		createPanel = new CreateOrderPanel(plugin, itemManager);
		postsPanel = new PostsPanel(plugin, dasherName ->
		{
			createPanel.setDirectedTo(dasherName);
			tabGroup.select(createTab);
		});
		metricsPanel = new MetricsPanel();
		leaderboardPanel = new LeaderboardPanel(plugin);
		ordersTab = new MaterialTab("Orders", tabGroup, buildOrdersTab());
		MaterialTab mineTab = new MaterialTab("Mine", tabGroup, buildMineTab());
		createTab = new MaterialTab("Create", tabGroup, createPanel);
		MaterialTab postsTab = new MaterialTab("Posts", tabGroup, postsPanel);
		MaterialTab requestsTab = new MaterialTab("Reqs", tabGroup, buildRequestsTab());
		MaterialTab statsTab = new MaterialTab("Stats", tabGroup, metricsPanel);
		boardTab = new MaterialTab("Board", tabGroup, leaderboardPanel);
		boardTab.setOnSelectEvent(() ->
		{
			plugin.fetchLeaderboard();
			return true;
		});
		tabGroup.addTab(ordersTab);
		tabGroup.addTab(mineTab);
		tabGroup.addTab(createTab);
		tabGroup.addTab(postsTab);
		tabGroup.addTab(requestsTab);
		tabGroup.addTab(statsTab);
		// Board is registered so select() knows it, but only rendered when the
		// config toggle is on (grid goes 3+3 -> 3+3+1)
		tabGroup.addTab(boardTab);
		boardVisible = true;
		setLeaderboardVisible(plugin.leaderboardEnabled());
		// MaterialTabGroup defaults to FlowLayout, which wraps overflowing tabs
		// onto an invisible second row at 225px. One row of 5 makes the labels
		// unreadable - two rows of three keeps them full-width and legible
		tabGroup.setLayout(new DynamicGridLayout(0, 3, 2, 2));
		tabGroup.select(ordersTab);

		add(tabGroup);
		add(Box.createVerticalStrut(6));
		add(display);
	}

	private JPanel buildOrdersTab()
	{
		JPanel tab = new JPanel(new BorderLayout());
		tab.setOpaque(false);

		// Sort + filter controls
		sortCombo = new javax.swing.JComboBox<>(new String[]{"Newest", "Highest fee", "Closest"});
		sortCombo.setFont(FontManager.getRunescapeSmallFont());
		sortCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sortCombo.setForeground(ColorScheme.TEXT_COLOR);
		sortCombo.setFocusable(false);
		sortCombo.addActionListener(e -> renderOrders());

		myWorldBox = new javax.swing.JCheckBox("My world");
		myWorldBox.setFont(FontManager.getRunescapeSmallFont());
		myWorldBox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		myWorldBox.setOpaque(false);
		myWorldBox.setFocusPainted(false);
		myWorldBox.addActionListener(e -> renderOrders());

		JPanel controls = new JPanel(new BorderLayout(6, 0));
		controls.setOpaque(false);
		controls.add(sortCombo, BorderLayout.CENTER);
		controls.add(myWorldBox, BorderLayout.EAST);

		ordersList.setLayout(new DynamicGridLayout(0, 1, 0, 6));
		ordersList.setOpaque(false);

		emptyOrders.setContent("No open orders",
			"Orders posted by requesters will show up here. Pull to refresh happens automatically.");
		ordersList.add(emptyOrders);

		JPanel column = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		column.setOpaque(false);
		column.add(controls);
		column.add(ordersList);
		tab.add(column, BorderLayout.NORTH);
		return tab;
	}

	/** Re-sort and re-render the cached board per the current controls. Swing EDT only. */
	private void renderOrders()
	{
		List<Order> view = new java.util.ArrayList<>(lastOpen);
		if (myWorldBox.isSelected())
		{
			view.removeIf(o -> o.getWorld() != plugin.getCurrentWorld());
		}
		switch ((String) sortCombo.getSelectedItem())
		{
			case "Highest fee":
				view.sort(java.util.Comparator.comparingLong(Order::getFeeGp).reversed());
				break;
			case "Closest":
				view.sort(java.util.Comparator.comparingInt(
					o -> o.getDistanceTiles() == null ? Integer.MAX_VALUE : o.getDistanceTiles()));
				break;
			default: // Newest - server order
				break;
		}

		ordersList.removeAll();
		if (view.isEmpty())
		{
			ordersList.add(emptyOrders);
		}
		else
		{
			for (Order order : view)
			{
				ordersList.add(new OrderBox(order, itemManager, plugin::acceptOrder, null, plugin));
			}
		}
		ordersList.revalidate();
		ordersList.repaint();
	}

	private JPanel buildMineTab()
	{
		JPanel tab = new JPanel(new BorderLayout());
		tab.setOpaque(false);

		mineList.setLayout(new DynamicGridLayout(0, 1, 0, 6));
		mineList.setOpaque(false);

		emptyMine.setContent("Nothing yet",
			"Orders you post and deliveries you accept will show up here.");
		mineList.add(emptyMine);

		tab.add(mineList, BorderLayout.NORTH);
		return tab;
	}

	private JPanel buildRequestsTab()
	{
		JPanel tab = new JPanel(new BorderLayout());
		tab.setOpaque(false);

		requestsList.setLayout(new DynamicGridLayout(0, 1, 0, 6));
		requestsList.setOpaque(false);

		emptyRequests.setContent("No incoming requests",
			"When a requester picks you from the Posts tab, their order lands here first.");
		requestsList.add(emptyRequests);

		tab.add(requestsList, BorderLayout.NORTH);
		return tab;
	}

	/** Swing EDT only. */
	public void setRequests(List<Order> orders)
	{
		requestsList.removeAll();
		if (orders.isEmpty())
		{
			requestsList.add(emptyRequests);
		}
		else
		{
			for (Order order : orders)
			{
				requestsList.add(new OrderBox(order, itemManager, plugin::acceptOrder, plugin::declineOrder, plugin));
			}
		}
		requestsList.revalidate();
		requestsList.repaint();
	}

	/** Swing EDT only. Market pulse feeds the Create tab line + the fee nudge. */
	public void setMarket(com.gielidash.api.ApiClient.Market market)
	{
		this.market = market;
		createPanel.setMarket(market);
	}

	/** Swing EDT only. */
	public void setMyOrders(List<Order> orders)
	{
		mineList.removeAll();
		if (orders.isEmpty())
		{
			mineList.add(emptyMine);
		}
		else
		{
			boolean marketBusy = market != null && market.onlineDashers >= 3;
			for (Order order : orders)
			{
				// Fee nudge: my public order, unclaimed for 10+ min, with dashers around
				boolean nudge = marketBusy
					&& "requester".equals(order.getRole())
					&& "open".equals(order.getStatus())
					&& order.getDirectedTo() == null
					&& order.getAgeSeconds() != null && order.getAgeSeconds() >= 600;
				mineList.add(new MyOrderBox(order, plugin, nudge));
			}
		}
		mineList.revalidate();
		mineList.repaint();
	}

	/** Swing EDT only. */
	public void setOrders(List<Order> orders)
	{
		lastOpen = orders;
		renderOrders();
	}

	/** Swing EDT only. */
	public void setPosts(List<DasherPost> posts)
	{
		postsPanel.setPosts(posts);
	}

	/** Swing EDT only. */
	public void setSyncStatus(String text)
	{
		syncStatus.setText(text);
	}

	/** Swing EDT only. */
	public void setMetrics(com.gielidash.api.Metrics metrics, boolean businessStats)
	{
		metricsPanel.setMetrics(metrics, businessStats);
	}

	/** Swing EDT only. */
	public void setLeaderboard(com.gielidash.api.ApiClient.Leaderboard board)
	{
		leaderboardPanel.setLeaderboard(board);
	}

	/** Swing EDT only. Show/hide the config-gated Board tab. */
	public void setLeaderboardVisible(boolean show)
	{
		if (show == boardVisible)
		{
			return;
		}
		boardVisible = show;
		if (show)
		{
			tabGroup.add(boardTab);
		}
		else
		{
			if (boardTab.isSelected())
			{
				tabGroup.select(ordersTab);
			}
			tabGroup.remove(boardTab);
		}
		tabGroup.revalidate();
		tabGroup.repaint();
	}

	/** Swing EDT only. Refill the Create basket from a past order and jump to it. */
	public void reorderInto(List<com.gielidash.api.OrderItem> items, java.util.Map<Integer, Long> prices)
	{
		createPanel.loadBasket(items, prices);
		tabGroup.select(createTab);
	}

	/** Swing EDT only. Load a saved preset into the Create tab. */
	public void loadPreset(com.gielidash.api.Preset preset, java.util.Map<Integer, Long> prices)
	{
		createPanel.loadBasket(preset.getItems(), prices);
		createPanel.setFee(preset.getFeeGp());
		tabGroup.select(createTab);
	}
}
