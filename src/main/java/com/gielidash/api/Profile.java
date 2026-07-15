package com.gielidash.api;

import java.util.Map;
import lombok.Data;

@Data
public class Profile
{
	private String displayName;
	private int combatLevel;
	private int totalLevel;
	private int questPoints;
	private Map<String, Boolean> unlocks;
	private Integer hiscoreVerified;
	private Double stars;
	private int ratingCount;
	private int uniqueRaters;
	private boolean isNew;
	private Integer completionRate;
	private Integer avgDeliverySeconds;
	private Integer secsPer100Tiles;
	private int dasherDelivered;
	private int dasherFailed;
	private int ordersPosted;
	private int ordersFulfilled;
	private String memberSince;
}
