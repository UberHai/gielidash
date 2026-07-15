package com.gielidash.ui;

import com.gielidash.api.Order;
import com.gielidash.api.OrderItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One order card in the sidebar list, styled after Loot Tracker boxes.
 */
class OrderBox extends JPanel
{
	private static final Color GP_GREEN = ColorScheme.GRAND_EXCHANGE_PRICE;

	OrderBox(Order order, ItemManager itemManager, Consumer<Order> onAccept)
	{
		this(order, itemManager, onAccept, null);
	}

	/** With onDecline non-null this renders as an incoming request (Accept + Decline). */
	OrderBox(Order order, ItemManager itemManager, Consumer<Order> onAccept, Consumer<Order> onDecline)
	{
		setLayout(new BorderLayout(0, 1));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

		// Header: fee (green) left, world right
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		JLabel fee = new JLabel(Gp.format(order.getFeeGp()) + " gp");
		fee.setFont(FontManager.getRunescapeBoldFont());
		fee.setForeground(GP_GREEN);
		JLabel world = new JLabel("W" + order.getWorld());
		world.setFont(FontManager.getRunescapeSmallFont());
		world.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(fee, BorderLayout.WEST);
		header.add(world, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		// Body: items with icons, then requester line
		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

		for (OrderItem item : order.getItems())
		{
			JLabel line = new JLabel(item.getName() + " ×" + item.getQty());
			line.setFont(FontManager.getRunescapeSmallFont());
			line.setForeground(ColorScheme.TEXT_COLOR);
			line.setIconTextGap(6);
			itemManager.getImage(item.getId(), item.getQty(), item.getQty() > 1).addTo(line);
			body.add(line);
			body.add(Box.createVerticalStrut(2));
		}

		// GE cost estimate so Dashers know what they'll front. Computed on the
		// client thread during the poll (ItemManager price lookups assert it) -
		// never call itemManager.getItemPrice from this EDT constructor.
		if (order.getFrontCostGp() != null)
		{
			long collect = order.getFrontCostGp() + order.getFeeGp();
			JLabel cost = new JLabel("Front ~" + Gp.format(order.getFrontCostGp())
				+ " gp · collect ~" + Gp.format(collect) + " gp");
			cost.setFont(FontManager.getRunescapeSmallFont());
			cost.setForeground(ColorScheme.GRAND_EXCHANGE_ALCH);
			cost.setToolTipText("You buy the items (~"
				+ Gp.format(order.getFrontCostGp())
				+ " gp), the requester pays items + "
				+ Gp.format(order.getFeeGp()) + " gp fee on delivery");
			body.add(cost);
		}

		String check = order.getRequesterVerified() != null && order.getRequesterVerified() == 1 ? "✓ " : "";
		JLabel requester = new JLabel(check + order.getRequesterName() + "  ·  Cb " + order.getRequesterCombat()
			+ "  ·  " + Stars.format(order.getRequesterStars(), order.getRequesterRatingCount()));
		requester.setFont(FontManager.getRunescapeSmallFont());
		requester.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.add(requester);
		add(body, BorderLayout.CENTER);

		// Skill-total world I can't enter: warn, and don't offer Accept
		boolean locked = order.getLockedRequirement() != null;
		if (locked)
		{
			JLabel warning = new JLabel("Requires " + order.getLockedRequirement() + "+ total to enter world");
			warning.setFont(FontManager.getRunescapeSmallFont());
			warning.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			body.add(warning);
		}

		// Accept (and for requests, Decline) - plugin-panel actions, never game actions
		JButton accept = bottomButton("Accept order", ColorScheme.BRAND_ORANGE, () -> onAccept.accept(order));
		if (onDecline == null)
		{
			if (!locked)
			{
				add(accept, BorderLayout.SOUTH);
			}
		}
		else if (locked)
		{
			add(bottomButton("Decline", ColorScheme.LIGHT_GRAY_COLOR, () -> onDecline.accept(order)),
				BorderLayout.SOUTH);
		}
		else
		{
			JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
			buttons.setOpaque(false);
			buttons.add(accept);
			buttons.add(bottomButton("Decline", ColorScheme.LIGHT_GRAY_COLOR, () -> onDecline.accept(order)));
			add(buttons, BorderLayout.SOUTH);
		}
	}

	private static JButton bottomButton(String text, Color fg, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		button.setForeground(fg);
		button.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.addActionListener(e -> action.run());
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}
		});
		return button;
	}
}
