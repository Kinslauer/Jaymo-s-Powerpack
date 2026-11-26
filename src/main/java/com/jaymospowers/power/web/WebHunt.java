package com.jaymospowers.power.web;

import com.jaymospowers.core.Fate;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.UltimateFilm;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Hunting dive, the ascended web slinger's ultimate (Sneak + C with a foe locked on, once ascended; a film plays,
 * {@link UltimateFilm#CUTS}): leap back from the foe, then dive at it over and over from different sides, {@link #PASSES}
 * strikes in the air with the two lines drawn to it for each pass ({@link WebPose#HUNT_DIVE}), ending in a stomp that
 * pins it to the ground ({@link WebStrength#pinAt}). Gone if the foe dies, strays further than {@link #RANGE} or after
 * {@link #MAX_TICKS}.
 */
public final class WebHunt {
	/** Five strikes, then the stomp. */
	public static final int PASSES = 5;
	/** How far off the foe it can be locked and still hunted (blocks), the wait before the next hunt (ticks) and the longest a hunt lasts. */
	public static final double RANGE = 40.0;
	public static final int COOLDOWN = 1200, MAX_TICKS = 160;
	/** The leap back: how fast (blocks/tick) and how high, and how long it takes before the first pass (ticks). */
	static final double LEAP_BACK = 0.9, LEAP_UP = 0.8;
	static final int LEAP_TICKS = 10;
	/** A pass: it comes in from this far out and this high over the foe (blocks), takes this long to get there and then dives (ticks), at this speed. */
	static final double SIDE_OUT = 8.0, SIDE_UP = 5.0, SIDE_SPEED = 1.7, DIVE_SPEED = 2.3;
	static final int SIDE_TICKS = 7, DIVE_TICKS = 12;
	/** A strike's damage (a base, and more per pass) and how hard it jolts the foe up so the next is still in the air. */
	static final float STRIKE_BASE = 4.0F, STRIKE_PER_PASS = 1.0F;
	static final double JOLT = 0.35;
	/** The stomp: damage, how long it pins (ticks), and how wide it throws the rest (blocks). */
	static final float STOMP_DAMAGE = 10.0F;
	static final int PIN_TICKS = 80;
	static final double STOMP_REACH = 2.4;

	private static final class Live {
		final int target;
		final long start;
		/** Which strike (0 to PASSES - 1, then PASSES for the stomp), and when its approach began. */
		int pass;
		long passStart;
		/** Whether this pass is diving now (past the approach). */
		boolean diving;
		double base;

		Live(int target, long start, double base) {
			this.target = target;
			this.start = start;
			this.base = base;
			this.passStart = start + LEAP_TICKS;
		}
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private WebHunt() {
	}

	static boolean active(ServerPlayer player) {
		return LIVE.containsKey(player.getUUID());
	}

	/** Sneak + C: false if it isn't a hunt (not ascended, nothing locked on, or still resting). */
	static boolean begin(ServerPlayer player) {
		if (!Fate.isAscended(player) || LIVE.containsKey(player.getUUID()) || WebPower.ragdolled(player) || WebSymbiote.driving(player)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		LivingEntity foe = LockOn.target(player);
		if (foe == null || !foe.isAlive() || foe == player || foe.distanceTo(player) > RANGE || foe instanceof Player other && !player.canHarmPlayer(other)) {
			return false;
		}
		if (now < READY.getOrDefault(player.getUUID(), 0L)) {
			return false;
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		Vec3 away = player.position().subtract(foe.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(-1.0, 0.0, -1.0) : away.normalize();
		LIVE.put(player.getUUID(), new Live(foe.getId(), now, Math.atan2(away.z, away.x)));
		ServerPlayNetworking.send(player, new WebNet.Nudge(away.scale(LEAP_BACK).add(0.0, LEAP_UP, 0.0)));
		WebPower.pose(player, WebPose.HUNT_DIVE, 1);
		UltimateFilm.play(player, UltimateFilm.CUTS, MAX_TICKS - 20, foe.getBoundingBox().getCenter(), foe, 0.0);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 1.1F);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}

	/** Whether {@code player} is mid-hunt (no movement check, no fall damage). */
	static boolean hunting(ServerPlayer player) {
		return LIVE.containsKey(player.getUUID());
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!player.isAlive() || player.isSpectator() || now - live.start > MAX_TICKS || !(level.getEntity(live.target) instanceof LivingEntity foe) || !foe.isAlive()
			|| foe.distanceTo(player) > RANGE * 1.5) {
			end(player);
			return;
		}
		player.resetFallDistance();
		WebStrikeLines.show(player, foe.getBoundingBox().getCenter(), foe.getId(), pass(foe, live), -1);
		if (now < live.passStart) {
			// still leaping back
			return;
		}
		Vec3 centre = foe.getBoundingBox().getCenter();
		long t = now - live.passStart;
		if (live.pass >= PASSES) {
			stomp(level, player, foe, live);
			return;
		}
		if (!live.diving) {
			// swinging round to this pass's side, high over the foe
			Vec3 spot = pass(foe, live);
			Vec3 to = spot.subtract(player.position());
			if (t >= SIDE_TICKS || to.length() < 1.5) {
				live.diving = true;
				live.passStart = now;
				WebPower.pose(player, WebPose.HUNT_DIVE, live.pass % 2 == 0 ? 1 : -1);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.8F, 1.3F);
				return;
			}
			WebHaul.push(player, to.normalize().scale(SIDE_SPEED));
			return;
		}
		Vec3 to = centre.subtract(player.position().add(0.0, player.getBbHeight() * 0.5, 0.0));
		if (to.length() <= STOMP_REACH || t >= DIVE_TICKS) {
			strike(level, player, foe, live);
			return;
		}
		WebHaul.push(player, to.normalize().scale(DIVE_SPEED));
	}

	/** Where this pass comes in from: a different side of the foe each time (two fifths of a turn on), high over it. */
	private static Vec3 pass(LivingEntity foe, Live live) {
		double a = live.base + live.pass * Math.PI * 0.8;
		return foe.position().add(Math.cos(a) * SIDE_OUT, SIDE_UP + foe.getBbHeight(), Math.sin(a) * SIDE_OUT);
	}

	private static void strike(ServerLevel level, ServerPlayer player, LivingEntity foe, Live live) {
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, player.damageSources().mobAttack(player), STRIKE_BASE + STRIKE_PER_PASS * live.pass);
		Vec3 at = foe.getBoundingBox().getCenter();
		if (foe.isAlive()) {
			// knocked a little off it and kept in the air for the next
			Vec3 away = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
			WebHaul.push(foe, away.scale(0.3).add(0.0, JOLT, 0.0));
		}
		ServerPlayNetworking.send(player, new WebNet.Nudge(player.position().subtract(at).normalize().scale(0.5).add(0.0, 0.4, 0.0)));
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.15);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.9F + 0.1F * live.pass);
		com.jaymospowers.core.Shakes.send(player, 0.4F, at);
		live.pass++;
		live.diving = false;
		live.passStart = level.getGameTime() + 2;
	}

	/** The last: down onto it from above, and pinned to the ground. */
	private static void stomp(ServerLevel level, ServerPlayer player, LivingEntity foe, Live live) {
		long now = level.getGameTime();
		Vec3 over = foe.position().add(0.0, 1.0, 0.0);
		Vec3 to = foe.getBoundingBox().getCenter().subtract(player.position().add(0.0, player.getBbHeight() * 0.5, 0.0));
		if (!live.diving) {
			live.diving = true;
			live.passStart = now;
			WebPower.pose(player, WebPose.DIVE_SLAM, 1);
			ServerPlayNetworking.send(player, new WebNet.Nudge(new Vec3(0.0, 0.9, 0.0)));
			return;
		}
		if (now - live.passStart < 4) {
			return;
		}
		if (to.length() > STOMP_REACH && now - live.passStart < DIVE_TICKS * 2) {
			WebHaul.push(player, to.normalize().scale(DIVE_SPEED));
			return;
		}
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, player.damageSources().mobAttack(player), STOMP_DAMAGE);
		if (foe.isAlive()) {
			Vec3 ground = WebHaul.ground(level, foe.position(), foe);
			WebStrength.pinAt(level, foe, ground, PIN_TICKS, WebSymbiote.suitShown(player));
		}
		WebImpacts.smash(level, over.add(0.0, -1.5, 0.0), 1.4, player);
		level.sendParticles(ParticleTypes.EXPLOSION, over.x, over.y, over.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, over.x, over.y, over.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.3F, 0.8F);
		com.jaymospowers.core.Shakes.heavy(player, over, 14.0, 6.0F);
		WebPower.pose(player, WebPose.HERO_LAND, 1);
		end(player);
	}

	private static void end(ServerPlayer player) {
		LIVE.remove(player.getUUID());
		WebStrikeLines.clear(player);
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false, false));
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
