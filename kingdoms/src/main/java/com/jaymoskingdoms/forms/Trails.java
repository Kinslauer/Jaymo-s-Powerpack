package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.forms.Forms.Family;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.372) School Swing Trails: each family's forms leave their own vanilla-style particle trail along a swing, a lunge or a
 * throw. Blades leave dark ink streaks, spears and lances leave sparks, fists leave dust. The fist forms (a later patch)
 * call {@link #fist}; {@link #of} picks by {@link Family}. Particles only: no entity, nothing to clean up.
 */
final class Trails {
	/** Blocks between one puff and the next along a trail, and the most puffs one trail makes (so a long lunge doesn't flood the network). */
	static final double SPACING = 0.35;
	static final int MAX_PUFFS = 24;
	/** The fist's dust: a dry tan, and how big the grains are. */
	static final int DUST_COLOR = 0xB59E7A;
	static final float DUST_SIZE = 0.9F;

	private Trails() {
	}

	/** A dark ink streak from {@code from} to {@code to}: a blade's swing. */
	static void blade(ServerLevel level, Vec3 from, Vec3 to) {
		along(level, from, to, (x, y, z) -> {
			level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 1, 0.03, 0.03, 0.03, 0.01);
			level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
		});
	}

	/** Sparks along the line: a spear's thrust or a lance's charge. */
	static void spear(ServerLevel level, Vec3 from, Vec3 to) {
		along(level, from, to, (x, y, z) -> {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.08, 0.08, 0.08, 0.15);
			level.sendParticles(ParticleTypes.CRIT, x, y, z, 1, 0.05, 0.05, 0.05, 0.05);
		});
	}

	/** A trail of dust: a fist's swing. */
	static void fist(ServerLevel level, Vec3 from, Vec3 to) {
		DustParticleOptions dust = new DustParticleOptions(DUST_COLOR, DUST_SIZE);
		along(level, from, to, (x, y, z) -> {
			level.sendParticles(dust, x, y, z, 2, 0.1, 0.1, 0.1, 0.0);
			level.sendParticles(ParticleTypes.POOF, x, y, z, 1, 0.05, 0.05, 0.05, 0.01);
		});
	}

	/** {@code family}'s own trail. (The knife and claw families borrow the blade's ink for now.) */
	static void of(ServerLevel level, Family family, Vec3 from, Vec3 to) {
		switch (family) {
			case SPEAR -> spear(level, from, to);
			case FIST -> fist(level, from, to);
			default -> blade(level, from, to);
		}
	}

	private interface Puff {
		void at(double x, double y, double z);
	}

	private static void along(ServerLevel level, Vec3 from, Vec3 to, Puff puff) {
		double length = from.distanceTo(to);
		if (length < 1.0E-3) {
			puff.at(from.x, from.y, from.z);
			return;
		}
		int n = Math.min(MAX_PUFFS, Math.max(1, (int)(length / SPACING)));
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.lerp(to, i / (double)n);
			puff.at(p.x, p.y, p.z);
		}
	}
}
