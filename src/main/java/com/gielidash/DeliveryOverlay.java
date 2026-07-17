package com.gielidash;

import com.gielidash.api.Order;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import com.gielidash.ui.Gp;
import net.runelite.client.ui.overlay.components.TitleComponent;

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

	/** What the right-click menu was last built for, "" when empty. */
	private String menuKey = "";

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Order order = plugin.getActiveOrder();
		if (order == null || !config.showOverlay())
		{
			syncMenu(null);
			return null;
		}
		syncMenu(order);

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
			.left("Reward:")
			.right(Gp.format(order.getFeeGp()) + " gp")
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
		else
		{
			// Requester left the drop-off? The dasher is heading to where the
			// order was POSTED - warn hard so the hand-off doesn't miss
			Player local = client.getLocalPlayer();
			if (local != null)
			{
				if (client.getWorld() != order.getWorld())
				{
					panelComponent.getChildren().add(LineComponent.builder()
						.left("WRONG WORLD")
						.right("Hop to " + order.getWorld())
						.leftColor(ColorScheme.PROGRESS_ERROR_COLOR)
						.rightColor(ColorScheme.PROGRESS_ERROR_COLOR)
						.build());
				}
				else
				{
					int away = local.getWorldLocation().distanceTo2D(dest);
					if (away > 12 || local.getWorldLocation().getPlane() != order.getDestPlane())
					{
						panelComponent.getChildren().add(LineComponent.builder()
							.left("RETURN TO DROP-OFF")
							.right(away + " tiles away")
							.leftColor(ColorScheme.PROGRESS_ERROR_COLOR)
							.rightColor(ColorScheme.PROGRESS_ERROR_COLOR)
							.build());
					}
				}
			}
		}

		if (!isDasher && order.getCpX() != null && order.getCpY() != null)
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

		// The dasher's ETA commitment countdown (both roles care)
		if (order.isActive() && order.getEtaRemaining() != null)
		{
			int left = order.getEtaRemaining();
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Commit:")
				.right(left >= 0
					? String.format("%d:%02d left", left / 60, left % 60)
					: "blown")
				.rightColor(left >= 0
					? ColorScheme.PROGRESS_INPROGRESS_COLOR
					: ColorScheme.PROGRESS_ERROR_COLOR)
				.build());
		}

		// Queued orders (multi-order): one compact line each, primary untouched
		java.util.List<Order> actives = plugin.getActiveOrders();
		if (actives.size() > 1)
		{
			Player local = client.getLocalPlayer();
			for (Order queued : actives)
			{
				if (queued.getId() == order.getId())
				{
					continue;
				}
				String right = Gp.format(queued.getFeeGp()) + " gp";
				if (local != null && client.getWorld() == queued.getWorld())
				{
					right += " · " + local.getWorldLocation().distanceTo2D(new WorldPoint(
						queued.getDestX(), queued.getDestY(), queued.getDestPlane())) + "t";
				}
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Queued #" + queued.getId() + ":")
					.leftColor(ColorScheme.LIGHT_GRAY_COLOR)
					.right(right)
					.rightColor(ColorScheme.LIGHT_GRAY_COLOR)
					.build());
			}
		}

		return super.render(graphics);
	}

	/** Every phase after the current one, in lifecycle order. */
	private static final String[] PHASES = {"claimed", "in_transit", "arrived", "delivered"};

	/**
	 * Shift-right-click the overlay to advance the delivery - no tab hunting
	 * mid-run (user feedback). ALL later phases are offered; a click on a
	 * later one chains through the steps in between. Dasher only; rebuilt
	 * only when the order/status changes.
	 */
	private void syncMenu(@Nullable Order order)
	{
		int at = order != null && "dasher".equals(order.getRole())
			? phaseIndex(order.getStatus())
			: -1;
		String key = at < 0 || at >= PHASES.length - 1 ? "" : order.getId() + ":" + order.getStatus();
		if (key.equals(menuKey))
		{
			return;
		}
		menuKey = key;
		getMenuEntries().clear();
		if (key.isEmpty())
		{
			return;
		}
		final Order target = order;
		// Add in reverse so the game menu lists the nearest phase on top
		for (int i = PHASES.length - 1; i > at; i--)
		{
			final String phase = PHASES[i];
			addMenuEntry(net.runelite.api.MenuAction.RUNELITE_OVERLAY, nextLabel(phase),
				"GieliDash delivery #" + order.getId(),
				e -> plugin.advanceOrderTo(target, phase));
		}
	}

	private static int phaseIndex(String status)
	{
		for (int i = 0; i < PHASES.length; i++)
		{
			if (PHASES[i].equals(status))
			{
				return i;
			}
		}
		return -1;
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
