package com.gielidash.ui;

import com.gielidash.GieliDashPlugin;
import com.gielidash.api.ApiClient;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * "Board" tab (config-gated): weekly courier rankings. Sub-tabs: most
 * deliveries, most gp earned (both rolling 7 days), best lifetime rating.
 */
class LeaderboardPanel extends JPanel
{
	private final GieliDashPlugin plugin;
	private final JPanel runsList = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
	private final JPanel earnedList = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
	private final JPanel ratedList = new JPanel(new DynamicGridLayout(0, 1, 0, 4));

	LeaderboardPanel(GieliDashPlugin plugin)
	{
		this.plugin = plugin;

		setLayout(new DynamicGridLayout(0, 1, 0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

		JPanel display = new JPanel(new BorderLayout());
		display.setOpaque(false);
		MaterialTabGroup subTabs = new MaterialTabGroup(display);
		MaterialTab runsTab = new MaterialTab("Runs", subTabs, wrap(runsList, "This week"));
		MaterialTab earnedTab = new MaterialTab("Earned", subTabs, wrap(earnedList, "This week"));
		MaterialTab ratedTab = new MaterialTab("Rating", subTabs, wrap(ratedList, "All time"));
		subTabs.addTab(runsTab);
		subTabs.addTab(earnedTab);
		subTabs.addTab(ratedTab);
		subTabs.setLayout(new DynamicGridLayout(1, 3, 2, 2));
		subTabs.select(runsTab);

		add(subTabs);
		add(display);
		showPlaceholder(runsList);
		showPlaceholder(earnedList);
		showPlaceholder(ratedList);
	}

	private JPanel wrap(JPanel list, String window)
	{
		list.setOpaque(false);
		JPanel tab = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		tab.setOpaque(false);
		JLabel period = new JLabel(window);
		period.setFont(FontManager.getRunescapeSmallFont());
		period.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		tab.add(period);
		tab.add(list);
		return tab;
	}

	private static void showPlaceholder(JPanel list)
	{
		list.removeAll();
		JLabel waiting = new JLabel("Loading...");
		waiting.setFont(FontManager.getRunescapeSmallFont());
		waiting.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		list.add(waiting);
	}

	/** Swing EDT only. */
	void setLeaderboard(ApiClient.Leaderboard board)
	{
		fill(runsList, board.runs, row ->
			row.value + (row.value != null && row.value == 1 ? " run" : " runs"));
		fill(earnedList, board.earned, row -> Gp.format(row.value == null ? 0 : row.value) + " gp");
		fill(ratedList, board.rated, row ->
			"★" + row.stars + " (" + (row.count == null ? 0 : row.count) + ")");
	}

	private void fill(JPanel list, List<ApiClient.LeaderboardRow> rows,
		java.util.function.Function<ApiClient.LeaderboardRow, String> valueText)
	{
		list.removeAll();
		if (rows == null || rows.isEmpty())
		{
			JLabel none = new JLabel("No couriers ranked yet.");
			none.setFont(FontManager.getRunescapeSmallFont());
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			list.add(none);
		}
		else
		{
			int rank = 1;
			for (ApiClient.LeaderboardRow row : rows)
			{
				list.add(rankRow(rank++, row, valueText.apply(row)));
			}
		}
		list.revalidate();
		list.repaint();
	}

	private JPanel rankRow(int rank, ApiClient.LeaderboardRow row, String value)
	{
		JPanel line = new JPanel(new BorderLayout());
		line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		line.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		String check = row.verified != null && row.verified == 1 ? " ✓" : "";
		JLabel who = new JLabel(rank + ". " + row.name + check);
		who.setFont(FontManager.getRunescapeSmallFont());
		who.setForeground(rank == 1 ? ColorScheme.BRAND_ORANGE : ColorScheme.TEXT_COLOR);
		who.setToolTipText("Click for " + row.name + "'s profile");
		who.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		final String name = row.name;
		who.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				ProfilePopup.fetchAndShow(plugin, who, name);
			}
		});
		line.add(who, BorderLayout.WEST);

		JLabel val = new JLabel(value);
		val.setFont(FontManager.getRunescapeSmallFont());
		val.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
		line.add(val, BorderLayout.EAST);
		return line;
	}
}
