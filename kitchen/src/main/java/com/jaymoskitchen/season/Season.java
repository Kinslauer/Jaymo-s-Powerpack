package com.jaymoskitchen.season;

import java.util.Locale;

/** The four seasons, in order. Each is {@link Seasons#DAYS_PER_SEASON} days: an early, a mid and a late part. */
public enum Season {
	SPRING(-0.25F),
	SUMMER(0.0F),
	AUTUMN(-0.3F),
	WINTER(-0.8F);

	/**
	 * How much colder a temperate land is than its biome says, in biome temperature (under 0.15 it snows and still
	 * water freezes). Winter's takes plains (0.8) and forests (0.7) below it.
	 */
	public final float chill;

	Season(float chill) {
		this.chill = chill;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Season next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
