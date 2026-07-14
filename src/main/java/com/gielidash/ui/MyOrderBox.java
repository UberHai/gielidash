package com.gielidash.ui;

import com.gielidash.GieliDashPlugin;
import com.gielidash.api.Order;
import com.gielidash.api.OrderItem;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * A card in the Mine tab: my order, its status, and the next action for my role.
 */
class MyOrderBox extends JPanel
{
	MyOrderBox(Order order, GieliDashPlugin plugin)
	{
		boolean isDasher = "dasher".equals(order.getRole());

		setLayout(new BorderLayout(0, 1));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

		// Header: role + order id left, status right
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		JLabel title = new JLabel((isDasher ? "Delivering" : "My order") + " #" + order.getId());
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		JLabel status = new JLabel(order.getStatus().replace('_', ' '));
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(statusColor(order.getStatus()));
		header.add(title, BorderLayout.WEST);
		header.add(status, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		// Body: item summary, counterpart, fee
		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

		StringBuilder summary = new StringBuilder();
		for (OrderItem item : order.getItems())
		{
			if (summary.length() > 0)
			{
				summary.append(", ");
			}
			summary.append(item.getName()).append(" ×").append(item.getQty());
		}
		JLabel items = new JLabel("<html>" + summary + "</html>");
		items.setFont(FontManager.getRunescapeSmallFont());
		items.setForeground(ColorScheme.TEXT_COLOR);
		body.add(items);

		// Two short lines instead of one long one - the panel is only 225px (live-test finding)
		String counterpartStars = isDasher
			? Stars.format(order.getRequesterStars(), order.getRequesterRatingCount())
			: Stars.format(order.getDasherStars(), order.getDasherRatingCount());
		Integer verifiedFlag = isDasher ? order.getRequesterVerified() : order.getDasherVerified();
		String check = verifiedFlag != null && verifiedFlag == 1 ? "✓ " : "";
		String counterpart = isDasher
			? "for " + check + order.getRequesterName() + " " + counterpartStars
			: (order.getDasherName() != null
				? "dasher: " + check + order.getDasherName() + " " + counterpartStars
				: "waiting for a dasher");
		JLabel who = new JLabel(counterpart);
		who.setFont(FontManager.getRunescapeSmallFont());
		who.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.add(who);

		JLabel meta = new JLabel("W" + order.getWorld()
			+ "  ·  " + QuantityFormatter.quantityToStackSize(order.getFeeGp()) + " gp fee");
		meta.setFont(FontManager.getRunescapeSmallFont());
		meta.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.add(meta);

		// Delivery duration (accept -> delivered), from server timestamps
		String duration = formatDuration(order.getClaimedAt(), order.getCompletedAt());
		if ("delivered".equals(order.getStatus()) && duration != null)
		{
			JLabel took = new JLabel("Delivered in " + duration);
			took.setFont(FontManager.getRunescapeSmallFont());
			took.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			body.add(took);
		}

		// Rate the counterpart once the order is finished (delivered/failed/cancelled with a dasher)
		if (isTerminal(order.getStatus()) && order.getDasherName() != null)
		{
			body.add(buildRatingRow(order, plugin));
		}
		add(body, BorderLayout.CENTER);

		// Action row for non-terminal orders
		JPanel actions = new JPanel();
		actions.setOpaque(false);
		actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));

		String next = nextAction(order.getStatus(), isDasher);
		if (next != null)
		{
			actions.add(actionButton(nextLabel(next), ColorScheme.BRAND_ORANGE,
				() -> plugin.updateOrderStatus(order, next)));
			actions.add(Box.createHorizontalStrut(6));
		}
		if (!isTerminal(order.getStatus()))
		{
			actions.add(actionButton("Cancel", ColorScheme.LIGHT_GRAY_COLOR,
				() -> plugin.cancelOrder(order)));
		}
		// One-click repost of a finished order I requested
		if (isTerminal(order.getStatus()) && !isDasher)
		{
			actions.add(actionButton("Reorder", ColorScheme.LIGHT_GRAY_COLOR,
				() -> plugin.reorder(order)));
		}
		if (actions.getComponentCount() > 0)
		{
			add(actions, BorderLayout.SOUTH);
		}
	}

	private static JPanel buildRatingRow(Order order, GieliDashPlugin plugin)
	{
		JPanel row = new JPanel();
		row.setOpaque(false);
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));

		// The panel is 225px wide minus card padding - keep this row compact (live-test finding)
		if (order.getMyRating() != null)
		{
			JLabel done = new JLabel("Rated " + order.getMyRating() + "/5 ★");
			done.setFont(FontManager.getRunescapeSmallFont());
			done.setForeground(ColorScheme.BRAND_ORANGE);
			row.add(done);
			return row;
		}

		JLabel prompt = new JLabel("Rate ");
		prompt.setFont(FontManager.getRunescapeSmallFont());
		prompt.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(prompt);
		for (int stars = 1; stars <= 5; stars++)
		{
			final int value = stars;
			JButton star = new JButton(String.valueOf(stars));
			star.setFont(FontManager.getRunescapeSmallFont());
			star.setFocusPainted(false);
			star.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			star.setForeground(ColorScheme.BRAND_ORANGE);
			star.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
			star.setMargin(new java.awt.Insets(0, 0, 0, 0));
			star.setToolTipText(stars + (stars == 1 ? " star" : " stars"));
			star.addActionListener(e -> plugin.rateOrder(order, value));
			row.add(star);
			row.add(Box.createHorizontalStrut(2));
		}
		row.add(Box.createHorizontalGlue());
		return row;
	}

	/** "3m 42s" from two server "yyyy-MM-dd HH:mm:ss" timestamps, or null. */
	private static String formatDuration(String claimedAt, String completedAt)
	{
		if (claimedAt == null || completedAt == null)
		{
			return null;
		}
		try
		{
			java.time.format.DateTimeFormatter fmt =
				java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			long seconds = java.time.Duration.between(
				java.time.LocalDateTime.parse(claimedAt, fmt),
				java.time.LocalDateTime.parse(completedAt, fmt)).getSeconds();
			if (seconds < 0)
			{
				return null;
			}
			return seconds >= 3600
				? (seconds / 3600) + "h " + (seconds % 3600) / 60 + "m"
				: (seconds / 60) + "m " + (seconds % 60) + "s";
		}
		catch (java.time.format.DateTimeParseException e)
		{
			return null;
		}
	}

	private static String nextAction(String status, boolean isDasher)
	{
		if (isDasher)
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
		return "arrived".equals(status) ? "delivered" : null;
	}

	private static String nextLabel(String next)
	{
		switch (next)
		{
			case "in_transit":
				return "Start delivery";
			case "arrived":
				return "I've arrived";
			case "delivered":
				return "Mark delivered";
			default:
				return next;
		}
	}

	private static boolean isTerminal(String status)
	{
		return "delivered".equals(status) || "failed".equals(status) || "cancelled".equals(status);
	}

	private static Color statusColor(String status)
	{
		switch (status)
		{
			case "delivered":
				return ColorScheme.PROGRESS_COMPLETE_COLOR;
			case "failed":
			case "cancelled":
				return ColorScheme.PROGRESS_ERROR_COLOR;
			default:
				return ColorScheme.PROGRESS_INPROGRESS_COLOR;
		}
	}

	private static JButton actionButton(String text, Color fg, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		button.setForeground(fg);
		button.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
		button.addActionListener(e -> action.run());
		return button;
	}
}
