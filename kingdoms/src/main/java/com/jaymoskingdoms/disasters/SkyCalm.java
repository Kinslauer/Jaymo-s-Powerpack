package com.jaymoskingdoms.disasters;

import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.34) Hooks for Jaymo's Powers' Smoke cloudshapers (ascended Smoke, "Harness the Sky"), found by reflection so
 * neither mod needs the other. Holding C under a storm calms it: it breaks up sooner, a supercell held
 * {@link #SPENT_SUPERCELL} seconds never puts down its tornado and becomes a thunderstorm, a thunderstorm held
 * {@link #SPENT_THUNDER} seconds more becomes a rain cell. Holding C at a tornado unwinds it a rank every
 * {@link #RANK_SECONDS} seconds (monster to tornado, tornado to dust devil) and shortens its life; only a caller allowed
 * to {@code kill} (Smoke with both fates, fully mastered) can finish a dust devil off.
 */
public final class SkyCalm {
	/** Each second of calming takes this many ticks off a cell's life. */
	static final int LIFE_PER_SECOND = 60;
	static final float SPENT_SUPERCELL = 10.0F, SPENT_THUNDER = 30.0F;
	/** A cell counts as overhead once this much of it lies over the caller. */
	static final float OVERHEAD = 0.3F;
	static final float RANK_SECONDS = 5.0F;
	/** Each rank lost takes this share off what's left of a tornado's life. */
	static final float LIFE_CUT = 0.25F;

	private SkyCalm() {
	}

	/** Calms the storm cell over (x, z) by {@code seconds}. Returns false if no cell is overhead. Server, Overworld. */
	public static boolean calm(Level level, double x, double z, float seconds) {
		if (level.isClientSide() || level.dimension() != Level.OVERWORLD) {
			return false;
		}
		Storm best = null;
		float most = OVERHEAD;
		for (Storm s : Storms.SERVER) {
			float over = s.over(x, z);
			if (over >= most) {
				most = over;
				best = s;
			}
		}
		if (best == null) {
			return false;
		}
		best.calmed += seconds;
		// it breaks up sooner (never sooner than a quarter of its gathering from now, and never later than it would have)
		int floor = Math.min(best.life, best.age + Storms.GATHER_TICKS / 4);
		best.life = Math.max(floor, best.life - Math.round(seconds * LIFE_PER_SECOND));
		if (best.kind == Storm.SUPERCELL && best.calmed >= SPENT_SUPERCELL) {
			best.spawned = true;
			best.kind = Storm.THUNDER;
		} else if (best.kind == Storm.THUNDER && best.calmed >= SPENT_THUNDER) {
			best.kind = Storm.RAIN;
		}
		return true;
	}

	/**
	 * Unwinds the nearest tornado within {@code range} of (x, z) by {@code seconds}. Returns where it is (x, y, z), or
	 * null if there's none. With {@code kill} a dust devil unwound a rank's worth ropes out.
	 */
	public static double @Nullable [] unwind(Level level, double x, double z, double range, float seconds, boolean kill) {
		if (level.isClientSide()) {
			return null;
		}
		Tornado best = null;
		double nearest = range * range;
		for (Tornado t : Tornadoes.SERVER) {
			double d = (t.x - x) * (t.x - x) + (t.z - z) * (t.z - z);
			if (d <= nearest) {
				nearest = d;
				best = t;
			}
		}
		if (best == null) {
			return null;
		}
		best.unwound += seconds;
		if (best.unwound >= RANK_SECONDS) {
			if (best.size > Tornado.DUST_DEVIL) {
				best.unwound = 0.0F;
				best.size--;
				best.life = best.age + Math.round((best.life - best.age) * (1.0F - LIFE_CUT));
			} else if (kill) {
				// it ropes out
				best.life = Math.min(best.life, best.age + Tornadoes.FORM_TICKS);
			} else {
				best.unwound = RANK_SECONDS;
			}
		}
		return new double[] {best.x, best.y, best.z};
	}
}
