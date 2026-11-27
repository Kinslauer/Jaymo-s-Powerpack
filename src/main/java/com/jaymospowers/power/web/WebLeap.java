package com.jaymospowers.power.web;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The suit's charged leap, as numbers both sides share: how hard a charge throws you up, how high that carries you, and
 * how much charge a ledge needs. The leap itself is the client's (see {@code WebSwing#tickChargeJump}); a takeover
 * coils it too, for ledges taller than a step (see {@link WebTakeoverPath}, {@code WebSymbioteClient}).
 */
public final class WebLeap {
	/** Ticks to a full charge; the leap from a tap to a full charge (up speed, and forward along the look), blocks/tick. */
	public static final int CHARGE_FULL = 30;
	public static final double CHARGE_UP_MIN = 0.45, CHARGE_UP_MAX = 1.65;
	public static final double CHARGE_FORWARD_MAX = 1.1;
	/**
	 * (0.16.7) While Jump is held the client tells the server so again every this many ticks: a takeover's, escape's or
	 * bound's coil ending sends its own "let go", which would otherwise leave a held charge unbraced (and uncoiled) on the
	 * server for the rest of the hold.
	 */
	public static final int CHARGE_REMIND_TICKS = 20;
	/** A takeover never coils more than this share of a full charge. */
	public static final float TAKEOVER_MAX_CHARGE = 0.75F;
	/** It charges to clear the ledge by this much (blocks) at the top of the leap. */
	public static final double CLEARANCE = 0.6;
	/** Vanilla's fall: gravity (blocks/tick², the attribute's default) and air drag per tick. */
	public static final double GRAVITY = 0.08, DRAG = 0.98;
	/** Ticks the flight is simulated for (far past any apex). */
	private static final int SIMULATE_TICKS = 120;
	/** Steps of the search for the charge a height needs. */
	private static final int SEARCH_STEPS = 16;
	/**
	 * The tallest rise (blocks, a whole number) a takeover's leap may climb: what {@link #TAKEOVER_MAX_CHARGE} clears
	 * with {@link #CLEARANCE} to spare, at normal gravity.
	 */
	public static final int TAKEOVER_MAX_RISE = (int)Math.floor(apex(speed(TAKEOVER_MAX_CHARGE), GRAVITY) - CLEARANCE);

	private WebLeap() {
	}

	/** The upward speed a charge (0-1) leaps with. */
	public static double speed(float charge) {
		return Mth.lerp(Mth.clamp(charge, 0.0F, 1.0F), CHARGE_UP_MIN, CHARGE_UP_MAX);
	}

	/** How high (blocks) a leap at {@code up} blocks/tick rises, under {@code gravity}: the player's own fall, tick by tick. */
	public static double apex(double up, double gravity) {
		double y = 0.0, top = 0.0, v = up;
		for (int i = 0; i < SIMULATE_TICKS && v > 0.0; i++) {
			y += v;
			top = Math.max(top, y);
			v = (v - gravity) * DRAG;
		}
		return top;
	}

	/** A full charge can land on a foe only when the whole body clears the expected arc. */
	static @Nullable Vec3 aimedVelocity(ServerPlayer player, Vec3 land) {
		Vec3 start = player.position();
		Vec3 span = land.subtract(start);
		double gravity = player.getAttributeValue(Attributes.GRAVITY);
		for (float charge = 0.48F; charge <= 1.01F; charge += 0.04F) {
			double up = speed(charge);
			double y = 0.0, v = up, travel = 0.0, stride = 1.0;
			int ticks = 0;
			while (ticks < 56 && (v >= 0 || y > span.y)) {
				y += v;
				v = (v - gravity) * DRAG;
				travel += stride;
				stride *= 0.98;
				ticks++;
			}
			if (ticks >= 56 || travel <= 0 || span.horizontalDistance() / travel > 1.65 || y > span.y) {
				continue;
			}
			Vec3 across = span.horizontal().scale(1 / travel);
			AABB body = player.getBoundingBox();
			double xz = 0.0;
			y = 0.0;
			v = up;
			stride = 1.0;
			boolean clear = true;
			for (int i = 0; i < ticks; i++) {
				xz += stride;
				y += v;
				Vec3 offset = across.scale(xz).add(0, Math.max(i == ticks - 1 ? span.y : -Double.MAX_VALUE, y), 0);
				if (!player.level().hasChunkAt(BlockPos.containing(start.add(offset)))
					|| !player.level().noCollision(player, body.move(offset))) {
					clear = false;
					break;
				}
				v = (v - gravity) * DRAG;
				stride *= 0.98;
			}
			if (clear) {
				return across.add(0, up, 0);
			}
		}
		return null;
	}

	/**
	 * The least charge (0-{@code max}) that clears a rise of {@code rise} blocks with {@link #CLEARANCE} to spare; {@code max}
	 * if even that falls short.
	 */
	public static float chargeFor(double rise, double gravity, float max) {
		double need = rise + CLEARANCE;
		if (apex(speed(max), gravity) <= need) {
			return max;
		}
		float lo = 0.0F, hi = max;
		for (int i = 0; i < SEARCH_STEPS; i++) {
			float mid = (lo + hi) * 0.5F;
			if (apex(speed(mid), gravity) >= need) {
				hi = mid;
			} else {
				lo = mid;
			}
		}
		return hi;
	}
}
