package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Downed;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.45, Andy's NOEA picks) Poise and K.O.: every living thing in a Z Fighter's fight has a hidden poise bar. Melee
 * blows (and half as much from his ki) wear it, heavy ones more; a parry wears the attacker's. Broken, it is staggered
 * for a second (slowed, can't strike), then with its poise half back; broken again in that second, it is knocked out for
 * two (lying still through the wounds' knock-out where it can go down, else held motionless), never killed by it. After
 * {@link #REFILL_TICKS} without a hit the bar is full again. Shown only by a short cue on the target.
 */
public final class ZFighterPoise {
	/** Poise is this plus this much per point of max health. */
	static final float BASE = 10.0F;
	static final float PER_HEALTH = 0.5F;
	/** Poise worn per point of damage taken: melee, and a Z Fighter's ki; a blow of this much or more counts half again. */
	static final float MELEE_SHARE = 1.0F;
	static final float KI_SHARE = 0.5F;
	static final float HEAVY_DAMAGE = 5.0F;
	static final float HEAVY_FACTOR = 1.5F;
	/** What a parry takes off the attacker. */
	public static final float PARRY_WEAR = 8.0F;
	/** Ticks without a hit before the bar is full again. */
	static final int REFILL_TICKS = 60;
	static final int STAGGER_TICKS = 20;
	static final int KO_TICKS = 40;
	/** Share of the bar left after a stagger (a second break inside it is the K.O.). */
	static final float STAGGER_LEFT = 0.5F;
	/** After a K.O. no poise is worn this long. */
	static final int IMMUNE_TICKS = 100;
	static final int STAGGER_SLOW = 3;
	static final int HOLD_SLOW = 9;

	private static final class Poise {
		float left;
		long lastHit;
		long staggerUntil;
		long koUntil;
		long immuneUntil;
	}

	private static final Map<LivingEntity, Poise> BARS = new IdentityHashMap<>();

	private ZFighterPoise() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken <= 0.0F || blocked) {
				return;
			}
			Entity attacker = source.getEntity();
			if (!(attacker instanceof LivingEntity) || source.getDirectEntity() != attacker || attacker == entity) {
				return;
			}
			boolean involved = attacker instanceof Player a && Powers.has(a, ZFighterPower.INSTANCE)
				|| entity instanceof Player v && Powers.has(v, ZFighterPower.INSTANCE);
			if (!involved) {
				return;
			}
			float wear = damageTaken * (source.is(ZFighterKi.KI) ? KI_SHARE : MELEE_SHARE) * (damageTaken >= HEAVY_DAMAGE ? HEAVY_FACTOR : 1.0F);
			wear(entity, wear);
		});
	}

	static float max(LivingEntity entity) {
		return BASE + entity.getMaxHealth() * PER_HEALTH;
	}

	/** Wears {@code amount} off {@code target}'s poise: a stagger when it breaks, a K.O. when it breaks inside one. */
	public static void wear(LivingEntity target, float amount) {
		if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || target.isSpectator() || Downed.isDowned(target)
			|| target instanceof Player player && player.isCreative()) {
			return;
		}
		long now = level.getGameTime();
		Poise bar = BARS.computeIfAbsent(target, t -> {
			Poise fresh = new Poise();
			fresh.left = max(t);
			return fresh;
		});
		if (now < bar.immuneUntil || now < bar.koUntil) {
			return;
		}
		bar.lastHit = now;
		bar.left -= amount;
		if (bar.left > 0.0F) {
			return;
		}
		if (now < bar.staggerUntil) {
			knockOut(level, target, bar, now);
		} else {
			stagger(level, target, bar, now);
		}
	}

	/** Whether it is staggered or knocked out just now (it can't strike). */
	public static boolean stunned(LivingEntity entity) {
		Poise bar = BARS.get(entity);
		if (bar == null) {
			return false;
		}
		long now = entity.level().getGameTime();
		return now < bar.staggerUntil || now < bar.koUntil;
	}

	/** (0.17.58) Whether it is knocked out just now (not merely staggered): a K.O. in a tournament. */
	public static boolean knockedOut(LivingEntity entity) {
		Poise bar = BARS.get(entity);
		return bar != null && entity.level().getGameTime() < bar.koUntil;
	}

	private static void stagger(ServerLevel level, LivingEntity target, Poise bar, long now) {
		bar.staggerUntil = now + STAGGER_TICKS;
		bar.left = max(target) * STAGGER_LEFT;
		hold(target, STAGGER_TICKS, STAGGER_SLOW);
		Vec3 head = target.position().add(0.0, target.getBbHeight() + 0.2, 0.0);
		level.sendParticles(ParticleTypes.CRIT, true, false, head.x, head.y, head.z, 12, 0.3, 0.1, 0.3, 0.3);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, true, false, head.x, head.y, head.z, 8, 0.3, 0.1, 0.3, 0.1);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 0.8F, 1.5F);
	}

	private static void knockOut(ServerLevel level, LivingEntity target, Poise bar, long now) {
		bar.koUntil = now + KO_TICKS;
		bar.immuneUntil = now + KO_TICKS + IMMUNE_TICKS;
		bar.left = max(target);
		// where it can go down, the wounds' own knock-out lays it out; otherwise it is simply held still
		if (!Downed.knockOut(level, target, KO_TICKS)) {
			hold(target, KO_TICKS, HOLD_SLOW);
		}
		Vec3 head = target.position().add(0.0, target.getBbHeight() + 0.2, 0.0);
		level.sendParticles(ParticleTypes.POOF, true, false, target.getX(), target.getY() + 0.3, target.getZ(), 10, 0.4, 0.1, 0.4, 0.05);
		level.sendParticles(ParticleTypes.CRIT, true, false, head.x, head.y, head.z, 20, 0.35, 0.15, 0.35, 0.4);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 0.9F, 0.8F);
	}

	/** Slowed hard and weakened to nothing for {@code ticks}, and a mob loses its quarry. */
	private static void hold(LivingEntity target, int ticks, int slow) {
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, slow, false, false));
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, false));
		if (target instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
	}

	/** Every tick: the bars refill after a quiet moment; a stunned mob stays off its quarry. */
	static void tick() {
		Iterator<Map.Entry<LivingEntity, Poise>> it = BARS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, Poise> entry = it.next();
			LivingEntity entity = entry.getKey();
			Poise bar = entry.getValue();
			if (entity.isRemoved() || !entity.isAlive()) {
				it.remove();
				continue;
			}
			long now = entity.level().getGameTime();
			if (now < bar.staggerUntil || now < bar.koUntil) {
				if (entity instanceof Mob mob) {
					mob.setTarget(null);
					mob.getNavigation().stop();
				}
				continue;
			}
			if (now - bar.lastHit >= REFILL_TICKS && now >= bar.immuneUntil) {
				// a full bar is just no entry
				it.remove();
			}
		}
	}

	static void drop(ServerPlayer player) {
		BARS.remove(player);
	}

	static void clear() {
		BARS.clear();
	}
}
