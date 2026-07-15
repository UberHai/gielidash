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

/**
 * A card in the Mine tab: my order, its status, and the next action for my role.
 */
class MyOrderBox extends JPanel
{
	MyOrderBox(Order order, GieliDashPlugin plugin, boolean showFeeNudge)
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
				: (order.getDirectedTo() != null
					? "waiting for " + order.getDirectedTo() + " to accept"
					: "waiting for a dasher"));
		JLabel who = new JLabel(counterpart);
		who.setFont(FontManager.getRunescapeSmallFont());
		who.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		String counterpartName = isDasher ? order.getRequesterName() : order.getDasherName();
		if (counterpartName != null)
		{
			who.setToolTipText("Click for " + counterpartName + "'s profile");
			who.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			who.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e)
				{
					ProfilePopup.fetchAndShow(plugin, who, counterpartName);
				}
			});
		}
		body.add(who);

		JLabel meta = new JLabel("W" + order.getWorld()
			+ "  ·  " + Gp.format(order.getFeeGp()) + " gp reward");
		meta.setFont(FontManager.getRunescapeSmallFont());
		meta.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.add(meta);

		// ETA commitment countdown on active orders
		if (order.isActive() && order.getEtaRemaining() != null)
		{
			int left = order.getEtaRemaining();
			JLabel eta = new JLabel(left >= 0
				? "ETA: " + (left / 60) + ":" + String.format("%02d", left % 60) + " left"
				: "ETA blown " + (-left / 60) + "m ago");
			eta.setFont(FontManager.getRunescapeSmallFont());
			eta.setForeground(left >= 0
				? ColorScheme.PROGRESS_INPROGRESS_COLOR
				: ColorScheme.PROGRESS_ERROR_COLOR);
			body.add(eta);
		}

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
		if (showFeeNudge)
		{
			actions.add(Box.createHorizontalStrut(6));
			actions.add(actionButton("Raise reward +25%", ColorScheme.GRAND_EXCHANGE_PRICE,
				() -> plugin.raiseFee(order)));
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

	/** Only the dasher advances the lifecycle - they made the hand-off (user-decided). */
	private static String nextAction(String status, boolean isDasher)
	{
		if (!isDasher)
		{
			return null;
		}
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
