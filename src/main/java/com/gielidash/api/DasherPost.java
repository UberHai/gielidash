package com.gielidash.api;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class DasherPost
{
	private int id;
	private String message;
	@SerializedName("fee_note")
	private String feeNote;
	@SerializedName("dasher_name")
	private String dasherName;
	@SerializedName("dasher_combat")
	private int dasherCombat;
	@SerializedName("dasher_verified")
	private Integer dasherVerified;
	@SerializedName("stars_avg")
	private Double stars;
	@SerializedName("rating_count")
	private Integer ratingCount;
	@SerializedName("is_mine")
	private int isMine;
	@SerializedName("created_at")
	private String createdAt;

	public boolean mine()
	{
		return isMine == 1;
	}
}
