package com.gielidash.api;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class DasherPost
{
	private int id;
	/** Whitelisted service type ("Food & potions") - no free text. */
	private String message;
	private String region;
	/** Numeric base fee as a string. */
	@SerializedName("fee_note")
	private String feeNote;

	public long baseFeeGp()
	{
		try
		{
			return Long.parseLong(feeNote);
		}
		catch (NumberFormatException | NullPointerException e)
		{
			return 0;
		}
	}
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
	/** Where the dasher stood when the ad posted - coords only, named client-side. */
	@SerializedName("posted_x")
	private Integer postedX;
	@SerializedName("posted_y")
	private Integer postedY;
	@SerializedName("posted_plane")
	private Integer postedPlane;
	@SerializedName("posted_world")
	private Integer postedWorld;

	public boolean mine()
	{
		return isMine == 1;
	}
}
