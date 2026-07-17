package com.gielidash;

import com.gielidash.api.ApiClient;
import com.gielidash.api.ApiException;
import com.gielidash.api.Order;
import com.gielidash.api.OrderItem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Player;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Passively observes the trade screen. When a trade completes with the active
 * order's counterpart, files a trade report (items I gave + coins I gave) so
 * the server can mark the hand-off verified. Read-only: never touches the trade.
 */
@Slf4j
@Singleton
class TradeObserver
{
	private final Client client;
	private final ScheduledExecutorService executor;
	private final ApiClient api;
	private final GieliDashPlugin plugin;

	@Nullable
	private volatile String tradePartner;
	private volatile List<OrderItem> myOffer = new ArrayList<>();

	@Inject
	private TradeObserver(Client client, ScheduledExecutorService executor,
		ApiClient api, GieliDashPlugin plugin)
	{
		this.client = client;
		this.executor = executor;
		this.api = api;
		this.plugin = plugin;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.TRADEMAIN && event.getGroupId() != InterfaceID.TRADECONFIRM)
		{
			return;
		}
		// Find the "Trading with: <name>" label without depending on a child index
		for (int child = 0; child < 40; child++)
		{
			Widget widget = client.getWidget(event.getGroupId(), child);
			if (widget == null || widget.getText() == null)
			{
				continue;
			}
			String text = Text.removeTags(widget.getText());
			int idx = text.indexOf("Trading with:");
			if (idx >= 0)
			{
				tradePartner = normalize(text.substring(idx + "Trading with:".length()));
				log.debug("Trade opened with {}", tradePartner);
				return;
			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.TRADEOFFER)
		{
			return;
		}
		ItemContainer container = event.getItemContainer();
		List<OrderItem> offer = new ArrayList<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > 0 && item.getQuantity() > 0)
				{
					String name = client.getItemDefinition(item.getId()).getName();
					offer.add(new OrderItem(item.getId(), item.getQuantity(), name));
				}
			}
		}
		myOffer = offer;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.TRADE
			|| !"Accepted trade.".equals(Text.removeTags(event.getMessage())))
		{
			return;
		}

		Order order = plugin.getActiveOrder();
		String partner = tradePartner;
		List<OrderItem> offer = myOffer;
		tradePartner = null;
		myOffer = new ArrayList<>();

		if (order == null || partner == null)
		{
			return;
		}

		String counterpart = "dasher".equals(order.getRole())
			? order.getRequesterName()
			: order.getDasherName();
		if (counterpart == null || !normalize(counterpart).equalsIgnoreCase(partner))
		{
			log.debug("Trade partner {} does not match order counterpart {}", partner, counterpart);
			return;
		}

		long gp = offer.stream()
			.filter(item -> item.getId() == ItemID.COINS)
			.mapToLong(OrderItem::getQty)
			.sum();
		List<OrderItem> items = new ArrayList<>();
		for (OrderItem item : offer)
		{
			if (item.getId() != ItemID.COINS)
			{
				items.add(item);
			}
		}

		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		final int x = local.getWorldLocation().getX();
		final int y = local.getWorldLocation().getY();
		final int plane = local.getWorldLocation().getPlane();
		final int world = client.getWorld();

		executor.execute(() ->
		{
			try
			{
				api.submitTradeReport(order.getId(), counterpart, items, gp, world, x, y, plane);
				log.debug("Trade report filed for order {}", order.getId());
			}
			catch (ApiException e)
			{
				log.warn("Trade report failed: {}", e.getMessage());
			}
		});
	}

	private static String normalize(String name)
	{
		return Text.removeTags(name).replace('\u00A0', ' ').trim();
	}
}
