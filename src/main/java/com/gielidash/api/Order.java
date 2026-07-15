package com.gielidash.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;

@Data
public class Order
{
	private int id;
	private String status;
	private List<OrderItem> items;
	@SerializedName("dest_x")
	private int destX;
	@SerializedName("dest_y")
	private int destY;
	@SerializedName("dest_plane")
	private int destPlane;
	private int world;
	@SerializedName("fee_gp")
	private long feeGp;
	private String notes;
	@SerializedName("created_at")
	private String createdAt;
	@SerializedName("claimed_at")
	private String claimedAt;
	@SerializedName("completed_at")
	private String completedAt;
	@SerializedName("expires_at")
	private String expiresAt;
	/** Counterpart's last heartbeat - /orders/mine only, null when never sent. */
	@SerializedName("cp_x")
	private Integer cpX;
	@SerializedName("cp_y")
	private Integer cpY;
	@SerializedName("cp_plane")
	private Integer cpPlane;
	@SerializedName("cp_world")
	private Integer cpWorld;
	@SerializedName("requester_name")
	private String requesterName;
	@SerializedName("requester_combat")
	private int requesterCombat;
	/** 1 = hiscores-verified, 0 = failed verification, null = unchecked. */
	@SerializedName("requester_verified")
	private Integer requesterVerified;
	@SerializedName("dasher_name")
	private String dasherName;
	@SerializedName("dasher_verified")
	private Integer dasherVerified;
	/** Dasher this open order is reserved for - /orders/mine only. */
	@SerializedName("directed_to")
	private String directedTo;
	/** "requester" or "dasher" - only present on /orders/mine responses. */
	private String role;
	/** Stars I already gave on this order - /orders/mine only, null if unrated. */
	@SerializedName("my_rating")
	private Integer myRating;
	@SerializedName("requester_stars")
	private Double requesterStars;
	@SerializedName("requester_rating_count")
	private Integer requesterRatingCount;
	@SerializedName("dasher_stars")
	private Double dasherStars;
	@SerializedName("dasher_rating_count")
	private Integer dasherRatingCount;

	/** GE cost of the items, computed client-side ON THE CLIENT THREAD during polls. */
	private transient Long frontCostGp;

	public boolean isTerminal()
	{
		return "delivered".equals(status) || "failed".equals(status) || "cancelled".equals(status);
	}

	public boolean isActive()
	{
		return "claimed".equals(status) || "in_transit".equals(status) || "arrived".equals(status);
	}
}
