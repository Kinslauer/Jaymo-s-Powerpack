package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.disasters.Storms;
import com.jaymoskingdoms.sailing.WindPush;
import com.jaymoskingdoms.sailing.Winds;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Cloth in the wind, drawn by each client from the wind the server syncs ({@link Winds#clientStrength},
 * {@code Storms.clientWindAngle}): banners' flags and the hooded cloak's cape lean downwind and flutter, less behind a
 * wall ({@link WindPush#sheltered}).
 */
public final class WindCloth {
	/** The wind at which cloth starts to stir and at which it is hanging furthest out. */
	static final float FROM = 0.3F, FULL = 1.5F;
	/** Cloth in shelter moves this share as far. */
	static final float SHELTERED = 0.25F;
	/** A banner's flag leans this far (radians) at the most, in steps of 1/LEVELS. */
	public static final float BANNER_LEAN = 0.3F;
	public static final int LEVELS = 10;
	/** A banner's phase carries its lean as an integer part above this (the swing repeats every whole phase, so it shows nothing). */
	public static final float CODE_BASE = 100.0F;
	/** A cloak's cape streams back this far (radians) at the most, to the side this far, presses forward this share of that, and flutters this much. */
	public static final float CLOAK_LEAN = 0.35F, CLOAK_SIDE = 0.2F, CLOAK_FRONT = 0.3F, CLOAK_FLAP = 0.05F;
	/** The flutter's speed (radians a tick). */
	public static final float FLAP_SPEED = 0.45F;

	private WindCloth() {
	}

	/** How much the wind stirs cloth at {@code at} (0 to 1, or less in shelter), from the world's wind as the client has it. */
	public static float blow(Level level, double x, double y, double z, @Nullable Entity wearer) {
		if (level.dimension() != Level.OVERWORLD) {
			return 0.0F;
		}
		float b = Mth.clamp((Winds.clientStrength(level, x, z) - FROM) / (FULL - FROM), 0.0F, 1.0F);
		if (b <= 0.0F) {
			return 0.0F;
		}
		boolean sheltered = wearer != null ? WindPush.sheltered(wearer, wearer.getEyeHeight()) : WindPush.shelteredAt(level, BlockPos.containing(x, y, z));
		return sheltered ? b * SHELTERED : b;
	}

	/**
	 * What the wind does to a cape on {@code wearer}'s back: {streams back, to the side, flutters} in radians (the back
	 * and side as angles about the wearer's body, positive back and to their right); null when the air is still.
	 */
	public static float @Nullable [] cape(Entity wearer) {
		float blow = blow(wearer.level(), wearer.getX(), wearer.getY(), wearer.getZ(), wearer);
		if (blow <= 0.0F) {
			return null;
		}
		float yaw = (wearer instanceof net.minecraft.world.entity.LivingEntity living ? living.yBodyRot : wearer.getYRot()) * Mth.DEG_TO_RAD;
		// the wearer's forward is (-sin, cos); their right is (-cos, -sin)
		double back = -(windX() * -Mth.sin(yaw) + windZ() * Mth.cos(yaw));
		double right = windX() * -Mth.cos(yaw) + windZ() * -Mth.sin(yaw);
		float lean = (float)(back >= 0.0 ? back : back * CLOAK_FRONT) * CLOAK_LEAN * blow;
		return new float[] {lean, (float)right * CLOAK_SIDE * blow, CLOAK_FLAP * blow};
	}

	/** The wind's way along (x, z), a unit long. */
	public static double windX() {
		return Math.cos(Storms.clientWindAngle);
	}

	public static double windZ() {
		return Math.sin(Storms.clientWindAngle);
	}

	/**
	 * A banner's lean, as a whole number from -LEVELS to LEVELS, for a flag facing (fx, fz): positive swings the flag's
	 * foot back, away from its face, so wind from the front lifts it off the pole.
	 */
	public static int bannerLean(Level level, BlockPos pos, double fx, double fz) {
		float b = blow(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, null);
		if (b <= 0.0F) {
			return 0;
		}
		double toward = windX() * fx + windZ() * fz;
		return Math.round((float)(-toward * b * LEVELS));
	}

	/** The lean a flag's phase carries, or 0 for a plain phase. */
	public static float decodeBanner(float phase) {
		return phase >= CODE_BASE * 0.4F ? (Mth.floor(phase) - CODE_BASE) / LEVELS * BANNER_LEAN : 0.0F;
	}

	/** A phase with {@code lean} (whole steps) carried in its integer part. */
	public static float encodeBanner(float phase, int lean) {
		return lean == 0 ? phase : phase + CODE_BASE + lean;
	}
}
