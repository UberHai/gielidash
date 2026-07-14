package com.gielidash.ui;

import com.gielidash.GieliDashPlugin;
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
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

public class GieliDashPanel extends PluginPanel
{
	private final JPanel ordersList = new JPanel();
	private final PluginErrorPanel emptyOrders = new PluginErrorPanel();
	private final JLabel syncStatus = new JLabel(" ");
	private final GieliDashPlugin plugin;
	private final ItemManager itemManager;

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

		// Tabs
		JPanel display = new JPanel();
		display.setOpaque(false);
		MaterialTabGroup tabGroup = new MaterialTabGroup(display);

		MaterialTab ordersTab = new MaterialTab("Orders", tabGroup, buildOrdersTab());
		MaterialTab createTab = new MaterialTab("Create", tabGroup, new CreateOrderPanel(plugin, itemManager));
		tabGroup.addTab(ordersTab);
		tabGroup.addTab(createTab);
		tabGroup.select(ordersTab);

		add(tabGroup);
		add(Box.createVerticalStrut(6));
		add(display);
	}

	private JPanel buildOrdersTab()
	{
		JPanel tab = new JPanel(new BorderLayout());
		tab.setOpaque(false);

		ordersList.setLayout(new BoxLayout(ordersList, BoxLayout.Y_AXIS));
		ordersList.setOpaque(false);

		emptyOrders.setContent("No open orders",
			"Orders posted by requesters will show up here. Pull to refresh happens automatically.");
		ordersList.add(emptyOrders);

		tab.add(ordersList, BorderLayout.NORTH);
		return tab;
	}

	/** Swing EDT only. */
	public void setOrders(List<Order> orders)
	{
		ordersList.removeAll();
		if (orders.isEmpty())
		{
			ordersList.add(emptyOrders);
		}
		else
		{
			for (Order order : orders)
			{
				OrderBox box = new OrderBox(order, itemManager, plugin::acceptOrder);
				box.setAlignmentX(LEFT_ALIGNMENT);
				ordersList.add(box);
				ordersList.add(Box.createVerticalStrut(6));
			}
		}
		ordersList.revalidate();
		ordersList.repaint();
	}

	/** Swing EDT only. */
	public void setSyncStatus(String text)
	{
		syncStatus.setText(text);
	}
}
