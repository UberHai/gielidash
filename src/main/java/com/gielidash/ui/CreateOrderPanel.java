package com.gielidash.ui;

import com.gielidash.GieliDashPlugin;
import com.gielidash.api.OrderItem;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.http.api.item.ItemPrice;

/**
 * "Create" tab: item search -> basket, fee, destination = player's location.
 */
class CreateOrderPanel extends JPanel
{
	private final GieliDashPlugin plugin;
	private final ItemManager itemManager;

	private final List<OrderItem> basket = new ArrayList<>();
	private final JPanel basketList = new JPanel();
	private final IconTextField searchField = new IconTextField();
	private final FlatTextField feeField = new FlatTextField();
	private final JLabel statusLabel = new JLabel(" ");

	CreateOrderPanel(GieliDashPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

		// Item search
		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new java.awt.Dimension(0, 30));
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.addActionListener(e -> addFirstMatch());
		add(searchField);
		add(Box.createVerticalStrut(6));

		JLabel hint = smallLabel("Type an item name, press Enter to add");
		add(hint);
		add(Box.createVerticalStrut(6));

		// Basket
		basketList.setLayout(new BoxLayout(basketList, BoxLayout.Y_AXIS));
		basketList.setOpaque(false);
		add(basketList);
		add(Box.createVerticalStrut(8));

		// Fee
		add(smallLabel("Delivery fee (gp, on top of item cost)"));
		feeField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		feeField.setPreferredSize(new java.awt.Dimension(0, 30));
		feeField.getTextField().setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
		feeField.getTextField().setFont(FontManager.getRunescapeFont());
		add(feeField);
		add(Box.createVerticalStrut(10));

		// Submit
		JButton submit = new JButton("Post order at my location");
		submit.setFont(FontManager.getRunescapeSmallFont());
		submit.setFocusPainted(false);
		submit.setAlignmentX(LEFT_ALIGNMENT);
		submit.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		submit.setForeground(ColorScheme.BRAND_ORANGE);
		submit.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
		submit.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				submit.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				submit.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}
		});
		submit.addActionListener(e -> submitOrder());
		add(submit);
		add(Box.createVerticalStrut(6));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(statusLabel);
	}

	private JLabel smallLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private void addFirstMatch()
	{
		String query = searchField.getText();
		if (query == null || query.trim().isEmpty() || basket.size() >= 28)
		{
			return;
		}
		List<ItemPrice> results = itemManager.search(query.trim());
		if (results.isEmpty())
		{
			setStatus("No tradeable item matches '" + query.trim() + "'", true);
			return;
		}
		ItemPrice match = results.get(0);
		basket.add(new OrderItem(match.getId(), 1, match.getName()));
		searchField.setText("");
		setStatus(" ", false);
		rebuildBasket();
	}

	private void rebuildBasket()
	{
		basketList.removeAll();
		for (int i = 0; i < basket.size(); i++)
		{
			final int index = i;
			OrderItem item = basket.get(i);

			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

			JLabel name = new JLabel(item.getName() + " ×" + item.getQty());
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setForeground(ColorScheme.TEXT_COLOR);
			itemManager.getImage(item.getId(), item.getQty(), item.getQty() > 1).addTo(name);
			name.setIconTextGap(6);
			row.add(name, BorderLayout.WEST);

			JPanel controls = new JPanel();
			controls.setOpaque(false);
			controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
			controls.add(miniButton("+", () -> changeQty(index, 1)));
			controls.add(miniButton("-", () -> changeQty(index, -1)));
			controls.add(miniButton("x", () -> removeItem(index)));
			row.add(controls, BorderLayout.EAST);

			basketList.add(row);
		}
		basketList.revalidate();
		basketList.repaint();
	}

	private JButton miniButton(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
		button.addActionListener(e -> action.run());
		return button;
	}

	private void changeQty(int index, int delta)
	{
		OrderItem item = basket.get(index);
		int qty = Math.max(1, Math.min(2147483647, item.getQty() + delta));
		basket.set(index, new OrderItem(item.getId(), qty, item.getName()));
		rebuildBasket();
	}

	private void removeItem(int index)
	{
		basket.remove(index);
		rebuildBasket();
	}

	private void submitOrder()
	{
		if (basket.isEmpty())
		{
			setStatus("Add at least one item first", true);
			return;
		}
		long fee;
		try
		{
			fee = Long.parseLong(feeField.getText().trim().replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			setStatus("Enter the fee as a number, e.g. 150000", true);
			return;
		}
		if (fee < 0)
		{
			setStatus("The fee can't be negative", true);
			return;
		}

		setStatus("Posting order...", false);
		plugin.createOrderAtMyLocation(new ArrayList<>(basket), fee, result ->
			SwingUtilities.invokeLater(() ->
			{
				if (result.startsWith("#"))
				{
					basket.clear();
					feeField.setText("");
					rebuildBasket();
					setStatus("Order " + result + " posted", false);
				}
				else
				{
					setStatus(result, true);
				}
			}));
	}

	private void setStatus(String text, boolean isError)
	{
		statusLabel.setText(text);
		statusLabel.setForeground(isError ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
	}
}
