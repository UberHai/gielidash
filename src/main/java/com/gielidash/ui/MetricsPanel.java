package com.gielidash.ui;

import com.gielidash.api.Metrics;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

/**
 * "Stats" tab: lifetime numbers from the server.
 */
class MetricsPanel extends JPanel
{
	private final JPanel rows = new JPanel(new DynamicGridLayout(0, 1, 0, 4));

	MetricsPanel()
	{
		setLayout(new DynamicGridLayout(0, 1, 0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		rows.setOpaque(false);
		add(rows);
		showPlaceholder();
	}

	private void showPlaceholder()
	{
		rows.removeAll();
		JLabel waiting = new JLabel("Stats load once you're registered.");
		waiting.setFont(FontManager.getRunescapeSmallFont());
		waiting.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		rows.add(waiting);
	}

	/** Swing EDT only. */
	void setMetrics(Metrics m, boolean businessStats)
	{
		rows.removeAll();
		rows.add(statRow("Rating", m.getStars() != null
			? "★" + m.getStars() + " (" + m.getRatingCount() + ")" : "New", ColorScheme.BRAND_ORANGE));
		rows.add(statRow("Deliveries done", String.valueOf(m.getDeliveriesDone()), null));
		rows.add(statRow("gp earned", Gp.format(m.getGpEarned()) + " gp",
			ColorScheme.GRAND_EXCHANGE_PRICE));
		rows.add(statRow("Avg delivery", formatSeconds(m.getAvgDeliverySeconds()), null));
		if (businessStats && m.getActiveSeconds() > 0)
		{
			// gp per hour ACTUALLY delivering (accept -> delivered), idle never counts
			rows.add(statRow("Active time",
				formatSeconds((int) Math.min(m.getActiveSeconds(), Integer.MAX_VALUE)), null));
			long gpPerHour = Math.round(m.getGpEarned() * 3600.0 / m.getActiveSeconds());
			rows.add(statRow("gp / active hour", Gp.format(gpPerHour) + " gp",
				ColorScheme.GRAND_EXCHANGE_PRICE));
		}
		rows.add(statRow("Orders posted", String.valueOf(m.getOrdersPosted()), null));
		rows.add(statRow("Orders received", String.valueOf(m.getOrdersReceived()), null));
		rows.add(statRow("gp spent on fees", Gp.format(m.getGpSpent()) + " gp",
			ColorScheme.GRAND_EXCHANGE_ALCH));
		rows.revalidate();
		rows.repaint();
	}

	private static String formatSeconds(Integer seconds)
	{
		if (seconds == null)
		{
			return "-";
		}
		return seconds >= 3600
			? (seconds / 3600) + "h " + (seconds % 3600) / 60 + "m"
			: (seconds / 60) + "m " + (seconds % 60) + "s";
	}

	private JPanel statRow(String label, String value, java.awt.Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));

		JLabel key = new JLabel(label);
		key.setFont(FontManager.getRunescapeSmallFont());
		key.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(key, BorderLayout.WEST);

		JLabel val = new JLabel(value);
		val.setFont(FontManager.getRunescapeSmallFont());
		val.setForeground(valueColor != null ? valueColor : ColorScheme.TEXT_COLOR);
		row.add(val, BorderLayout.EAST);
		return row;
	}
}
