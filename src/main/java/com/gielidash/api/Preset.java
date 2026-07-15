package com.gielidash.api;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A saved order template ("CoX kit"): basket + fee, stored in plugin config. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Preset
{
	private String name;
	private long feeGp;
	private List<OrderItem> items;
}
