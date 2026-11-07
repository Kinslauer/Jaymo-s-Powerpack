package com.jaymospowers.client.water;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/**
 * (Motion patch 2) A wake behind anything moving on the water, after Actions &amp; Stuff's boat wakes: two lines of foam
 * spreading out in a V from the stern, and spray at the bow, more and wider the faster and bigger it goes. Boats and
 * rafts of every mod (canoes included), and anything whose id names a ship or a whale (Jaymo's Kingdoms' ships and
 * great whales, which leave the biggest wakes). Drawn on each client alone; nothing to sync.
 */
public final class BoatWakes {
	// ===== the knobs ================================================================================================
	/** Only within this far of the camera (blocks), only faster than this (blocks a tick). */
	static final double RANGE = 64.0, MIN_SPEED = 0.08;
	/** Foam bits per tick per block of width at full speed ({@link #FULL_SPEED}), at most {@link #MAX_PER_TICK}. */
	static final float FOAM_PER_WIDTH = 2.0F, FULL_SPEED = 0.6F;
	static final int MAX_PER_TICK = 24;
	/** How fast the V's arms spread (blocks a tick, times speed). */
	static final double SPREAD = 0.12;
	// ================================================================================================================

	private BoatWakes() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BoatWakes::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused() || minecraft.player == null) {
			return;
		}
		RandomSource random = level.getRandom();
		double range = RANGE * RANGE;
		for (Entity e : level.entitiesForRendering()) {
			if (!wakes(e) || e.distanceToSqr(minecraft.player) > range) {
				continue;
			}
			double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
			double speed = Math.sqrt(dx * dx + dz * dz);
			if (speed < MIN_SPEED) {
				continue;
			}
			double surface = surface(level, e);
			if (Double.isNaN(surface)) {
				continue;
			}
			double fx = dx / speed, fz = dz / speed, sx = -fz, sz = fx;
			float width = e.getBbWidth();
			float strength = (float)Math.min(1.0, speed / FULL_SPEED);
			int count = Math.min(MAX_PER_TICK, Mth.ceil(FOAM_PER_WIDTH * width * strength));
			double half = width * 0.5;
			for (int i = 0; i < count; i++) {
				double side = (i & 1) == 0 ? 1.0 : -1.0;
				double back = half + random.nextDouble() * 0.3;
				double x = e.getX() - fx * back + sx * side * half * 0.7, z = e.getZ() - fz * back + sz * side * half * 0.7;
				double out = SPREAD * strength;
				level.addParticle(ParticleTypes.BUBBLE_POP, x, surface + 0.02, z, sx * side * out - fx * 0.02, 0.0, sz * side * out - fz * 0.02);
				if (random.nextFloat() < 0.5F) {
					level.addParticle(ParticleTypes.SPLASH, x, surface + 0.05, z, sx * side * out, 0.05, sz * side * out);
				}
			}
			// spray thrown off the bow
			int bow = Math.max(1, count / 3);
			for (int i = 0; i < bow; i++) {
				double side = random.nextBoolean() ? 1.0 : -1.0;
				level.addParticle(ParticleTypes.SPLASH, e.getX() + fx * half + sx * side * half * 0.4, surface + 0.1,
					e.getZ() + fz * half + sz * side * half * 0.4, sx * side * 0.1, 0.1 + strength * 0.1, sz * side * 0.1);
			}
		}
	}

	/** Boats, and anything whose id names a ship or a whale. */
	static boolean wakes(Entity e) {
		if (e instanceof AbstractBoat) {
			return true;
		}
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
		String path = id.getPath();
		return path.contains("ship") || path.contains("whale") || path.contains("canoe") || path.contains("raft");
	}

	/** The top of the water it floats on or swims at, or NaN if it isn't at the surface of water. */
	private static double surface(ClientLevel level, Entity e) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(e.getX(), e.getY() + 0.2, e.getZ()).mutable();
		if (!level.getFluidState(pos).is(FluidTags.WATER)) {
			pos.move(0, -1, 0);
			if (!level.getFluidState(pos).is(FluidTags.WATER)) {
				return Double.NaN;
			}
		}
		// up to the top of the water, no higher than its own top (a whale deep under leaves no wake)
		int top = Mth.floor(e.getY() + e.getBbHeight());
		while (level.getFluidState(pos.above()).is(FluidTags.WATER)) {
			if (pos.getY() >= top) {
				return Double.NaN;
			}
			pos.move(0, 1, 0);
		}
		return pos.getY() + level.getFluidState(pos).getHeight(level, pos);
	}
}
