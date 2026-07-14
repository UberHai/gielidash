package com.gielidash.ui;

import javax.annotation.Nullable;

/** Formats a "★4.9 (12)" reputation string, or "New" when unrated. */
final class Stars
{
	private Stars()
	{
	}

	static String format(@Nullable Double stars, @Nullable Integer count)
	{
		if (stars == null || count == null || count == 0)
		{
			return "New";
		}
		return "★" + stars + " (" + count + ")";
	}
}
