package com.jaymospowers.power.chrono;

import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The golden streak a rewind leaves: the recorded path played back fast, from where you were to where you went,
 * with a rising, swelling chime (a "reversed" sound: it builds up instead of fading out).
 */
final class RewindTrail {
	static final int PLAYBACK_TICKS = 6;
	private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(0xF2C14E, 1.3F);

	private final ServerLevel level;
	private final List<Vec3> path;
	private int tick;

	RewindTrail(ServerLevel level, List<Vec3> path) {
		this.level = level;
		this.path = path;
	}

	/** One tick of playback. Returns false when done. */
	boolean tick() {
		if (tick >= PLAYBACK_TICKS || path.isEmpty()) {
			return false;
		}
		int n = path.size();
		int from = n * tick / PLAYBACK_TICKS;
		int to = n * (tick + 1) / PLAYBACK_TICKS;
		for (int i = from; i < to; i++) {
			Vec3 p = path.get(i);
			level.sendParticles(GOLD_DUST, p.x, p.y + 1.0, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			if (i % 2 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 0.9, p.z, 1, 0.05, 0.25, 0.05, 0.0);
			}
			if (i % 5 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 1.2, p.z, 1, 0.15, 0.3, 0.15, 0.0);
			}
		}
		Vec3 at = path.get(Math.min(n - 1, Math.max(0, to - 1)));
		float progress = (tick + 1) / (float) PLAYBACK_TICKS;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
			0.3F + 0.7F * progress, 0.7F + 1.2F * progress);
		tick++;
		if (tick >= PLAYBACK_TICKS) {
			Vec3 end = path.get(n - 1);
			level.playSound(null, end.x, end.y, end.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 2.0F);
			level.sendParticles(ParticleTypes.WAX_ON, end.x, end.y + 1.0, end.z, 12, 0.4, 0.7, 0.4, 0.4);
			return false;
		}
		return true;
	}
}
