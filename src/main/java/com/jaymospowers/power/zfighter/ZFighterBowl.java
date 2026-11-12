package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Z Fighter's thrown body ({@link ZFighterGrab}): a foe he throws is a weapon in flight. It bowls into everyone else
 * in its path (each once, up to {@link #MAX_HITS}), hurting them for {@link #HIT_SHARE} of his fists and flinging them
 * off, and takes a share of the blow itself. The throw leans toward an enemy near where he looks ({@link #aim}). A
 * willing body (a teammate, a pet, a player he may not harm) is thrown the same way but hurts no one and lands soft: no
 * fall damage, a slow float down, a puff of dust.
 */
final class ZFighterBowl {
	// ---- tuning ----
	/** A body that flew this long (ticks) is no longer watched. */
	static final int FLIGHT_TICKS = 40;
	/** It must have moved this far in a tick (blocks) to knock anyone over. */
	static final double MIN_SPEED = 0.35;
	/** Each thing it bowls into is hurt for this share of his fists; the body takes this share of that back. */
	static final float HIT_SHARE = 0.8F, RECOIL_SHARE = 0.25F;
	static final int MAX_HITS = 4;
	/** What it hits is flung off its line this hard (horizontal, up). */
	static final double FLING = 0.9, FLING_LIFT = 0.3;
	/** The box it knocks with is its own, grown by this much (blocks). */
	static final double REACH = 0.35;
	/** The throw leans toward an enemy within this far and this near his line of sight (cosine), if he is not locked on. */
	static final double AIM_RANGE = 24.0, AIM_COSINE = 0.93;
	/** A soft landing: slow falling for this long (ticks), and the ticks before landing counts. */
	static final int SOFT_TICKS = 30, LIFT_OFF_TICKS = 4;

	private static final class Flight {
		final @Nullable UUID by;
		final float damage;
		final boolean willing;
		final long from;
		final Set<UUID> hit = new HashSet<>();
		Vec3 last;

		Flight(@Nullable UUID by, float damage, boolean willing, long from, Vec3 last) {
			this.by = by;
			this.damage = damage;
			this.willing = willing;
			this.from = from;
			this.last = last;
		}
	}

	private static final Map<LivingEntity, Flight> FLYING = new LinkedHashMap<>();

	private ZFighterBowl() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FLYING.clear());
	}

	/** Whether {@code foe} goes along with being thrown: on his team, his pet, or a player he may not harm. */
	static boolean willing(ServerPlayer thrower, LivingEntity foe) {
		return foe.isAlliedTo(thrower) || foe instanceof OwnableEntity owned && thrower.equals(owned.getOwner()) || foe instanceof Player other && !thrower.canHarmPlayer(other);
	}

	/** A body just thrown by {@code thrower}: watched for what it hits, or for a soft landing. */
	static void thrown(LivingEntity body, ServerPlayer thrower, float fists, boolean willing) {
		FLYING.put(body, new Flight(thrower.getUUID(), fists * HIT_SHARE, willing, body.level().getGameTime(), body.position()));
		if (willing) {
			body.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SOFT_TICKS, 0, false, false));
			body.resetFallDistance();
		}
	}

	/** Where the throw goes: {@code look}, bent toward the enemy nearest his line of sight, if there is one. */
	static Vec3 aim(ServerPlayer thrower, LivingEntity body, Vec3 look) {
		LivingEntity best = null;
		double bestCos = AIM_COSINE;
		Vec3 eye = thrower.getEyePosition();
		for (LivingEntity e : thrower.level().getEntitiesOfClass(LivingEntity.class, thrower.getBoundingBox().inflate(AIM_RANGE),
			e -> e != thrower && e != body && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && enemy(thrower, e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d < 2.0 || d > AIM_RANGE) {
				continue;
			}
			double cos = to.scale(1.0 / d).dot(look);
			if (cos > bestCos) {
				bestCos = cos;
				best = e;
			}
		}
		if (best == null) {
			return look;
		}
		return best.getBoundingBox().getCenter().subtract(body.getBoundingBox().getCenter()).normalize();
	}

	private static boolean enemy(ServerPlayer thrower, LivingEntity e) {
		return !thrower.isAlliedTo(e) && (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == thrower);
	}

	private static void tick() {
		Iterator<Map.Entry<LivingEntity, Flight>> it = FLYING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, Flight> entry = it.next();
			LivingEntity body = entry.getKey();
			Flight flight = entry.getValue();
			if (body.isRemoved() || !body.isAlive() || !(body.level() instanceof ServerLevel level) || level.getGameTime() - flight.from > FLIGHT_TICKS) {
				it.remove();
				continue;
			}
			// read off where it went: a player's motion lives on his own client
			double speed = body.position().subtract(flight.last).length();
			flight.last = body.position();
			long age = level.getGameTime() - flight.from;
			if (flight.willing) {
				body.resetFallDistance();
				if (age > LIFT_OFF_TICKS && body.onGround()) {
					it.remove();
					level.sendParticles(ParticleTypes.CLOUD, true, false, body.getX(), body.getY() + 0.1, body.getZ(), 8, 0.4, 0.05, 0.4, 0.04);
					level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 0.9F);
				}
				continue;
			}
			if (speed >= MIN_SPEED) {
				bowl(level, body, flight);
			}
			if (age > LIFT_OFF_TICKS && body.onGround()) {
				it.remove();
			}
		}
	}

	/** The body ploughs through whoever it overlaps. */
	private static void bowl(ServerLevel level, LivingEntity body, Flight flight) {
		Entity thrower = flight.by == null ? null : level.getEntity(flight.by);
		LivingEntity by = thrower instanceof LivingEntity living ? living : null;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(REACH),
			e -> e != body && e != by && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !flight.hit.contains(e.getUUID())
				&& (by == null || !by.isAlliedTo(e) && !(e instanceof OwnableEntity owned && by.equals(owned.getOwner()))) && !(e instanceof Player p && p.isCreative()))) {
			if (flight.hit.size() >= MAX_HITS) {
				return;
			}
			flight.hit.add(other.getUUID());
			other.setInvulnerableTime(0);
			var source = by instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().flyIntoWall();
			if (other.hurtServer(level, source, flight.damage)) {
				Vec3 dir = other.position().subtract(body.position());
				ZFighterKi.push(other, dir, FLING, FLING_LIFT);
				Vec3 at = other.getBoundingBox().getCenter();
				MagicFx.blast(level, at, 1.0F, 0xE8E0D0);
				level.sendParticles(ParticleTypes.CRIT, true, false, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.3);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.1F, 0.7F);
				if (by instanceof ServerPlayer player) {
					Shakes.send(player, 0.3F, at);
				}
			}
			body.setInvulnerableTime(0);
			body.hurtServer(level, level.damageSources().flyIntoWall(), flight.damage * RECOIL_SHARE);
		}
	}
}
