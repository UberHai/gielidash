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
	@SerializedName("expires_at")
	private String expiresAt;
	@SerializedName("requester_name")
	private String requesterName;
	@SerializedName("requester_combat")
	private int requesterCombat;
}
