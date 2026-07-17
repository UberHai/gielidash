package com.gielidash.ui;

import javax.annotation.Nullable;

/**
 * Names a coordinate after the nearest well-known hub. Purely client-side -
 * the server only ever stores raw coords, so no player- or server-authored
 * place text reaches the UI.
 */
final class AreaNames
{
	/** Accept the nearest hub only within this Chebyshev radius (tiles). */
	private static final int MAX_TILES = 250;

	private static final Object[][] HUBS = {
		{"the Grand Exchange", 3165, 3487},
		{"Varrock", 3213, 3428},
		{"Lumbridge", 3222, 3218},
		{"Draynor Village", 3093, 3244},
		{"Falador", 2965, 3380},
		{"Port Sarim", 3022, 3208},
		{"Rimmington", 2957, 3214},
		{"Edgeville", 3087, 3496},
		{"Barbarian Village", 3082, 3420},
		{"Al Kharid", 3293, 3180},
		{"Shantay Pass", 3304, 3124},
		{"Pollnivneach", 3359, 2972},
		{"Nardah", 3427, 2892},
		{"Sophanem", 3277, 2784},
		{"Ardougne", 2662, 3305},
		{"Yanille", 2606, 3093},
		{"Castle Wars", 2440, 3090},
		{"Seers' Village", 2726, 3485},
		{"Catherby", 2809, 3435},
		{"Taverley", 2895, 3443},
		{"Burthorpe", 2898, 3546},
		{"the Gnome Stronghold", 2461, 3443},
		{"the Fishing Guild", 2611, 3393},
		{"the Barbarian Outpost", 2531, 3577},
		{"Piscatoris", 2340, 3665},
		{"Prifddinas", 3257, 6088},
		{"Lletya", 2340, 3172},
		{"Zanaris", 2452, 4446},
		{"Keldagrim", 2905, 10200},
		{"Rellekka", 2660, 3657},
		{"Neitiznot", 2336, 3806},
		{"Miscellania", 2536, 3865},
		{"Canifis", 3495, 3489},
		{"Port Phasmatys", 3687, 3502},
		{"Darkmeyer", 3607, 3366},
		{"Ver Sinhaza", 3652, 3215},
		{"Brimhaven", 2760, 3178},
		{"Musa Point", 2916, 3153},
		{"Tai Bwo Wannai", 2790, 3066},
		{"Shilo Village", 2852, 2955},
		{"Mor Ul Rek", 2455, 5150},
		{"Corsair Cove", 2569, 2862},
		{"Ape Atoll", 2760, 2770},
		{"Mos Le'Harmless", 3670, 2975},
		{"Fossil Island", 3730, 3800},
		{"Hosidius", 1750, 3600},
		{"Shayzien", 1504, 3576},
		{"Arceuus", 1650, 3745},
		{"Lovakengj", 1530, 3800},
		{"Port Piscarilius", 1800, 3740},
		{"the Farming Guild", 1249, 3719},
		{"the Wintertodt Camp", 1630, 3940},
		{"Mount Karuulm", 1311, 3798},
		{"Civitas illa Fortis", 1700, 3130},
		{"Aldarin", 1420, 2965},
		{"Cam Torum", 1450, 3110},
		{"Ferox Enclave", 3130, 3630},
	};

	private AreaNames()
	{
	}

	/** Nearest hub name, or null when nowhere recognizable is close. */
	@Nullable
	static String nearest(int x, int y)
	{
		String best = null;
		int bestDist = Integer.MAX_VALUE;
		for (Object[] hub : HUBS)
		{
			int dist = Math.max(Math.abs(x - (int) hub[1]), Math.abs(y - (int) hub[2]));
			if (dist < bestDist)
			{
				bestDist = dist;
				best = (String) hub[0];
			}
		}
		return bestDist <= MAX_TILES ? best : null;
	}
}
