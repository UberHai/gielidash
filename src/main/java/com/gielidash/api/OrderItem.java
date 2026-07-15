package com.gielidash.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * name is CLIENT-RESOLVED from the game's item cache - the server stores and
 * returns only {id, qty} so no player-authored text can ride along.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderItem
{
	private int id;
	private int qty;
	private transient String name;
}
