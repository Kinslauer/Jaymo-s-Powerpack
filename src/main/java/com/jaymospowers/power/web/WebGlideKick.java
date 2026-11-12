package com.jaymospowers.power.web;

import com.jaymospowers.core.Powers;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Glide kick: gliding on the web wings into a living creature at speed lands a both-feet kick (everyone sees the legs
 * thrown out in front: {@link WebPose#GLIDE_KICK}). The harder and faster you come, the more it hurts, most of all out
 * of a dive. The target is knocked down (laid out like a takedown) and you bounce back up into the glide.
 */
public final class WebGlideKick {
	// ================================================================================================================
	// Tuning
	/** The least speed (blocks/tick) a glide must have to kick, and the speed at which the kick is at full strength. */
	static final double MIN_SPEED = 0.85, FULL_SPEED = 2.0;
	/** Damage: a base, and more per block/tick of speed; multiplied out of a dive (falling faster than {@link #DIVE_FALL}). */
	static final float BASE_DAMAGE = 3.0F, PER_SPEED = 5.0F, DIVE_MULTIPLIER = 1.5F;
	static final double DIVE_FALL = 0.6;
	/** How long the target stays down (ticks). */
	static final int DOWN_TICKS = 160;
	/** Ticks before the next kick, and how far (blocks) ahead of the body a target is caught. */
	static final int COOLDOWN = 20;
	static final double CATCH_AHEAD = 0.6, CATCH_WIDTH = 0.5;
	/** The bounce: keeps this share of the horizontal speed, and goes up this fast (blocks/tick). */
	static final double BOUNCE_KEEP = 0.55, BOUNCE_UP = 0.7;
	// ================================================================================================================

	private static final Map<ServerPlayer, Vec3> LAST = new WeakHashMap<>();
	private static final Map<ServerPlayer, Long> NEXT = new WeakHashMap<>();

	private WebGlideKick() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
	}

	private static void tick(ServerPlayer player) {
		Vec3 last = LAST.put(player, player.position());
		if (last == null || player.getAttached(WebPower.WINGS) == null || !player.isAlive() || player.isSpectator()
			|| !Powers.has(player, WebPower.INSTANCE) || player.onGround()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (NEXT.getOrDefault(player, 0L) > now) {
			return;
		}
		Vec3 moved = player.position().subtract(last);
		double speed = moved.length();
		if (speed < MIN_SPEED || speed > 6.0) {
			return;
		}
		Vec3 dir = moved.scale(1.0 / speed);
		AABB reach = player.getBoundingBox().expandTowards(dir.scale(CATCH_AHEAD + speed * 0.5)).inflate(CATCH_WIDTH, 0.2, CATCH_WIDTH);
		LivingEntity hit = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach, e -> e != player && e.isAlive() && !e.isSpectator() && !e.isPassengerOfSameVehicle(player)
			&& (!(e instanceof Player other) || player.canHarmPlayer(other)) && !Downed.isDowned(e) && !Finishers.helpless(e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(player.getBoundingBox().getCenter());
			if (to.dot(dir) <= 0.0) {
				continue;
			}
			double d = to.lengthSqr();
			if (d < best) {
				best = d;
				hit = e;
			}
		}
		if (hit == null) {
			return;
		}
		kick(player, hit, moved, speed);
	}

	private static void kick(ServerPlayer player, LivingEntity target, Vec3 moved, double speed) {
		ServerLevel level = player.level();
		NEXT.put(player, level.getGameTime() + COOLDOWN);
		boolean dive = -moved.y > DIVE_FALL;
		float strength = (float)Math.min(1.0, speed / FULL_SPEED);
		float damage = (BASE_DAMAGE + PER_SPEED * (float)speed) * (dive ? DIVE_MULTIPLIER : 1.0F);
		target.setInvulnerableTime(0);
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		Vec3 flat = moved.multiply(1.0, 0.0, 1.0);
		Vec3 dir = flat.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0) : flat.normalize();
		if (target.isAlive()) {
			if (!Downed.knockOut(level, target, DOWN_TICKS, true, "message.jaymospowers.takedown.downed")) {
				DarktideStatus.stun(target, DOWN_TICKS / 4);
			}
			target.setDeltaMovement(dir.scale(0.5 * strength + 0.2).add(0.0, 0.25, 0.0));
			target.needsSync = true;
			target.syncVelocity = true;
		}
		// the bounce: back up into the glide
		Vec3 v = new Vec3(flat.x * BOUNCE_KEEP, BOUNCE_UP * (0.7 + 0.3 * strength), flat.z * BOUNCE_KEEP);
		ServerPlayNetworking.send(player, new WebNet.Nudge(v));
		WebPower.pose(player, WebPose.GLIDE_KICK, 0);
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 14, 0.3, 0.4, 0.3, 0.06);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, dive ? 0.6F : 0.9F);
		com.jaymospowers.core.Shakes.send(player, 0.6F + 0.5F * strength, at);
	}
}
