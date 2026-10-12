package com.jaymoskingdoms.equestrian;

import java.util.Locale;

/**
 * (0.5.40) What a horse learns by being ridden, each to level 5 ({@link Bonding}): the trail (distance walked and
 * trotted: more stamina), racing (distance galloped: a faster top speed), jumping (jumps cleared: a higher jump) and war
 * (fights ridden through: more courage, and the kick). Saved by ordinal: only add at the end.
 */
public enum Discipline {
	TRAIL, RACING, JUMPING, WAR;

	/** The experience each level starts at (level 1 at 0). */
	public static final float[] LEVELS = {0.0F, 100.0F, 300.0F, 700.0F, 1500.0F};

	public static int level(float xp) {
		int level = 1;
		for (int i = 1; i < LEVELS.length; i++) {
			if (xp >= LEVELS[i]) {
				level = i + 1;
			}
		}
		return level;
	}

	/** How far through its level {@code xp} is (0-1; 1 at level 5). */
	public static float progress(float xp) {
		int level = level(xp);
		if (level >= LEVELS.length) {
			return 1.0F;
		}
		float from = LEVELS[level - 1];
		return (xp - from) / (LEVELS[level] - from);
	}

	public String key() {
		return "discipline.jaymoskingdoms." + this.name().toLowerCase(Locale.ROOT);
	}
}
