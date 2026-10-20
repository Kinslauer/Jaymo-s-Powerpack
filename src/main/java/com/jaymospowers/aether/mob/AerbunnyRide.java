package com.jaymospowers.aether.mob;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What an aerbunny on your head does for you, run every tick on the client that owns the player (movement is the
 * client's): your fall is capped at {@link #RIDER_FALL} (no fall damage for a drop that gentle: the server also clears
 * the fall, see {@link Aerbunny}), and once you have fallen more than {@link #HOP_AFTER} blocks, jump hops you up
 * again and puffs the bunny. Hops are limited to {@link #MAX_HOPS} per time in the air (refilled on the ground), and the fall gate spaces them out, so a bunny cannot turn into free flight.
 */
public final class AerbunnyRide {
	// ---- tuning ----
	/** The most the carrier falls a tick (blocks): well under half a block a tick, so a gentle landing. */
	public static final double RIDER_FALL = 0.25;
	/** The hop's upward speed (blocks a tick), the drop (blocks) needed before it, and how long the bunny stays puffed (ticks). */
	public static final double HOP_SPEED = 0.55;
	public static final double HOP_AFTER = 1.1;
	public static final int PUFF_TICKS = 11;
	/** Hops allowed before touching the ground again. */
	public static final int MAX_HOPS = 2;

	private static boolean jumpWasHeld;
	private static int hops;

	private AerbunnyRide() {
	}

	/** The bunny on {@code player}'s head, or null. */
	public static Aerbunny carried(Player player) {
		for (var passenger : player.getPassengers()) {
			if (passenger instanceof Aerbunny bunny) {
				return bunny;
			}
		}
		return null;
	}

	/** One tick for the local {@code player}; {@code jumpHeld} is the jump key. */
	public static void tick(Player player, boolean jumpHeld) {
		boolean pressed = jumpHeld && !jumpWasHeld;
		jumpWasHeld = jumpHeld;
		Aerbunny bunny = carried(player);
		if (bunny == null || player.onGround() || player.isInWater() || player.isFallFlying() || player.getAbilities().flying) {
			hops = 0;
			return;
		}
		Vec3 motion = player.getDeltaMovement();
		if (pressed && hops < MAX_HOPS && player.fallDistance > HOP_AFTER) {
			hops++;
			player.setDeltaMovement(motion.x, HOP_SPEED, motion.z);
			player.fallDistance = 0.0;
			bunny.setPuff(PUFF_TICKS);
		} else if (motion.y < -RIDER_FALL) {
			player.setDeltaMovement(motion.x, -RIDER_FALL, motion.z);
		}
	}
}
