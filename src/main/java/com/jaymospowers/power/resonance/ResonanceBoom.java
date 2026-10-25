package com.jaymospowers.power.resonance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * C. Choir (ascended): <b>Sonic Boom</b>, the Warden's own attack: a {@code CHARGE_TICKS} charge, then a beam
 * {@code RANGE} blocks long, through walls, armour and shields: {@code DAMAGE}, and whatever it hits is launched.
 * Shrieker (corrupted): <b>Shriek</b>: every creature within {@code SHRIEK_RADIUS} gets Darkness for
 * {@code SHRIEK_TICKS}, you hear their heartbeats meanwhile, and Wardens in range come for you.
 */
final class ResonanceBoom {
	static final int CHARGE_TICKS = 20;
	static final double RANGE = 20.0;
	/** How close to the beam's line a creature must be (plus half its width). */
	static final double BEAM_WIDTH = 0.75;
	static final float DAMAGE = 14.0F;
	/** The Warden's launch: 2.5 along, 0.5 up; ours lifts a little more. */
	static final double LAUNCH = 2.5;
	static final double LAUNCH_UP = 0.6;
	static final double SHRIEK_RADIUS = 16.0;
	static final int SHRIEK_TICKS = 160;
	/** (0.16.24) The films (ticks): side on along the Sonic Boom; round your front for the Shriek, bars for all who hear it. */
	static final int BOOM_FILM_TICKS = 24, SHRIEK_FILM_TICKS = 30;
	/** Anger a Shriek sets on a Warden in range (80 is angry; 150 the most). */
	static final int SHRIEK_ANGER = 150;

	/** Game time each charging player's boom goes off. */
	private static final Map<UUID, Long> CHARGING = new HashMap<>();

	private ResonanceBoom() {
	}

	static boolean charge(ServerPlayer player) {
		if (CHARGING.containsKey(player.getUUID())) {
			return false;
		}
		ServerLevel level = player.level();
		long fireAt = level.getGameTime() + CHARGE_TICKS;
		CHARGING.put(player.getUUID(), fireAt);
		ResonancePower.set(player, ResonancePower.state(player).withCharge(fireAt));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3.0F, 1.0F);
		return true;
	}

	static void tick(ServerPlayer player) {
		Long fireAt = CHARGING.get(player.getUUID());
		if (fireAt == null) {
			return;
		}
		ServerLevel level = player.level();
		if (!player.isAlive() || player.isSpectator()) {
			forget(player);
			return;
		}
		long now = level.getGameTime();
		if (now % 4 == 0) {
			Vec3 chest = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.6)).add(0.0, -0.3, 0.0);
			level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, chest.x, chest.y, chest.z, 3, 0.15, 0.15, 0.15, 0.02);
		}
		if (now < fireAt) {
			return;
		}
		forget(player);
		fire(player, level);
	}

	private static void fire(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(RANGE));
		// the Warden's ring of rings, every block of the way
		for (int i = 1; i <= (int)RANGE; i++) {
			Vec3 at = eye.add(look.scale(i));
			level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		com.jaymospowers.core.Shakes.heavy(player, eye.add(look.scale(2.0)), RANGE, 1.6F);
		com.jaymospowers.core.UltimateFilm.play(player, com.jaymospowers.core.UltimateFilm.PROFILE, BOOM_FILM_TICKS, end, null, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.0F);
		List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(BEAM_WIDTH + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand));
		for (LivingEntity target : hits) {
			Vec3 center = target.getBoundingBox().getCenter();
			double along = center.subtract(eye).dot(look);
			if (along < 0.0 || along > RANGE) {
				continue;
			}
			double off = center.distanceTo(eye.add(look.scale(along)));
			if (off > BEAM_WIDTH + target.getBbWidth() * 0.5 + target.getBbHeight() * 0.25) {
				continue;
			}
			if (target.hurtServer(level, ResonanceDamage.source(player, ResonanceDamage.SONIC_BOOM), DAMAGE)) {
				ResonanceDamage.push(target, look, LAUNCH, LAUNCH_UP + Math.max(0.0, look.y) * 0.5);
			}
		}
	}

	/** Shrieker's C. */
	static boolean shriek(ServerPlayer player) {
		ServerLevel level = player.level();
		List<LivingEntity> heard = ResonanceEcho.around(player, SHRIEK_RADIUS);
		for (LivingEntity living : heard) {
			living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SHRIEK_TICKS, 0, false, false, true), player);
			if (living instanceof Warden warden) {
				warden.increaseAngerAt(player, SHRIEK_ANGER, true);
			}
		}
		ResonanceEcho.send(player, heard, SHRIEK_TICKS, ResonanceNet.Sense.HEARTBEAT);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 2.0F, 0.9F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), SHRIEK_RADIUS, 0.9F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.8F);
		for (int i = 0; i < 10; i++) {
			level.sendParticles(new ShriekParticleOption(i * 5), player.getX(), player.getEyeY() + 0.2, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.gameEvent(GameEvent.SHRIEK, player.position(), GameEvent.Context.of(player));
		com.jaymospowers.core.UltimateFilm.play(player, com.jaymospowers.core.UltimateFilm.ORBIT, SHRIEK_FILM_TICKS, SHRIEK_RADIUS);
		return true;
	}

	static void forget(ServerPlayer player) {
		if (CHARGING.remove(player.getUUID()) != null && player.getAttached(ResonancePower.STATE) != null) {
			ResonancePower.set(player, ResonancePower.state(player).withCharge(0L));
		}
	}

	static void clear() {
		CHARGING.clear();
	}
}
