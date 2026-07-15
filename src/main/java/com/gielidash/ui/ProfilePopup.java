package com.gielidash.ui;

import com.gielidash.api.Profile;
import java.awt.Component;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

/**
 * Lightweight profile card shown when a player name is clicked. Popup menu so
 * it never steals focus from the game.
 */
public final class ProfilePopup
{
	private ProfilePopup()
	{
	}

	/** Swing EDT only. */
	public static void show(Component anchor, Profile p)
	{
		JPanel card = new JPanel(new DynamicGridLayout(0, 1, 0, 3));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		String check = p.getHiscoreVerified() != null && p.getHiscoreVerified() == 1 ? "✓ " : "";
		JLabel title = new JLabel(check + p.getDisplayName());
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		card.add(title);

		String rep = p.getStars() != null
			? "★" + p.getStars() + " · " + p.getRatingCount()
				+ (p.getRatingCount() == 1 ? " rating" : " ratings")
				+ " from " + p.getUniqueRaters()
				+ (p.getUniqueRaters() == 1 ? " player" : " players")
			: "New - no counted ratings yet";
		card.add(line(rep, ColorScheme.BRAND_ORANGE));

		card.add(line("Cb " + p.getCombatLevel() + " · Total " + p.getTotalLevel()
			+ " · " + p.getQuestPoints() + " QP", null));

		String deliveries = p.getDasherDelivered() + " deliveries";
		if (p.getCompletionRate() != null)
		{
			deliveries += " · " + p.getCompletionRate() + "% completed";
		}
		card.add(line(deliveries, null));

		if (p.getAvgDeliverySeconds() != null)
		{
			String pace = "Avg " + formatSeconds(p.getAvgDeliverySeconds());
			if (p.getSecsPer100Tiles() != null)
			{
				pace += " · " + p.getSecsPer100Tiles() + "s / 100 tiles";
			}
			card.add(line(pace, null));
		}

		card.add(line(p.getOrdersPosted() + " posted · " + p.getOrdersFulfilled() + " received", null));

		StringBuilder unlocks = new StringBuilder();
		if (p.getUnlocks() != null)
		{
			for (Map.Entry<String, Boolean> unlock : p.getUnlocks().entrySet())
			{
				if (Boolean.TRUE.equals(unlock.getValue()))
				{
					if (unlocks.length() > 0)
					{
						unlocks.append(", ");
					}
					unlocks.append(prettify(unlock.getKey()));
				}
			}
		}
		if (unlocks.length() > 0)
		{
			JLabel u = line("Unlocks: " + unlocks, null);
			u.setText("<html>Unlocks: " + unlocks + "</html>");
			card.add(u);
		}

		if (p.getMemberSince() != null)
		{
			card.add(line("Since " + p.getMemberSince().split(" ")[0], ColorScheme.MEDIUM_GRAY_COLOR));
		}

		JPopupMenu popup = new JPopupMenu();
		popup.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR));
		popup.add(card);
		popup.show(anchor, 0, anchor.getHeight());
	}

	private static JLabel line(String text, java.awt.Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color != null ? color : ColorScheme.TEXT_COLOR);
		return label;
	}

	private static String formatSeconds(int seconds)
	{
		return (seconds / 60) + "m " + (seconds % 60) + "s";
	}

	/** fairyRings -> "Fairy rings" */
	private static String prettify(String key)
	{
		String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}
}
