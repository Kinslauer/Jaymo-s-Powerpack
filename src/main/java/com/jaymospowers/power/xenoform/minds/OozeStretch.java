package com.jaymospowers.power.xenoform.minds;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.GreatFoes;
import com.jaymospowers.power.xenoform.XenoMinds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Ooze's elastic limbs, on the keys beside the moves it already has (sneak + the key):
 * <ul>
 * <li>Sneak + G, Lash: an arm stretched {@link #WHIP_REACH} blocks out sweeps a wide arc ({@link #WHIP_ARC} degrees) and
 * flings everything in it sideways.</li>
 * <li>Sneak + R, Wrap: an arm winds round the nearest creature in {@link #WRAP_REACH}, squeezes it, and drags it where
 * you turn; sneak + R again lets go. Great foes are squeezed but not dragged.</li>
 * <li>Sneak + B, Mallet: both hands swell and come down ahead of you for a heavy slam.</li>
 * <li>Always, Rebound: a shot that hits the jelly bounces back the way it came, and is the Ooze's now.</li>
 * </ul>
 * The arm is drawn as a {@link MagicFx} beam from the shoulder to the fist with an orb for the fist.
 */
public final class OozeStretch {
	// ---- tuning ----
	public static final double WHIP_REACH = 7.0, WHIP_ARC = 150.0, WHIP_FLING = 1.1, WHIP_LIFT = 0.3;
	public static final float WHIP_DAMAGE = 5.0F;
	public static final int WHIP_COOLDOWN = 50;
	public static final double WRAP_REACH = 7.0, WRAP_MIN = 1.8, WRAP_REEL = 0.04, WRAP_PULL = 0.4, WRAP_SPEED = 1.0;
	public static final int WRAP_TICKS = 80, SQUEEZE_EVERY = 10, WRAP_COOLDOWN = 80;
	public static final float SQUEEZE_DAMAGE = 1.5F;
	public static final double MALLET_AHEAD = 2.6, MALLET_RADIUS = 3.2, MALLET_OUT = 0.9, MALLET_UP = 0.55;
	public static final float MALLET_DAMAGE = 9.0F;
	public static final int MALLET_COOLDOWN = 100;
	/** A shot that bounces keeps this share of its speed, with this much scatter. */
	public static final double REBOUND_KEEP = 0.9, REBOUND_SCATTER = 0.08;

	private static final class Wrap {
		int target, ticks;
		double dist;
	}

	private static final Map<UUID, Wrap> WRAPS = new HashMap<>();

	private OozeStretch() {
	}

	private static Vec3 shoulder(ServerPlayer player) {
		return player.position().add(0.0, player.getBbHeight() * 0.75, 0.0);
	}

	/** Sneak + G. */
	public static void lash(ServerPlayer player, int color) {
		if (!XenoMinds.ready(player, "ooze_lash")) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 from = shoulder(player);
		Vec3 forward = MindsKit.flat(player);
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		double cos = Math.cos(Math.toRadians(WHIP_ARC * 0.5));
		for (LivingEntity e : MindsKit.near(player, from, WHIP_REACH)) {
			Vec3 to = e.position().subtract(player.position()).horizontal();
			if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(forward) < cos || Math.abs(e.getY() - player.getY()) > 3.0) {
				continue;
			}
			e.setInvulnerableTime(0);
			e.hurtServer(level, player.damageSources().playerAttack(player), WHIP_DAMAGE);
			if (!GreatFoes.is(e)) {
				// flung sideways along the swing (left to right), a little back from the arm
				Vec3 fling = side.scale(-WHIP_FLING).add(to.normalize().scale(0.35)).add(0.0, WHIP_LIFT, 0.0);
				e.setDeltaMovement(e.getDeltaMovement().add(fling));
				e.syncVelocity = true;
			}
		}
		MagicFx.swing(level, from, forward, side, WHIP_REACH, WHIP_ARC, 0.5F, color, 8);
		MagicFx.beam(level, from, from.add(forward.scale(WHIP_REACH)), 0.1F, color, 5);
		MagicFx.orb(level, from.add(forward.scale(WHIP_REACH)), 0.3F, color, 5);
		MindsKit.sound(player, SoundEvents.SLIME_ATTACK, 1.2F, 0.7F);
		MindsKit.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
		XenoMinds.cool(player, "ooze_lash", WHIP_COOLDOWN);
	}

	/** Sneak + R: wrap the nearest creature, or let go of the one wrapped. */
	public static void wrap(ServerPlayer player, int color) {
		if (WRAPS.containsKey(player.getUUID())) {
			release(player, true);
			return;
		}
		if (!XenoMinds.ready(player, "ooze_wrap")) {
			return;
		}
		LivingEntity target = null;
		double best = WRAP_REACH * WRAP_REACH;
		for (LivingEntity e : MindsKit.near(player, shoulder(player), WRAP_REACH)) {
			double d = e.distanceToSqr(player);
			if (d < best) {
				best = d;
				target = e;
			}
		}
		if (target == null) {
			return;
		}
		Wrap wrap = new Wrap();
		wrap.target = target.getId();
		wrap.ticks = WRAP_TICKS;
		wrap.dist = Math.max(WRAP_MIN, Math.sqrt(best));
		WRAPS.put(player.getUUID(), wrap);
		MindsKit.sound(player, SoundEvents.SLIME_SQUISH, 1.2F, 0.5F);
	}

	/** Lets go of whatever is wrapped. */
	public static void release(ServerPlayer player, boolean fx) {
		Wrap wrap = WRAPS.remove(player.getUUID());
		if (wrap != null) {
			XenoMinds.cool(player, "ooze_wrap", WRAP_COOLDOWN);
			if (fx) {
				MindsKit.sound(player, SoundEvents.SLIME_SQUISH_SMALL, 1.0F, 1.4F);
			}
		}
	}

	public static void forget(ServerPlayer player) {
		WRAPS.remove(player.getUUID());
	}

	/** Every tick as Ooze: the wrap drags and squeezes. */
	public static void tick(ServerPlayer player, int color) {
		Wrap wrap = WRAPS.get(player.getUUID());
		if (wrap == null) {
			return;
		}
		ServerLevel level = player.level();
		Entity found = level.getEntity(wrap.target);
		if (!(found instanceof LivingEntity held) || !held.isAlive() || --wrap.ticks <= 0 || held.distanceTo(player) > WRAP_REACH * 1.7 || player.isSpectator()) {
			release(player, true);
			return;
		}
		wrap.dist = Math.max(WRAP_MIN, wrap.dist - WRAP_REEL);
		if (!GreatFoes.is(held)) {
			// it is dragged to where you look, as far out as the arm has reeled it in
			Vec3 want = player.position().add(MindsKit.flat(player).scale(wrap.dist));
			Vec3 pull = want.subtract(held.position());
			Vec3 flat = new Vec3(pull.x, 0.0, pull.z).scale(WRAP_PULL);
			if (flat.lengthSqr() > WRAP_SPEED * WRAP_SPEED) {
				flat = flat.normalize().scale(WRAP_SPEED);
			}
			held.setDeltaMovement(flat.x, Math.max(-0.1, Math.min(0.25, pull.y * 0.3)), flat.z);
			held.syncVelocity = true;
			held.resetFallDistance();
			if (held instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
		held.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 6, 2, false, false), player);
		if (wrap.ticks % SQUEEZE_EVERY == 0) {
			held.setInvulnerableTime(0);
			held.hurtServer(level, player.damageSources().playerAttack(player), SQUEEZE_DAMAGE);
			MindsKit.sound(player, SoundEvents.SLIME_SQUISH_SMALL, 0.8F, 0.6F);
		}
		if (player.tickCount % 2 == 0) {
			MagicFx.beam(level, shoulder(player), held.getBoundingBox().getCenter(), 0.1F, color, 3);
		}
	}

	/** Sneak + B: both hands swell and slam down ahead. */
	public static void mallet(ServerPlayer player, int color) {
		if (!XenoMinds.ready(player, "ooze_mallet")) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 forward = MindsKit.flat(player);
		Vec3 at = player.position().add(forward.scale(MALLET_AHEAD));
		for (LivingEntity e : MindsKit.near(player, at.add(0.0, 0.8, 0.0), MALLET_RADIUS)) {
			e.setInvulnerableTime(0);
			e.hurtServer(level, player.damageSources().playerAttack(player), MALLET_DAMAGE);
			MindsKit.shove(e, at, MALLET_OUT, MALLET_UP);
		}
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		MagicFx.orb(level, at.add(side.scale(0.7)).add(0.0, 0.5, 0.0), 0.55F, color, 6);
		MagicFx.orb(level, at.add(side.scale(-0.7)).add(0.0, 0.5, 0.0), 0.55F, color, 6);
		MagicFx.blast(level, at.add(0.0, 0.3, 0.0), 1.6F, color);
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), (float)MALLET_RADIUS, color);
		MindsKit.sound(player, SoundEvents.SLIME_JUMP, 1.5F, 0.4F);
		MindsKit.sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 0.7F);
		Shakes.heavy(player, at, 10.0, 0.7F);
		XenoMinds.cool(player, "ooze_mallet", MALLET_COOLDOWN);
	}

	/** A shot that strikes the jelly bounces back. True: the blow is turned aside. */
	public static boolean bounce(ServerPlayer player, DamageSource source) {
		if (!(source.getDirectEntity() instanceof Projectile shot) || shot.isRemoved()) {
			return false;
		}
		Vec3 v = shot.getDeltaMovement();
		if (v.lengthSqr() < 1.0E-4) {
			return false;
		}
		var rng = player.getRandom();
		Vec3 back = v.scale(-REBOUND_KEEP).add((rng.nextDouble() - 0.5) * REBOUND_SCATTER, (rng.nextDouble() - 0.5) * REBOUND_SCATTER,
			(rng.nextDouble() - 0.5) * REBOUND_SCATTER);
		shot.setDeltaMovement(back);
		shot.setPos(shot.position().add(back.normalize().scale(0.6)));
		shot.setOwner(player);
		shot.syncVelocity = true;
		ServerLevel level = player.level();
		MagicFx.blast(level, shot.position(), 0.5F, 0x6CC060, 6);
		MindsKit.soundAt(level, shot.position(), SoundEvents.SLIME_SQUISH_SMALL, 0.9F, 1.5F);
		return true;
	}
}
