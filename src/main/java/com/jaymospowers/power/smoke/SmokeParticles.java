package com.jaymospowers.power.smoke;

import com.jaymospowers.core.Fate;
import net.minecraft.core.particles.GeyserBaseParticleOptions;
import net.minecraft.core.particles.GeyserParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The particles a Smoke user's smoke throws off. Plain and ascended smoke use vanilla's geyser steam (the thick plume
 * over potent sulfur on lava): {@code geyser_poof} for trails ({@link #puff}) and bursts ({@link #burst}),
 * {@code geyser_plume} for a gout going up (Vapour Form). A poof flies off at a random angle at its burst impulse plus
 * the velocity it's given, always upward (it never sinks), and slows fast; a plume ignores velocity and climbs straight
 * up 5 blocks a water block. A corrupted (not ascended) user's smoke keeps the campfire smoke it had. The gas itself
 * ({@link SmokeField}) is drawn the same for everyone.
 */
public final class SmokeParticles {
	/** The water blocks the geyser particles are sized for (vanilla's 1-4: bigger, faster and taller with more). */
	static final int SIZE = 1;
	/** A trail poof's own burst impulse: none, so it keeps close to where it's thrown (plus vanilla's 0.25 a water block). */
	static final float TRAIL = 0.0F;
	/** A burst poof's impulse (vanilla's eruption uses 2.0 for its poofs, 1.5 for its base). */
	static final float BURST = 1.5F;
	/** Plumes in a gout (Vapour Form's legs going up). */
	static final int PLUMES = 3;

	private static ParticleOptions trail;
	private static ParticleOptions burst;
	private static ParticleOptions plume;

	private SmokeParticles() {
	}

	/** Corrupted smoke (smog) keeps its old particles. */
	static boolean old(Player owner) {
		return owner != null && Fate.isCorrupted(owner) && !Fate.isAscended(owner);
	}

	/** A puff of a trail of {@code owner}'s smoke (jet, tail): geyser steam, or {@code old} for a corrupted user. */
	public static ParticleOptions puff(Player owner, ParticleOptions old) {
		if (old(owner)) {
			return old;
		}
		if (trail == null) {
			trail = poof(TRAIL);
		}
		return trail != null ? trail : old;
	}

	/** A burst of {@code owner}'s smoke (bomb, step, landing): geyser steam flung wide, or {@code old} for a corrupted user. */
	public static ParticleOptions burst(Player owner, ParticleOptions old) {
		if (old(owner)) {
			return old;
		}
		if (burst == null) {
			burst = poof(BURST);
		}
		return burst != null ? burst : old;
	}

	private static ParticleOptions poof(float impulse) {
		return type("geyser_poof") instanceof ParticleType<?> type ? new GeyserBaseParticleOptions(cast(type), SIZE, impulse) : null;
	}

	/** A gout of {@code owner}'s smoke going up from {@code at}: a few geyser plumes (nothing for a corrupted user). */
	public static void gout(ServerLevel level, Player owner, Vec3 at) {
		if (old(owner)) {
			return;
		}
		if (plume == null) {
			plume = type("geyser_plume") instanceof ParticleType<?> type ? new GeyserParticleOptions(cast(type), SIZE) : null;
		}
		if (plume != null) {
			level.sendParticles(plume, at.x, at.y, at.z, PLUMES, 0.3, 0.0, 0.3, 0.0);
		}
	}

	private static ParticleType<?> type(String id) {
		return BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.withDefaultNamespace(id));
	}

	@SuppressWarnings("unchecked")
	private static <T extends ParticleOptions> ParticleType<T> cast(ParticleType<?> type) {
		return (ParticleType<T>)type;
	}
}
