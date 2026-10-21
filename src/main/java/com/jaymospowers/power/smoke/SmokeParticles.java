package com.jaymospowers.power.smoke;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The particles a Smoke user's smoke throws off. Plain and ascended smoke throw off {@link #STEAM}: our own particle
 * drawn with vanilla's geyser poof sprites (the thick puffs over potent sulfur on lava), but moving like smoke, where
 * it's thrown, and grey (the client's {@code SmokeSteam}). A corrupted (not ascended) user's smoke keeps the campfire
 * smoke it had. The gas itself ({@link SmokeField}) is drawn the same for everyone.
 */
public final class SmokeParticles {
	/** Puffs in a gout (Vapour Form's legs going up), and how fast they're thrown up. */
	static final int GOUT = 12;
	static final double GOUT_SPEED = 0.35;

	public static final SimpleParticleType STEAM = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("smoke_steam"),
		FabricParticleTypes.simple(false));

	private SmokeParticles() {
	}

	/** Registers {@link #STEAM} (by loading this class). */
	static void init() {
	}

	/** Corrupted smoke (smog) keeps its old particles. */
	static boolean old(Player owner) {
		return owner != null && Fate.isCorrupted(owner) && !Fate.isAscended(owner);
	}

	/** A puff of {@code owner}'s smoke: steam, or {@code old} for a corrupted user. */
	public static ParticleOptions puff(Player owner, ParticleOptions old) {
		return old(owner) ? old : STEAM;
	}

	/** A gout of {@code owner}'s smoke thrown up from {@code at} (nothing extra for a corrupted user). */
	public static void gout(ServerLevel level, Player owner, Vec3 at) {
		if (old(owner)) {
			return;
		}
		for (int i = 0; i < GOUT; i++) {
			double speed = GOUT_SPEED * (0.6 + 0.4 * level.getRandom().nextDouble());
			// a count of 0 sends the offsets as the puff's direction, times the speed
			level.sendParticles(STEAM, at.x + level.getRandom().nextGaussian() * 0.25, at.y, at.z + level.getRandom().nextGaussian() * 0.25, 0,
				level.getRandom().nextGaussian() * 0.15, 1.0, level.getRandom().nextGaussian() * 0.15, speed);
		}
	}
}
