package com.jaymospowers.core.wounds;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.32) Andy (2026-11-01): "the mechs should spark and malfunction while downed instead of being neck snappable or
 * crawling". A downed machine (Kitchen's {@code machine_corpses} tag: mechs, androids, golems, sentries, drones; see
 * {@link #is}) never crawls and never groans: it lies where it fell and shorts out. Sparks jump off it in bursts, it
 * smokes, and every so often a fault runs through it with a crackle and a clank (on the client its limbs lock and jerk:
 * {@code DownedPose}). No neck snap either ({@code Finishers}): there's no neck to break, only a weapon finishes it.
 */
public final class Malfunction {
	/** A burst of sparks: about every this many ticks (half to twice that), and how many. */
	static final int SPARK_EVERY = 14, SPARKS = 6;
	/** A fault (crackle, clank, a jerk of the limbs, a puff of smoke): about every this many ticks (half to twice that). */
	static final int FAULT_EVERY = 45;
	/** Smoke rising off it: one wisp every this many ticks. */
	static final int SMOKE_EVERY = 6;
	/** The sounds' volume. */
	static final float VOLUME = 0.6F;

	private static final class State {
		long nextSpark, nextFault;
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();

	private Malfunction() {
	}

	/** Whether this is a machine (it shorts out when downed, and can't have its neck snapped). */
	public static boolean is(@Nullable Entity entity) {
		return entity != null && com.jaymospowers.power.web.KitchenCompat.machine(entity.getType());
	}

	static void forget(Entity entity) {
		STATES.remove(entity);
	}

	/** Each tick a downed machine lies there, free: sparks, smoke, and now and then a fault. Shut down (passed out): just smoke. */
	static void tick(ServerLevel level, Mob mob) {
		State s = STATES.computeIfAbsent(mob, m -> new State());
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		double w = mob.getBbWidth() * 0.5, h = Math.max(0.3, mob.getBbHeight() * 0.25);
		double x = mob.getX(), y = mob.getY() + 0.25, z = mob.getZ();
		if (now % SMOKE_EVERY == 0) {
			level.sendParticles(ParticleTypes.SMOKE, x, y + 0.2, z, 1, w * 0.6, 0.05, w * 0.6, 0.01);
		}
		if (Downed.passedOut(mob)) {
			return;
		}
		if (s.nextSpark == 0L) {
			s.nextSpark = now + 5 + random.nextInt(10);
			s.nextFault = now + 20 + random.nextInt(30);
		}
		if (now >= s.nextSpark) {
			s.nextSpark = now + SPARK_EVERY / 2 + random.nextInt(SPARK_EVERY * 3 / 2);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, SPARKS + random.nextInt(SPARKS), w, h, w, 0.4);
			if (random.nextInt(3) == 0) {
				level.playSound(null, x, y, z, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.HOSTILE, VOLUME * 0.6F, 1.6F + random.nextFloat() * 0.4F);
			}
		}
		if (now >= s.nextFault) {
			s.nextFault = now + FAULT_EVERY / 2 + random.nextInt(FAULT_EVERY * 3 / 2);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + h, z, 20, w, h, w, 0.8);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, 3, w * 0.5, 0.1, w * 0.5, 0.02);
			level.sendParticles(ParticleTypes.CRIT, x, y + h, z, 6, w, h, w, 0.3);
			level.playSound(null, x, y, z, SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.HOSTILE, VOLUME, 0.5F + random.nextFloat() * 0.3F);
			level.playSound(null, x, y, z, random.nextBoolean() ? SoundEvents.IRON_GOLEM_DAMAGE : SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE,
				VOLUME, 0.6F + random.nextFloat() * 0.5F);
			// the fault jerks its head round
			mob.setYHeadRot(mob.getYHeadRot() + (random.nextFloat() - 0.5F) * 70.0F);
		}
	}
}
