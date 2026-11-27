package com.jaymoskingdoms.wild;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.phys.Vec3;

/**
 * Fish-eaters (Andy's nature picks, 2026-11-26; {@link Nature}).
 * <ul>
 * <li><b>Bears fish the run</b>: in autumn a bear near a river goes down to the salmon in it, stands in the water and
 * snaps one up when it's close ({@link #BEAR_REACH}). Any other time a bear that's in the water takes a fish that comes
 * right up to it. Each fish feeds it ({@link FedPredators#feed}), so a bear at the falls is a calm one.</li>
 * <li><b>Birds dive for fish</b> (Andy's note: "Not just salmon either"): eagles, gulls and ducks drop onto any fish
 * near the surface ({@link #SURFACE}) within {@link #DIVE_RANGE} and take it.</li>
 * <li><b>Crocodiles work the shoals</b>: a crocodile or alligator in the water with nothing in its sights swims to where
 * fish crowd ({@link #SHOAL} or more), and takes them as it does ({@code Crocodiles#takePrey}). Salmon need cold rivers
 * and crocodiles warm ones, so this is in the warm rivers and swamps, with whatever fish live there.</li>
 * </ul>
 */
final class FishEaters {
	static final int STEP_EVERY = 20;
	static final double BEAR_LOOK = 14.0, BEAR_REACH = 2.4, BEAR_PACE = 1.0, BEAR_NEAR_WATER = 3.0;
	static final double DIVE_RANGE = 16.0, DIVE_REACH = 1.8, DIVE_SPEED = 0.6;
	static final int SURFACE = 2;
	static final double SHOAL_LOOK = 24.0, SHOAL_PACE = 0.9;
	static final int SHOAL = 3;

	private FishEaters() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FishEaters::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % STEP_EVERY != 0) {
			return;
		}
		for (Mob bear : Nature.of(Nature.BEARS)) {
			if (Nature.free(bear) && bear.level() instanceof ServerLevel level) {
				bear(level, bear);
			}
		}
		if (server.getTickCount() % (STEP_EVERY * 2) == 0) {
			for (Mob bird : Nature.of(Nature.FISH_DIVERS)) {
				if (Nature.free(bird) && bird.level() instanceof ServerLevel level) {
					dive(level, bird);
				}
			}
		}
	}

	private static void bear(ServerLevel level, Mob bear) {
		boolean run = Nature.season(level, "autumn") && level.getBiome(bear.blockPosition()).is(BiomeTags.IS_RIVER);
		double look = run ? BEAR_LOOK : BEAR_REACH;
		if (!run && !bear.isInWater()) {
			return;
		}
		AbstractFish fish = nearestFish(level, bear, look, run);
		if (fish == null) {
			return;
		}
		if (bear.distanceTo(fish) > BEAR_REACH) {
			Nature.go(bear, fish.position(), BEAR_PACE);
			return;
		}
		eat(level, bear, fish);
	}

	private static void dive(ServerLevel level, Mob bird) {
		AbstractFish fish = null;
		double best = DIVE_RANGE * DIVE_RANGE;
		for (AbstractFish f : level.getEntitiesOfClass(AbstractFish.class, bird.getBoundingBox().inflate(DIVE_RANGE, DIVE_RANGE + 4, DIVE_RANGE),
			f -> f.isAlive() && f.isInWater() && nearSurface(level, f))) {
			double d = f.distanceToSqr(bird);
			if (d < best) {
				best = d;
				fish = f;
			}
		}
		if (fish == null) {
			return;
		}
		if (bird.distanceTo(fish) > DIVE_REACH) {
			Vec3 to = fish.position().subtract(bird.position()).normalize().scale(DIVE_SPEED);
			bird.setDeltaMovement(to);
			bird.needsSync = true;
			Nature.go(bird, fish.position(), 1.4);
			return;
		}
		eat(level, bird, fish);
	}

	/** Crocodiles and alligators in the water go where the fish crowd. Called from {@link Crocodiles}' own round. */
	static void shoals(ServerLevel level, Mob croc) {
		if (!croc.isInWater() || croc.getTarget() != null || croc.isBaby()) {
			return;
		}
		List<AbstractFish> fish = level.getEntitiesOfClass(AbstractFish.class, croc.getBoundingBox().inflate(SHOAL_LOOK), f -> f.isAlive() && f.isInWater());
		if (fish.size() < SHOAL) {
			return;
		}
		Vec3 mid = Vec3.ZERO;
		for (AbstractFish f : fish) {
			mid = mid.add(f.position());
		}
		mid = mid.scale(1.0 / fish.size());
		if (mid.distanceToSqr(croc.position()) > 9.0) {
			Nature.go(croc, mid, SHOAL_PACE);
		}
	}

	private static boolean nearSurface(ServerLevel level, AbstractFish f) {
		for (int i = 1; i <= SURFACE + 1; i++) {
			if (level.getBlockState(f.blockPosition().above(i)).isAir()) {
				return true;
			}
		}
		return false;
	}

	private static AbstractFish nearestFish(ServerLevel level, Mob by, double look, boolean salmonOnly) {
		AbstractFish best = null;
		double bestD = look * look;
		for (AbstractFish f : level.getEntitiesOfClass(AbstractFish.class, by.getBoundingBox().inflate(look),
			f -> f.isAlive() && f.isInWater() && (!salmonOnly || f instanceof Salmon))) {
			double d = f.distanceToSqr(by);
			if (d < bestD) {
				bestD = d;
				best = f;
			}
		}
		return best;
	}

	private static void eat(ServerLevel level, Mob by, AbstractFish fish) {
		by.getLookControl().setLookAt(fish, 30.0F, 30.0F);
		level.sendParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY() + 0.4, fish.getZ(), 16, 0.3, 0.1, 0.3, 0.1);
		level.playSound(null, by.getX(), by.getY(), by.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.7F, 0.9F);
		fish.discard();
		FedPredators.feed(by);
	}
}
