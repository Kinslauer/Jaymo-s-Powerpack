package com.jaymospowers.power.web;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Ride a launched foe: kick a creature into the air ({@link com.jaymospowers.mortal.AirCombo}), zip onto it
 * ({@link WebZipStrike}) and you stand on its back as it flies ({@link WebPose#SURF}). Where you look, it goes: the sides and
 * ahead push it along, looking steeply down drives it down, up holds it aloft. Into a wall or the ground it crashes
 * ({@link #CRASH_BASE} damage and more for the speed, and the ground breaks as a slam's does), and you kick off clear. Sneak
 * or Jump gets off early; it also ends after {@link #MAX_TICKS}. Only a creature (not another player: their flight is their
 * own client's) and only for as long as it is in the air.
 */
public final class WebSurf {
	/** The longest ride (ticks), and the shortest before a landing counts as a crash. */
	static final int MAX_TICKS = 70, CRASH_AFTER = 6;
	/** What a look sets the foe doing: the push along it (blocks/tick each tick), its top flat speed, and the up and down it is held to. */
	static final double PUSH = 0.16, TOP = 1.2, DIVE = -1.1, FLOAT = 0.18, SINK = -0.2;
	/** Looking this steeply down (the look's y) drives it down; this steeply up holds it up. */
	static final double LOOK_DOWN = -0.55, LOOK_UP = 0.5;
	/** The crash: damage, and more per block/tick of speed, capped; the speed under which a bump is not a crash. */
	static final float CRASH_BASE = 5.0F, CRASH_PER_SPEED = 8.0F, CRASH_MAX = 20.0F;
	static final double CRASH_SPEED = 0.35;
	/** How firmly you are kept on its back (the share of the gap closed a tick). */
	static final double STICK = 0.6;

	private static final class Live {
		final int target;
		final long start;
		/** How fast it was going last tick (blocks/tick), for what it hits. */
		double lastSpeed;

		Live(int target, long start) {
			this.target = target;
			this.start = start;
		}
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();

	private WebSurf() {
	}

	/** Stands {@code player} on {@code foe}'s back. */
	static void begin(ServerPlayer player, LivingEntity foe) {
		LIVE.put(player.getUUID(), new Live(foe.getId(), player.level().getGameTime()));
		WebPower.pose(player, WebPose.SURF, 1);
		ServerLevel level = player.level();
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.9F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, foe.getX(), foe.getY() + foe.getBbHeight(), foe.getZ(), 10, 0.3, 0.1, 0.3, 0.02);
	}

	static boolean surfing(ServerPlayer player) {
		return LIVE.containsKey(player.getUUID());
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - live.start;
		if (!player.isAlive() || player.isSpectator() || !(level.getEntity(live.target) instanceof LivingEntity foe) || !foe.isAlive()) {
			end(player, null, false);
			return;
		}
		var input = player.getLastClientInput();
		if (t >= MAX_TICKS || t > 4 && (input.shift() || input.jump()) || player.distanceTo(foe) > 8.0) {
			end(player, foe, input.jump());
			return;
		}
		Vec3 v = foe.getDeltaMovement();
		double speed = Math.sqrt(v.x * v.x + v.z * v.z);
		// the crash: into a wall, or down onto the ground, as fast as it was going a tick ago
		if (t >= CRASH_AFTER && live.lastSpeed >= CRASH_SPEED && (foe.horizontalCollision || foe.onGround() || foe.verticalCollision)) {
			crash(level, player, foe, live.lastSpeed);
			return;
		}
		if (t >= CRASH_AFTER && foe.onGround()) {
			end(player, foe, false);
			return;
		}
		live.lastSpeed = Math.max(speed, Math.abs(v.y));
		// steered by your look
		Vec3 look = player.getLookAngle();
		Vec3 flat = look.multiply(1.0, 0.0, 1.0);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		double x = v.x * 0.92 + flat.x * PUSH, z = v.z * 0.92 + flat.z * PUSH;
		double along = Math.sqrt(x * x + z * z);
		if (along > TOP) {
			x *= TOP / along;
			z *= TOP / along;
		}
		double y = look.y < LOOK_DOWN ? DIVE : look.y > LOOK_UP ? FLOAT : Math.max(v.y, SINK);
		if (foe instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		foe.resetFallDistance();
		Vec3 now = new Vec3(x, y, z);
		WebHaul.push(foe, now);
		// and you stay on its back
		Vec3 spot = foe.position().add(0.0, foe.getBbHeight() - 0.1, 0.0);
		WebHaul.push(player, now.add(spot.subtract(player.position()).scale(STICK)));
		player.resetFallDistance();
		if (t % 4 == 0) {
			WebPower.pose(player, WebPose.SURF, 1);
		}
		WebStrikeLines.toPoint(player, spot.add(0.0, -0.2, 0.0));
		if (t % 3 == 0) {
			level.sendParticles(ParticleTypes.ITEM_COBWEB, foe.getX(), foe.getY() + foe.getBbHeight() * 0.5, foe.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
		}
	}

	private static void crash(ServerLevel level, ServerPlayer player, LivingEntity foe, double speed) {
		float damage = Math.min(CRASH_MAX, CRASH_BASE + CRASH_PER_SPEED * (float)speed);
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, player.damageSources().mobAttack(player), damage);
		Vec3 at = foe.position();
		WebImpacts.smash(level, at.add(0.0, -0.5, 0.0), Math.max(0.6, speed), player);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.2F, 0.8F);
		com.jaymospowers.core.Shakes.heavy(player, at, 12.0, (float)Math.min(8.0, 3.0 + speed * 4.0));
		end(player, foe, true);
	}

	/** Off its back: a kick clear (up, and on the way you were going when {@code hop}), and a soft landing. */
	private static void end(ServerPlayer player, LivingEntity foe, boolean hop) {
		LIVE.remove(player.getUUID());
		player.removeAttached(WebPower.POSE);
		WebStrikeLines.clear(player);
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false, false));
		Vec3 v = foe == null ? Vec3.ZERO : foe.getDeltaMovement();
		ServerPlayNetworking.send(player, new WebNet.Nudge(new Vec3(v.x * 0.5, hop ? 0.55 : 0.2, v.z * 0.5)));
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
	}

	static void clearAll() {
		LIVE.clear();
	}
}
