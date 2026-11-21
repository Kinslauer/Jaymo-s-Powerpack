package com.jaymospowers.power.cryo;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Frostbite (Andy's Awakening pick: "Cryokinesis, and freezing fog"). Every hit of Cryokinesis' ice (the shards, the breath,
 * Flash Freeze, the gaze, the volley, the ball, the Blizzard, the javelin and the rampart) and every breath of freezing fog
 * lays a stack of Frostbite on what it strikes. The bitten keep slowing and hurting after the move is over: each second a
 * little freezing harm for every stack, and a slowness that deepens with the stacks. The stacks hold for {@link #HOLD_TICKS}
 * after the last new one, then thaw one a second. Whatever can't freeze (a stray, a snow golem, someone in leather) is never
 * bitten.
 */
public final class CryoFrostbite {
	// ---- tuning ----
	/** The most stacks, and the least time between two new stacks from the same rush of cold (ticks). */
	static final int MAX = 10, GAP = 10;
	/** Harm each second for each stack, and the stacks a slowness level takes (up to the deepest). */
	static final float HARM_PER_STACK = 0.25F;
	static final int STACKS_PER_SLOW = 3, MAX_SLOW = 3;
	/** Ticks the stacks hold after the last new one, then ticks a stack takes to thaw. */
	static final int HOLD_TICKS = 100, THAW_TICKS = 20;

	private static final class Bite {
		int stacks;
		long last, lastAdded, thawed;
		@Nullable Entity by;
	}

	private static final Map<LivingEntity, Bite> BITES = new WeakHashMap<>();

	private CryoFrostbite() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CryoFrostbite::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> BITES.clear());
	}

	/** Lays {@code stacks} of Frostbite on {@code target} (from {@code by}, if anyone). */
	public static void bite(LivingEntity target, @Nullable Entity by, int stacks) {
		if (!target.isAlive() || !target.canFreeze() || target.level().isClientSide() || target instanceof Player p && p.isCreative()) {
			return;
		}
		long now = target.level().getGameTime();
		Bite b = BITES.computeIfAbsent(target, k -> new Bite());
		b.last = now;
		if (by != null) {
			b.by = by;
		}
		if (now - b.lastAdded < GAP && b.stacks > 0) {
			return;
		}
		b.lastAdded = now;
		b.thawed = now;
		b.stacks = Math.min(MAX, b.stacks + stacks);
	}

	/** {@code entity}'s Frostbite stacks now. */
	public static int stacks(LivingEntity entity) {
		Bite b = BITES.get(entity);
		return b == null ? 0 : b.stacks;
	}

	private static void tick(MinecraftServer server) {
		if (BITES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<LivingEntity, Bite>> it = BITES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<LivingEntity, Bite> e = it.next();
			LivingEntity target = e.getKey();
			Bite b = e.getValue();
			if (!target.isAlive() || target.isRemoved() || b.stacks <= 0) {
				it.remove();
				continue;
			}
			ServerLevel level = (ServerLevel)target.level();
			long now = level.getGameTime();
			if (now - b.last > HOLD_TICKS && now - b.thawed >= THAW_TICKS) {
				b.thawed = now;
				if (--b.stacks <= 0) {
					it.remove();
					continue;
				}
			}
			// a steady beat of its own (not timed from the last stack, which fresh hits keep renewing)
			if ((now + target.getId()) % 20 == 0) {
				int slow = Math.min(MAX_SLOW, b.stacks / STACKS_PER_SLOW);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, slow, false, true));
				target.hurtServer(level, level.damageSources().source(DamageTypes.FREEZE, null, b.by), HARM_PER_STACK * b.stacks);
				level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
					2 + b.stacks, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.01);
			}
		}
	}
}
