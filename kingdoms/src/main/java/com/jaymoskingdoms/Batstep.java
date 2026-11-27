package com.jaymoskingdoms;

import com.jaymoskingdoms.nightforms.NightForms;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Batstep (Night's Blood, patch 4). Andy's plan: "A short blink (about 8 blocks) to where you look, in a burst of bats.
 * Anything you pass through is hurt and staggered. The riposte stays as it is." A move on the skill bar ({@code night_batstep}):
 * they go as far along the look as there is room for, up to {@link #REACH}, a burst of bats leaving where they stood and
 * another where they land; whatever stood in the way is hurt ({@link #DAMAGE}) and staggered (slowed, shoved aside). The
 * crouch riposte ({@link NightForms}) is untouched.
 */
public final class Batstep {
	/** What it costs (mB), the wait (ticks), how far it goes (blocks) and how near the path a body must be to be passed through. */
	static final int COST_MB = 250, COOLDOWN = 100;
	static final double REACH = 8.0, PASS = 0.9;
	/** What a body passed through suffers: harm, the slowing's length and level, the shove aside. */
	static final float DAMAGE = 5.0F;
	static final int STAGGER_TICKS = 50, STAGGER_LEVEL = 2;
	static final double SHOVE = 0.5;

	private static final Map<UUID, Long> READY = new HashMap<>();

	private Batstep() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READY.clear());
	}

	/** The move off the bar: whether it went. */
	public static boolean cast(ServerPlayer p) {
		if (!VampireBlood.full(p) || !(p.level() instanceof ServerLevel level) || PowersCompat.snuffed(p) || p.isPassenger() || !NightForms.form(p).isEmpty()) {
			return false;
		}
		long now = level.getGameTime();
		if (now < READY.getOrDefault(p.getUUID(), 0L)) {
			return false;
		}
		Vec3 start = p.position();
		Vec3 look = p.getLookAngle();
		Vec3 eye = p.getEyePosition();
		// as far as the eye can see along the look, up to the reach
		double room = REACH;
		var hit = level.clip(new ClipContext(eye, eye.add(look.scale(REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (hit.getType() != HitResult.Type.MISS) {
			room = Math.max(0.0, hit.getLocation().distanceTo(eye) - 0.6);
		}
		Vec3 dest = null;
		for (double d = room; d >= 1.5; d -= 0.5) {
			Vec3 candidate = start.add(look.scale(d));
			if (level.noCollision(p, p.getBoundingBox().move(candidate.subtract(start)))) {
				dest = candidate;
				break;
			}
		}
		if (dest == null) {
			ArtKeys.refuse(p, "no_room");
			return false;
		}
		if (!VampireBlood.pay(p, COST_MB)) {
			return false;
		}
		READY.put(p.getUUID(), now + COOLDOWN);
		// whatever stands along the way
		Vec3 from = start.add(0.0, p.getBbHeight() * 0.5, 0.0), to = dest.add(0.0, p.getBbHeight() * 0.5, 0.0);
		Vec3 line = to.subtract(from);
		List<LivingEntity> passed = level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(PASS + 1.0),
			e -> NightTargets.target(p, e) && e.getBoundingBox().inflate(PASS).clip(from, to).isPresent());
		NightForms.burst(level, p, start.add(0.0, 1.0, 0.0));
		Fx.stroke(level, from, to, 0.5F, NightForms.BLOOD);
		p.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), p.getYRot(), p.getXRot(), false);
		NightGifts.fallsSoftly(p);
		NightForms.burst(level, p, dest.add(0.0, 1.0, 0.0));
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.3F, 0.9F);
		Vec3 side = new Vec3(-line.z, 0.0, line.x);
		side = side.lengthSqr() < 1.0E-6 ? Vec3.ZERO : side.normalize();
		for (LivingEntity e : passed) {
			e.hurtServer(level, p.damageSources().playerAttack(p), NightTargets.harm(p, DAMAGE));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, STAGGER_LEVEL, false, true, true), p);
			double sign = Math.signum(e.getBoundingBox().getCenter().subtract(from).dot(side));
			e.push(side.x * SHOVE * (sign == 0.0 ? 1.0 : sign), 0.2, side.z * SHOVE * (sign == 0.0 ? 1.0 : sign));
			e.needsSync = true;
			Fx.burst(level, e.getX(), e.getY(0.5), e.getZ(), NightForms.BLOOD, 0.9F, 6, 0.3, 0.4, 0.3);
		}
		return true;
	}
}
