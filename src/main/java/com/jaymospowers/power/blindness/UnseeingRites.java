package com.jaymospowers.power.blindness;

import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Unseeing's ritual-keepers fight with decay, blood and death magic (Andy's Awakening call, 2026-11-17). The ones who have
 * taken the dark on themselves (blindfolded, gouged or zealous; never a follower who still has their eyes) keep the rites, and
 * the rites are what they fight with:
 * <ul>
 * <li><b>decay</b>: each of their blows leaves the Ash's rot in what it strikes (Jaymo's Kingdoms' {@code AshRot}; without
 * Kingdoms, a short withering);</li>
 * <li><b>blood</b>: now and then they draw blood out of the one they hunt, across the air, and it closes their own wounds;</li>
 * <li><b>death</b>: now and then they lay death's touch on it, a withering that lingers.</li>
 * </ul>
 */
public final class UnseeingRites {
	// ---- tuning ----
	/** Rot a keeper's blow lays, and the withering instead without Kingdoms (ticks, level). */
	static final int BLOW_ROT = 1, WITHER_TICKS = 60, WITHER_LEVEL = 0;
	/** How often (ticks) the keepers are looked over, and how far their rites reach (blocks). */
	static final int EVERY = 20;
	static final double REACH = 10.0;
	/** The blood draw: harm drawn, share healed back, its wait (ticks). */
	static final float DRAW = 3.0F, DRAW_HEALS = 1.0F;
	static final int DRAW_WAIT = 120;
	/** Death's touch: the withering it lays (ticks, level), its wait (ticks). */
	static final int TOUCH_TICKS = 80, TOUCH_LEVEL = 1, TOUCH_WAIT = 200;
	static final int BLOOD = 0x8A0F14, DEATH = 0x2B2238;

	/** When each keeper may next draw blood and touch with death (game time). */
	private static final Map<Unseeing, long[]> NEXT = new WeakHashMap<>();

	private UnseeingRites() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getEntity() instanceof Unseeing keeper && source.isDirect() && keeper(keeper)
				&& !KingdomsCompat.rot(entity, keeper, BLOW_ROT)) {
				entity.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, WITHER_LEVEL));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(UnseeingRites::tick);
	}

	/** One who keeps the rites: the dark taken on themselves. */
	static boolean keeper(Unseeing u) {
		return u.look() != Unseeing.Look.FOLLOWER;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		Set<Unseeing> seen = new HashSet<>();
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			ServerLevel level = sp.level();
			for (Unseeing u : level.getEntitiesOfClass(Unseeing.class, sp.getBoundingBox().inflate(48.0), u -> u.isAlive() && keeper(u))) {
				if (!seen.add(u)) {
					continue;
				}
				LivingEntity target = u.getTarget();
				if (target == null || !target.isAlive() || target.distanceToSqr(u) > REACH * REACH || !u.hasLineOfSight(target)) {
					continue;
				}
				long now = level.getGameTime();
				long[] next = NEXT.computeIfAbsent(u, k -> new long[] {now + DRAW_WAIT / 2, now + TOUCH_WAIT / 2});
				Vec3 from = u.position().add(0.0, u.getBbHeight() * 0.6, 0.0);
				Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
				if (now >= next[0]) {
					next[0] = now + DRAW_WAIT + level.getRandom().nextInt(40);
					if (target.hurtServer(level, level.damageSources().indirectMagic(u, u), DRAW)) {
						u.heal(DRAW * DRAW_HEALS);
					}
					MagicFx.beam(level, to, from, 0.08F, BLOOD, 10);
					level.sendParticles(MagicFx.mote(BLOOD, 1.0F), to.x, to.y, to.z, 8, 0.2, 0.3, 0.2, 0.02);
					level.playSound(null, u.blockPosition(), SoundEvents.HONEY_DRINK.value(), SoundSource.HOSTILE, 0.8F, 0.5F);
				} else if (now >= next[1]) {
					next[1] = now + TOUCH_WAIT + level.getRandom().nextInt(60);
					target.addEffect(new MobEffectInstance(MobEffects.WITHER, TOUCH_TICKS, TOUCH_LEVEL), u);
					level.sendParticles(MagicFx.miasma(DEATH, 1.6F), to.x, to.y, to.z, 16, 0.3, 0.4, 0.3, 0.01);
					MagicFx.orb(level, to, 0.6F, DEATH, 12);
					level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
				}
			}
		}
	}
}
