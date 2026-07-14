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
import net.runelite.client.util.QuantityFormatter;

/**
 * One order card in the sidebar list, styled after Loot Tracker boxes.
 */
class OrderBox extends JPanel
{
	private static final Color GP_GREEN = ColorScheme.GRAND_EXCHANGE_PRICE;

	OrderBox(Order order, ItemManager itemManager, Consumer<Order> onAccept)
	{
		setLayout(new BorderLayout(0, 1));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

		// Header: fee (green) left, world right
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		JLabel fee = new JLabel(QuantityFormatter.quantityToStackSize(order.getFeeGp()) + " gp");
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

		JLabel requester = new JLabel(order.getRequesterName() + "  ·  Cb " + order.getRequesterCombat()
			+ "  ·  " + Stars.format(order.getRequesterStars(), order.getRequesterRatingCount()));
		requester.setFont(FontManager.getRunescapeSmallFont());
		requester.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.add(requester);
		add(body, BorderLayout.CENTER);

		// Accept button - a plugin-panel action, never a game action
		JButton accept = new JButton("Accept order");
		accept.setFont(FontManager.getRunescapeSmallFont());
		accept.setFocusPainted(false);
		accept.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		accept.setForeground(ColorScheme.BRAND_ORANGE);
		accept.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		accept.setHorizontalAlignment(SwingConstants.CENTER);
		accept.addActionListener(e -> onAccept.accept(order));
		accept.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				accept.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				accept.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}
		});
		add(accept, BorderLayout.SOUTH);
	}
}
