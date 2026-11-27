package com.jaymospowers.power.web;

import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Shock lines: with both swing webs fixed on one creature (within {@link WebWhip#NEAR} of each other) a punch sends a shock
 * down both ({@link WebPose#SHOCK_HOLD}): {@link #JOLTS} jolts over {@link #TICKS} ticks, each hurting and stunning it
 * ({@link WebStrength#jolt}). Break line of sight, or let it get further than {@link #RANGE}, and the lines snap. Sent by the
 * client as a {@link WebWhip.Hold} of mode {@link WebWhip#SHOCK}; the server checks the lines itself. (The plan's idea of copper in the
 * shooters gating it is not built.)
 */
public final class WebShock {
	/** Five jolts over two seconds. */
	public static final int JOLTS = 5, TICKS = 40;
	/** Each jolt's damage and stun (ticks); the lines snap past this range (blocks); the wait before the next shock (ticks). */
	static final float DAMAGE = 2.5F;
	static final int STUN = 12;
	static final double RANGE = 28.0;
	static final int COOLDOWN = 100;

	private record Live(int target, long start) {
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private WebShock() {
	}

	/** A punch with both webs on one creature. */
	static void begin(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || !Powers.has(player, WebPower.INSTANCE) || LIVE.containsKey(player.getUUID()) || WebWhip.active(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		WebLine main = player.getAttached(WebPower.line(WebNet.MAIN));
		WebLine off = player.getAttached(WebPower.line(WebNet.OFF));
		if (main == null || off == null || main.kind() != WebLine.SWING || off.kind() != WebLine.SWING || main.entityId() < 0
			|| main.entityId() != off.entityId() || main.anchor().distanceTo(off.anchor()) > WebWhip.NEAR) {
			return;
		}
		Entity found = level.getEntity(main.entityId());
		if (!(found instanceof LivingEntity foe) || !WebWhip.whippable(player, foe) || foe instanceof Player other && !player.canHarmPlayer(other)
			|| foe.distanceTo(player) > RANGE || !player.hasLineOfSight(foe)) {
			return;
		}
		LIVE.put(player.getUUID(), new Live(foe.getId(), now));
		WebPower.pose(player, WebPose.SHOCK_HOLD, 1);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.COPPER_BULB_TURN_ON, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - live.start();
		if (!player.isAlive() || player.isSpectator() || !(level.getEntity(live.target()) instanceof LivingEntity foe) || !foe.isAlive()) {
			end(player, false);
			return;
		}
		WebLine main = player.getAttached(WebPower.line(WebNet.MAIN));
		WebLine off = player.getAttached(WebPower.line(WebNet.OFF));
		boolean held = main != null && off != null && main.entityId() == foe.getId() && off.entityId() == foe.getId();
		if (!held) {
			// a line was cut, or let go of
			end(player, false);
			return;
		}
		if (foe.distanceTo(player) > RANGE || !player.hasLineOfSight(foe)) {
			// out of reach or out of sight: both lines snap
			WebPower.snapLines(player);
			level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
			end(player, false);
			return;
		}
		int interval = TICKS / JOLTS;
		Vec3 end = foe.getBoundingBox().getCenter();
		// the current runs along both lines between jolts
		Vec3 hand = player.position().add(0.0, player.getBbHeight() * 0.7, 0.0);
		Vec3 p = hand.lerp(end, (t % interval) / (double)interval);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.02);
		if (t % interval == 0 && t / interval < JOLTS) {
			WebStrength.jolt(player, foe, DAMAGE, STUN, hand);
			com.jaymospowers.core.Shakes.send(player, 0.35F, end);
		}
		if (foe.isAlive()) {
			// stunned: it goes nowhere
			Vec3 v = foe.getDeltaMovement();
			foe.setDeltaMovement(v.x * 0.2, Math.min(v.y, 0.0), v.z * 0.2);
			foe.needsSync = true;
			foe.syncVelocity = true;
		}
		if (t >= TICKS) {
			end(player, true);
		}
	}

	private static void end(ServerPlayer player, boolean done) {
		LIVE.remove(player.getUUID());
		READY.put(player.getUUID(), player.level().getGameTime() + (done ? COOLDOWN : COOLDOWN / 2));
		player.removeAttached(WebPower.POSE);
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
		READY.remove(player.getUUID());
	}

	static void clearAll() {
		LIVE.clear();
		READY.clear();
	}
}
