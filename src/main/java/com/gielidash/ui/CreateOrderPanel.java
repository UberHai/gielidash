package com.gielidash.ui;

import com.gielidash.GieliDashPlugin;
import com.gielidash.api.OrderItem;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.http.api.item.ItemPrice;

/**
 * "Create" tab: item search with a result picker, basket with GE price
 * estimate, fee, destination = player's location.
 *
 * Layout rule (live-test finding): every vertical stack is a DynamicGridLayout
 * so children are forced to the 225px panel width - labels ellipsize instead
 * of getting clipped.
 */
class CreateOrderPanel extends JPanel
{
	private static final int MAX_RESULTS = 4;
	private static final int SEARCH_DEBOUNCE_MS = 250;

	private final GieliDashPlugin plugin;
	private final ItemManager itemManager;

	private final List<OrderItem> basket = new ArrayList<>();
	/** GE prices captured from search results - ItemManager lookups assert the client thread. */
	private final java.util.Map<Integer, Long> priceCache = new java.util.HashMap<>();
	private final JPanel resultsList = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
	private final JPanel basketList = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
	private final IconTextField searchField = new IconTextField();
	private final FlatTextField feeField = new FlatTextField();
	private final JLabel estimateLabel = new JLabel(" ");
	private final JLabel statusLabel = new JLabel(" ");

