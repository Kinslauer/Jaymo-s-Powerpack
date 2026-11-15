package com.jaymoskingdoms.disasters;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A disaster's strength, from minor to catastrophic (medieval picks, nd-tiers): 1 minor, 2 moderate, 3 severe, 4 great,
 * 5 catastrophic. It changes a disaster's size, how long it lasts, how fast it moves and how hard it hits, not only its
 * damage. Every disaster rolls one when it starts (a Blood Moon lifts it a step); settlements remember the great ones
 * ({@code DisasterMemory}).
 */
public final class Magnitude {
	public static final int MINOR = 1, MODERATE = 2, SEVERE = 3, GREAT = 4, CATASTROPHIC = 5;
	public static final String[] NAMES = {"", "minor", "moderate", "severe", "great", "catastrophic"};
	/** How often each comes up by chance (shares, minor to catastrophic). */
	static final float[] ODDS = {0.0F, 0.45F, 0.30F, 0.15F, 0.08F, 0.02F};
	/** By magnitude: how big it is, how long it lasts, how fast it moves, and how hard it hits (times its own). */
	static final float[] SIZE = {0.0F, 0.7F, 1.0F, 1.3F, 1.7F, 2.2F};
	static final float[] LENGTH = {0.0F, 0.7F, 1.0F, 1.25F, 1.5F, 2.0F};
	static final float[] SPEED = {0.0F, 0.8F, 1.0F, 1.2F, 1.45F, 1.8F};
	static final float[] HARM = {0.0F, 0.6F, 1.0F, 1.5F, 2.2F, 3.0F};

	private Magnitude() {
	}

	/** A magnitude by chance, at least {@code least}; a Blood Moon makes it a step worse. */
	static int roll(ServerLevel level, RandomSource random, int least) {
		float pick = random.nextFloat(), total = 0.0F;
		int m = MINOR;
		for (int i = MINOR; i <= CATASTROPHIC; i++) {
			total += ODDS[i];
			if (pick < total) {
				m = i;
				break;
			}
			m = i;
		}
		if (Calamities.bloodMoon(level)) {
			m++;
		}
		return clamp(Math.max(least, m));
	}

	static int clamp(int m) {
		return Mth.clamp(m, MINOR, CATASTROPHIC);
	}

	static float size(int m) {
		return SIZE[clamp(m)];
	}

	static float length(int m) {
		return LENGTH[clamp(m)];
	}

	static float speed(int m) {
		return SPEED[clamp(m)];
	}

	static float harm(int m) {
		return HARM[clamp(m)];
	}

	/** A great one or worse: settlements remember it, signs come before it. */
	public static boolean great(int m) {
		return m >= GREAT;
	}
}
