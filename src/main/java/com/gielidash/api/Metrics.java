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
	private Double stars;
	private int ratingCount;
}
