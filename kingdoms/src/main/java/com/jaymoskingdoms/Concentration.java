package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * One held spell (fifth edition's concentration, Andy's pick H1). A caster, player or not, keeps only one lasting spell
 * up at a time: a ward, a war chant, a circle of sanctuary, the dead they raised. Casting another ends the first, and a
 * hard enough blow may break it unless their Grit holds ({@link Saves.Kind#FOCUS}). Hit the shaman and his war chant
 * drops; hit the court wizard and his guards' ward fades.
 */
public final class Concentration {
	/** A blow of at least this much makes a caster save to keep their spell; one of {@link #SURE_BREAK} or more always breaks it. */
	static final float SHAKEN = 1.0F, SURE_BREAK = 10.0F;

	private record Held(String spell, long until, Runnable end) {
	}

	private static final Map<UUID, Held> HELD = new HashMap<>();

	private Concentration() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Concentration::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> HELD.clear());
	}

	/**
	 * {@code caster} now holds {@code spell} for {@code ticks}; {@code end} takes it down if it breaks early. Whatever they
	 * held before ends now.
	 */
	public static void hold(LivingEntity caster, String spell, int ticks, Runnable end) {
		Held was = HELD.remove(caster.getUUID());
		if (was != null) {
			was.end().run();
		}
		HELD.put(caster.getUUID(), new Held(spell, caster.level().getGameTime() + ticks, end));
	}

	/** The spell {@code caster} holds, or null. */
	public static String holding(LivingEntity caster) {
		Held h = HELD.get(caster.getUUID());
		return h == null ? null : h.spell();
	}

	/** Ends whatever {@code caster} holds, as if broken (a dispel, a silence). */
	public static boolean drop(LivingEntity caster) {
		Held h = HELD.remove(caster.getUUID());
		if (h == null) {
			return false;
		}
		h.end().run();
		fizzle(caster);
		return true;
	}

	/** {@code target} taking {@code amount}: a caster holding a spell may lose it. Returns the amount unchanged. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (amount < SHAKEN || !HELD.containsKey(target.getUUID()) || target.level().isClientSide()) {
			return amount;
		}
		if (amount >= SURE_BREAK || !Saves.resist(target, Saves.Kind.FOCUS)) {
			drop(target);
		}
		return amount;
	}

	private static void fizzle(LivingEntity caster) {
		if (caster.level() instanceof ServerLevel level) {
			level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.7F, 1.6F);
			level.sendParticles(ParticleTypes.SMOKE, caster.getX(), caster.getY(1.0), caster.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
		}
	}

	private static void tick(MinecraftServer server) {
		if (HELD.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Iterator<Held> it = HELD.values().iterator(); it.hasNext();) {
			if (it.next().until() <= now) {
				it.remove();
			}
		}
	}
}
