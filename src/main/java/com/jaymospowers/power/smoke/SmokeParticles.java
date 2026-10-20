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
 * over potent sulfur on lava): {@code geyser_poof} for puffs, bursts and trails, {@code geyser_plume} for a gout going
 * up (Vapour Form). A corrupted (not ascended) user's smoke keeps the campfire smoke it had. The gas itself
 * ({@link SmokeField}) is drawn the same for everyone.
 */
public final class SmokeParticles {
	/** The water blocks the geyser particles are sized for (vanilla's 1-4: bigger, faster and taller with more). */
	static final int SIZE = 1;
	/** The poof's burst impulse (vanilla scales it by the water blocks). */
	static final float BURST = 0.5F;
	/** Plumes in a gout (Vapour Form's legs going up). */
	static final int PLUMES = 3;

	private static ParticleOptions poof;
	private static ParticleOptions plume;

	private SmokeParticles() {
	}

	/** Corrupted smoke (smog) keeps its old particles. */
	static boolean old(Player owner) {
		return owner != null && Fate.isCorrupted(owner) && !Fate.isAscended(owner);
	}

	/** A puff, burst or trail of {@code owner}'s smoke: geyser steam, or {@code old} for a corrupted user. */
	public static ParticleOptions puff(Player owner, ParticleOptions old) {
		if (old(owner)) {
			return old;
		}
		if (poof == null) {
			poof = type("geyser_poof") instanceof ParticleType<?> type
				? new GeyserBaseParticleOptions(cast(type), SIZE, BURST) : null;
		}
		return poof != null ? poof : old;
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
