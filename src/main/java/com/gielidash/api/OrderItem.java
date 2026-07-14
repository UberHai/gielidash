package com.gielidash.api;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class OrderItem
{
	int id;
	int qty;
	String name;
}