	CreateOrderPanel(GieliDashPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;

		setLayout(new DynamicGridLayout(0, 1, 0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(0, 30));
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.addActionListener(e -> doSearch());
		searchField.addClearListener(this::clearResults);

		// Live results while typing (debounced) - top matches appear without Enter
		javax.swing.Timer debounce = new javax.swing.Timer(SEARCH_DEBOUNCE_MS, e -> showLiveResults());
		debounce.setRepeats(false);
		searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e)
			{
				debounce.restart();
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e)
			{
				debounce.restart();
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e)
			{
				debounce.restart();
			}
		});
		add(searchField);
		add(smallLabel("Search an item, click a match"));

		resultsList.setOpaque(false);
		add(resultsList);

		basketList.setOpaque(false);
		add(basketList);

		estimateLabel.setFont(FontManager.getRunescapeSmallFont());
		estimateLabel.setForeground(ColorScheme.GRAND_EXCHANGE_ALCH);
		add(estimateLabel);

		add(smallLabel("Delivery fee (gp)"));
		feeField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		feeField.setPreferredSize(new Dimension(0, 30));
		feeField.getTextField().setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
		feeField.getTextField().setFont(FontManager.getRunescapeFont());
		add(feeField);

		JButton submit = new JButton("Post order at my location");
		submit.setFont(FontManager.getRunescapeSmallFont());
		submit.setFocusPainted(false);
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

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(statusLabel);
	}

	private JLabel smallLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	/** Typing (debounced): show the top matches live for one-click adding. */
	private void showLiveResults()
	{
		String query = searchField.getText();
		if (query == null || query.trim().length() < 2)
		{
			clearResults();
			return;
		}
		List<ItemPrice> results = sortDoseVariants(itemManager.search(query.trim()));
		resultsList.removeAll();
		for (ItemPrice result : results.subList(0, Math.min(MAX_RESULTS, results.size())))
		{
			resultsList.add(buildResultRow(result));
		}
		resultsList.revalidate();
		resultsList.repaint();
	}

	/**
	 * Dose/charge variants like "Prayer potion(1..4)" come back in arbitrary
	 * order - sort each base-name group descending so (4) leads, keeping the
	 * groups themselves in relevance order.
	 */
	private static List<ItemPrice> sortDoseVariants(List<ItemPrice> results)
	{
		java.util.regex.Pattern dosePattern = java.util.regex.Pattern.compile("^(.*)\\((\\d)\\)$");
		java.util.Map<String, Integer> groupOrder = new java.util.HashMap<>();
		for (int i = 0; i < results.size(); i++)
		{
			java.util.regex.Matcher m = dosePattern.matcher(results.get(i).getName());
			String base = m.matches() ? m.group(1) : results.get(i).getName();
			groupOrder.putIfAbsent(base, i);
		}
		List<ItemPrice> sorted = new ArrayList<>(results);
		sorted.sort((a, b) ->
		{
			java.util.regex.Matcher ma = dosePattern.matcher(a.getName());
			java.util.regex.Matcher mb = dosePattern.matcher(b.getName());
			String baseA = ma.matches() ? ma.group(1) : a.getName();
			String baseB = mb.matches() ? mb.group(1) : b.getName();
			int byGroup = Integer.compare(groupOrder.get(baseA), groupOrder.get(baseB));
			if (byGroup != 0)
			{
				return byGroup;
			}
			int doseA = ma.matches() ? Integer.parseInt(ma.group(2)) : -1;
			int doseB = mb.matches() ? Integer.parseInt(mb.group(2)) : -1;
			return Integer.compare(doseB, doseA); // descending: (4) first
		});
		return sorted;
	}

	/** Enter: add the exact-name match; otherwise the live list is there to click. */
	private void doSearch()
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

		ItemPrice exact = results.stream()
			.filter(r -> r.getName().equalsIgnoreCase(query.trim()))
			.findFirst().orElse(null);
		if (exact != null || results.size() == 1)
		{
			addToBasket(exact != null ? exact : results.get(0));
			return;
		}
		setStatus("Pick the item you mean", false);
	}

	private JPanel buildResultRow(ItemPrice result)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel name = new JLabel(result.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(ColorScheme.TEXT_COLOR);
		name.setIconTextGap(6);
		itemManager.getImage(result.getId()).addTo(name);
		row.add(name, BorderLayout.CENTER);

		JLabel price = new JLabel(QuantityFormatter.quantityToStackSize(result.getPrice()) + " gp");
		price.setFont(FontManager.getRunescapeSmallFont());
		price.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
		row.add(price, BorderLayout.EAST);

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				addToBasket(result);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});
		return row;
	}

	private void addToBasket(ItemPrice item)
	{
		priceCache.put(item.getId(), (long) item.getPrice());
		basket.add(new OrderItem(item.getId(), 1, item.getName()));
		searchField.setText("");
		clearResults();
		setStatus(" ", false);
		rebuildBasket();
	}

	private void clearResults()
	{
		resultsList.removeAll();
		resultsList.revalidate();
		resultsList.repaint();
	}

	private void rebuildBasket()
	{
		basketList.removeAll();
		long estimate = 0;
		for (int i = 0; i < basket.size(); i++)
		{
			final int index = i;
			OrderItem item = basket.get(i);
			estimate += priceCache.getOrDefault(item.getId(), 0L) * item.getQty();

			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

			JLabel name = new JLabel(item.getName() + " ×" + item.getQty());
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setForeground(ColorScheme.TEXT_COLOR);
			itemManager.getImage(item.getId(), item.getQty(), item.getQty() > 1).addTo(name);
			name.setIconTextGap(6);
			row.add(name, BorderLayout.CENTER);

			JPanel controls = new JPanel(new DynamicGridLayout(1, 3, 2, 0));
			controls.setOpaque(false);
			controls.add(miniButton("+", () -> changeQty(index, 1)));
			controls.add(miniButton("-", () -> changeQty(index, -1)));
			controls.add(miniButton("x", () -> removeItem(index)));
			row.add(controls, BorderLayout.EAST);

			basketList.add(row);
		}
		estimateLabel.setText(basket.isEmpty() ? " "
			: "Items: ~" + QuantityFormatter.quantityToStackSize(estimate) + " gp (GE)");
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
		button.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
		button.setMargin(new java.awt.Insets(0, 0, 0, 0));
		button.addActionListener(e -> action.run());
		return button;
	}

	private void changeQty(int index, int delta)
	{
		OrderItem item = basket.get(index);
		int qty = Math.max(1, item.getQty() + delta);
		basket.set(index, new OrderItem(item.getId(), qty, item.getName()));
		rebuildBasket();
	}

	private void removeItem(int index)
	{
		basket.remove(index);
		rebuildBasket();
	}

	/** Refill the basket from a past order (Reorder). Prices computed on the client thread. */
	void loadBasket(List<OrderItem> items, java.util.Map<Integer, Long> prices)
	{
		basket.clear();
		priceCache.putAll(prices);
		for (OrderItem item : items)
		{
			basket.add(new OrderItem(item.getId(), item.getQty(), item.getName()));
		}
		clearResults();
		setStatus("Basket loaded from your past order", false);
		rebuildBasket();
	}

	private void submitOrder()
	{
		if (!plugin.isLoggedIn())
		{
			setStatus("Log in first", true);
			return;
		}
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
