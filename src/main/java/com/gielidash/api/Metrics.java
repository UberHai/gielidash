package com.gielidash.api;

import lombok.Data;

@Data
public class Metrics
{
	private int ordersPosted;
	private int ordersReceived;
	private int deliveriesDone;
	private long gpEarned;
	private long gpSpent;
	private Integer avgDeliverySeconds;
	/** Lifetime accept-to-delivered time as a dasher, for gp/active-hour. */
	private long activeSeconds;
	private Double stars;
	private int ratingCount;
}
