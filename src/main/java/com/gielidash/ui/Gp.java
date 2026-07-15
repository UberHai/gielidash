package com.gielidash.ui;

/**
 * gp display formatting: millions get two decimals ("1.25M"), thousands get
 * a K suffix, small amounts get thousands separators.
 */
public final class Gp
{
	private Gp()
	{
	}

	public static String format(long gp)
	{
		if (gp >= 1_000_000)
		{
			return String.format("%.2fM", gp / 1_000_000.0);
		}
		if (gp >= 10_000)
		{
			return String.format("%,dK", gp / 1_000);
		}
		return String.format("%,d", gp);
	}
}
