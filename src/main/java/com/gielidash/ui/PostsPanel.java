package com.gielidash.ui;

import com.gielidash.GieliDashPlugin;
import com.gielidash.api.DasherPost;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.PluginErrorPanel;

/**
 * "Posts" tab: Dasher availability ads + a composer for my own post.
 */
public class PostsPanel extends JPanel
{
	private final GieliDashPlugin plugin;
	private final JPanel postsList = new JPanel();
	private final PluginErrorPanel emptyPosts = new PluginErrorPanel();
	private final FlatTextField messageField = new FlatTextField();
	private final FlatTextField feeField = new FlatTextField();
	private final JLabel statusLabel = new JLabel(" ");

	PostsPanel(GieliDashPlugin plugin)
	{
		this.plugin = plugin;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

		// Composer
		add(smallLabel("Advertise as a Dasher"));
		messageField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		messageField.setPreferredSize(new Dimension(0, 30));
		messageField.getTextField().setFont(FontManager.getRunescapeSmallFont());
		messageField.getTextField().setToolTipText("What you deliver and where, e.g. 'Food + potions, anywhere in Kandarin'");
		add(messageField);
		add(Box.createVerticalStrut(4));

		feeField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		feeField.setPreferredSize(new Dimension(0, 30));
		feeField.getTextField().setFont(FontManager.getRunescapeSmallFont());
		feeField.getTextField().setToolTipText("Your rate, e.g. '50k base + 10k per region'");
		add(feeField);
		add(Box.createVerticalStrut(6));

		JPanel buttons = new JPanel();
		buttons.setOpaque(false);
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		JButton post = actionButton("Post ad", () -> submit());
		JButton takeDown = actionButton("Take down", () -> plugin.deactivateDasherPost());
		buttons.add(post);
		buttons.add(Box.createHorizontalStrut(6));
		buttons.add(takeDown);
		add(buttons);
		add(Box.createVerticalStrut(4));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(statusLabel);
		add(Box.createVerticalStrut(8));

		// Feed
		postsList.setLayout(new BoxLayout(postsList, BoxLayout.Y_AXIS));
		postsList.setOpaque(false);
		postsList.setAlignmentX(LEFT_ALIGNMENT);
		emptyPosts.setContent("No Dashers advertising",
			"Dasher availability posts will show up here.");
		postsList.add(emptyPosts);
		add(postsList);
	}

	private void submit()
	{
		String message = messageField.getText() == null ? "" : messageField.getText().trim();
		if (message.isEmpty())
		{
			statusLabel.setText("Write what you deliver first");
			return;
		}
		String feeNote = feeField.getText() == null ? "" : feeField.getText().trim();
		statusLabel.setText("Posting...");
		plugin.createDasherPost(message, feeNote);
	}

	/** Swing EDT only. */
	void setPosts(List<DasherPost> posts)
	{
		statusLabel.setText(" ");
		postsList.removeAll();
		if (posts.isEmpty())
		{
			postsList.add(emptyPosts);
		}
		else
		{
			for (DasherPost post : posts)
			{
				postsList.add(buildPostBox(post));
				postsList.add(Box.createVerticalStrut(6));
			}
		}
		postsList.revalidate();
		postsList.repaint();
	}

	private JPanel buildPostBox(DasherPost post)
	{
		JPanel box = new JPanel(new BorderLayout(0, 2));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		box.setAlignmentX(LEFT_ALIGNMENT);

		JLabel who = new JLabel((post.mine() ? "You" : post.getDasherName())
			+ "  ·  Cb " + post.getDasherCombat()
			+ "  ·  " + Stars.format(post.getStars(), post.getRatingCount()));
		who.setFont(FontManager.getRunescapeSmallFont());
		who.setForeground(post.mine() ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		box.add(who, BorderLayout.NORTH);

		JLabel message = new JLabel("<html>" + post.getMessage() + "</html>");
		message.setFont(FontManager.getRunescapeSmallFont());
		message.setForeground(ColorScheme.TEXT_COLOR);
		box.add(message, BorderLayout.CENTER);

		if (post.getFeeNote() != null && !post.getFeeNote().isEmpty())
		{
			JLabel fee = new JLabel(post.getFeeNote());
			fee.setFont(FontManager.getRunescapeSmallFont());
			fee.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
			box.add(fee, BorderLayout.SOUTH);
		}
		return box;
	}

	private JLabel smallLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private JButton actionButton(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		button.setForeground(ColorScheme.BRAND_ORANGE);
		button.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		button.addActionListener(e -> action.run());
		return button;
	}
}
