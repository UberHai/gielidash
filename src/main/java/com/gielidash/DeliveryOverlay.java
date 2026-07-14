package com.gielidash;

import com.gielidash.api.Order;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

class DeliveryOverlay extends OverlayPanel
{
	private static final Color GP_GREEN = ColorScheme.GRAND_EXCHANGE_PRICE;

	private final GieliDashPlugin plugin;
	private final Client client;
	private final GieliDashConfig config;

	@Inject
	private DeliveryOverlay(GieliDashPlugin plugin, Client client, GieliDashConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Order order = plugin.getActiveOrder();
		if (order == null || !config.showOverlay())
		{
			return null;
		}

		boolean isDasher = "dasher".equals(order.getRole());
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(isDasher ? "GieliDash delivery" : "Incoming delivery")
			.color(ColorScheme.BRAND_ORANGE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Status:")
			.right(statusText(order.getStatus()))
			.rightColor(ColorScheme.PROGRESS_INPROGRESS_COLOR)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Goods:")
			.right(order.getItems().size() + (order.getItems().size() == 1 ? " item" : " items"))
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Fee:")
			.right(QuantityFormatter.quantityToStackSize(order.getFeeGp()) + " gp")
			.rightColor(GP_GREEN)
			.build());

		WorldPoint dest = new WorldPoint(order.getDestX(), order.getDestY(), order.getDestPlane());
		if (isDasher)
		{
			// Dasher: my own distance to the drop-off
			Player local = client.getLocalPlayer();
			if (local != null)
			{
				if (client.getWorld() != order.getWorld())
				{
					panelComponent.getChildren().add(LineComponent.builder()
						.left("World:")
						.right("Hop to " + order.getWorld())
						.rightColor(ColorScheme.PROGRESS_ERROR_COLOR)
						.build());
				}
				else
				{
					int tiles = local.getWorldLocation().distanceTo2D(dest);
					panelComponent.getChildren().add(LineComponent.builder()
						.left("Distance:")
						.right(tiles + " tiles")
						.build());
					panelComponent.getChildren().add(LineComponent.builder()
						.left("ETA (run):")
						.right(formatEta(tiles))
						.build());
				}
			}
		}
		else if (order.getCpX() != null && order.getCpY() != null)
		{
			// Requester: the Dasher's last reported distance to me
			WorldPoint dasherAt = new WorldPoint(order.getCpX(), order.getCpY(),
				order.getCpPlane() != null ? order.getCpPlane() : 0);
			int tiles = dasherAt.distanceTo2D(dest);
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Dasher:")
				.right(tiles + " tiles out")
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("ETA (run):")
				.right(formatEta(tiles))
				.build());
			if (order.getCpWorld() != null && order.getCpWorld() != order.getWorld())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Dasher world:")
					.right(String.valueOf(order.getCpWorld()))
					.rightColor(ColorScheme.PROGRESS_INPROGRESS_COLOR)
					.build());
			}
		}

		return super.render(graphics);
	}

	private static String statusText(String status)
	{
		switch (status)
		{
			case "claimed":
				return "Claimed";
			case "in_transit":
				return "In transit";
			case "arrived":
				return "At destination";
			default:
				return status;
		}
	}

	/** Straight-line estimate: running covers 2 tiles per 0.6s game tick. */
	private static String formatEta(int tiles)
	{
		int seconds = (int) Math.ceil(tiles * 0.6 / 2);
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
