package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.disasters.Storms;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * (0.7.18) The wind in a ship's sails: the world's own wind ({@link Storms}, the one that drives its storm cells), harder
 * in rain and hardest under a thunderstorm, and how much of it the sails catch on the ship's heading. With the wind
 * behind her she runs well; across it (a reach) she sails best; she can point up to about 45 degrees off it
 * (close-hauled), and head to wind she lies in irons, her sails flogging, and has to row or come about. (AlekiShips, MIT,
 * gave us the idea of points of sail; the numbers are our own.)
 */
public final class Winds {
	/** The wind's push on the sails, at the wind's least and at its most; the more in rain and under thunder. */
	static final float LEAST = 0.45F, MOST = 1.0F, RAIN = 0.15F, THUNDER = 0.35F;
	/**
	 * How much of the wind the sails catch, by the angle (degrees) between the way she heads and the way the wind blows:
	 * 0 is running before it, 180 straight into it. Between the points it's eased.
	 */
	static final float[] ANGLES = {0.0F, 30.0F, 60.0F, 90.0F, 110.0F, 125.0F, 135.0F, 140.0F};
	static final float[] CATCH = {0.85F, 1.0F, 1.0F, 0.8F, 0.55F, 0.3F, 0.05F, 0.0F};

	/** Her point of sail, for her captain. */
	public enum Point {
		RUNNING, BROAD_REACH, BEAM_REACH, CLOSE_HAULED, IN_IRONS;

		public Component label() {
			return Component.translatable("ship.jaymoskingdoms.point." + this.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private Winds() {
	}

	/** How hard the wind blows at (x, z) for a ship's sails (about 0.45 to 1.5). */
	public static float strength(Level level, double x, double z) {
		float rain = Math.max(level.getRainLevel(1.0F), Storms.cover(level, x, z));
		return LEAST + (MOST - LEAST) * Mth.clamp(Storms.windStrength(), 0.0F, 1.0F) + RAIN * rain + THUNDER * Storms.thunder(level, x, z);
	}

	/** The angle (degrees, 0 to 180) between a ship heading {@code yaw} and the way the wind blows. */
	public static float offWind(float yaw) {
		double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
		float a = Storms.windAngle();
		double dot = fx * Math.cos(a) + fz * Math.sin(a);
		return (float)Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0, 1.0)));
	}

	/** How much of the wind her sails catch heading {@code yaw} (0 in irons to 1). */
	public static float catches(float yaw) {
		float angle = offWind(yaw);
		for (int i = 1; i < ANGLES.length; i++) {
			if (angle <= ANGLES[i]) {
				float t = (angle - ANGLES[i - 1]) / (ANGLES[i] - ANGLES[i - 1]);
				return Mth.lerp(t, CATCH[i - 1], CATCH[i]);
			}
		}
		return 0.0F;
	}

	public static Point point(float yaw) {
		float angle = offWind(yaw);
		if (angle < 35.0F) {
			return Point.RUNNING;
		}
		if (angle < 80.0F) {
			return Point.BROAD_REACH;
		}
		if (angle < 105.0F) {
			return Point.BEAM_REACH;
		}
		return angle < 137.0F ? Point.CLOSE_HAULED : Point.IN_IRONS;
	}

	/** Where the wind blows from, as a compass word. */
	public static Component from() {
		float a = Storms.windAngle();
		return Storms.direction(0.0, 0.0, -Math.cos(a), -Math.sin(a));
	}

	/** How hard it blows, as a word: light, fresh, strong, a gale. */
	public static Component word(float strength) {
		String w = strength < 0.62F ? "light" : strength < 0.85F ? "fresh" : strength < 1.15F ? "strong" : "gale";
		return Component.translatable("ship.jaymoskingdoms.wind." + w);
	}
}
