package com.jaymoskitchen.mill.steam;

import java.util.Locale;

/**
 * What a small boiler is made of (GT New Horizons picks, patch 8). Copper is the first boiler anyone makes; bronze
 * (Jaymo's Powers' tin and bronze, {@code #c:ingots/bronze}) takes the heat better, holds more and more pressure, and
 * drives its engine harder, but bursts the harder for it.
 */
public enum BoilerMetal {
	COPPER(4000, 1000.0F, 1.0F, 1.5F, 2.0F),
	BRONZE(8000, 2000.0F, 1.25F, 2.0F, 2.5F);

	/** Water it holds (mB; a bucket is 1000). */
	public final int water;
	/** Steam it holds before its safety valve lifts (mB of water boiled). */
	public final float steam;
	/** How much of the firebox's heat gets through it (1 = copper's). */
	public final float conducts;
	/** The engine's speed on it at full pressure (1 = a windmill in a fresh breeze). */
	public final float rateAtFull;
	/** How big its burst is (a creeper is 3). */
	public final float burst;

	BoilerMetal(int water, float steam, float conducts, float rateAtFull, float burst) {
		this.water = water;
		this.steam = steam;
		this.conducts = conducts;
		this.rateAtFull = rateAtFull;
		this.burst = burst;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}
