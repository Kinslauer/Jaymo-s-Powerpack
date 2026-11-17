package com.jaymospowers.power.web;

import com.jaymospowers.core.fx.MagicFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Web cartridges (Andy's Awakening pick: "Web Slinger's Web Shot"). A web shooter's cartridge holds one kind of web fluid,
 * and a Web Shot (V: the tapped pellet and the charged shot) from that wrist is that kind:
 * <ul>
 * <li><b>Sticky</b> (the plain cartridge): the web as it always was; a pellet's every hit stacks web, five in a row and they're
 * webbed up ({@link WebStrength#shotHit}).</li>
 * <li><b>Concussive</b> (brewed with gunpowder): no web; the ball bursts in a hard thump that hurts more, throws back
 * everything round it and leaves it reeling.</li>
 * <li><b>Taser</b> (brewed with copper and redstone): no web; what it hits is shocked still for a moment, and the charge jumps
 * to the nearest foe beside it.</li>
 * <li><b>Splitter</b> (brewed with flint): the charged shot leaves the nozzle as three, fanned.</li>
 * </ul>
 * The bomb (sneak + tap) and the suit's mass are always the plain web. Kinds are on the cartridge item ({@link WebShooters#KIND})
 * and on each wrist ({@link WebShooters.Load#kind}).
 */
public final class WebKinds {
	public static final int STICKY = 0, CONCUSSIVE = 1, TASER = 2, SPLITTER = 3, COUNT = 4;
	static final String[] NAMES = {"sticky", "concussive", "taser", "splitter"};

	// ---- tuning ----
	/** Concussive: the extra harm (a share of the shot's), the burst's reach (blocks), the throw, the reel (ticks). */
	static final float THUMP_SHARE = 1.4F;
	static final double THUMP_RADIUS = 2.5, THUMP_PUSH = 1.0;
	static final int REEL_TICKS = 40;
	/** Taser: the harm on top, how long it holds still (ticks), how far the charge jumps (blocks). */
	static final float SHOCK = 3.0F;
	static final int SHOCK_TICKS = 30;
	static final double JUMP = 5.0;
	/** Splitter: how many, and the fan's spread (degrees each side). */
	static final int SPLIT = 3;
	static final float SPLIT_SPREAD = 8.0F;
	static final int THUMP = 0xF2E4C8, SPARK = 0xFFF27A;

	private WebKinds() {
	}

	static String name(int kind) {
		return NAMES[Math.max(0, Math.min(COUNT - 1, kind))];
	}

	/**
	 * A Concussive or Taser ball burst at {@code at} ({@code direct}: what it struck, if anything), for {@code damage} (the
	 * shot's). True if it was one of them (and the plain web's splash is skipped).
	 */
	static boolean burst(ServerLevel level, int kind, Entity ball, @Nullable Entity owner, @Nullable Entity direct, Vec3 at, float damage) {
		if (kind == CONCUSSIVE) {
			MagicFx.blast(level, at, (float)THUMP_RADIUS, THUMP);
			MagicFx.ring(level, at, (float)THUMP_RADIUS * 1.2F, THUMP);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(THUMP_RADIUS),
				e -> e.isAlive() && !e.isSpectator() && e != owner && e.position().distanceToSqr(at) <= THUMP_RADIUS * THUMP_RADIUS * 1.5)) {
				float hurt = e == direct ? damage * THUMP_SHARE : damage * 0.5F;
				e.hurtServer(level, ball.damageSources().thrown(ball, owner), hurt);
				Vec3 out = e.position().subtract(at).multiply(1.0, 0.0, 1.0);
				out = out.lengthSqr() < 1.0E-4 ? ball.getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize() : out.normalize();
				e.setDeltaMovement(e.getDeltaMovement().add(out.x * THUMP_PUSH, 0.4, out.z * THUMP_PUSH));
				e.syncVelocity = true;
				e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, REEL_TICKS * 2, 0), owner);
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, REEL_TICKS, 1), owner);
			}
			return true;
		}
		if (kind == TASER) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 2.0F);
			level.sendParticles(MagicFx.spark(SPARK, 1.0F), at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.2);
			if (direct instanceof LivingEntity first && first.isAlive()) {
				shock(level, ball, owner, first, damage + SHOCK);
				LivingEntity next = null;
				double best = JUMP * JUMP;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, first.getBoundingBox().inflate(JUMP),
					e -> e != first && e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof Player p && owner instanceof Player o && !o.canHarmPlayer(p)))) {
					double d = e.distanceToSqr(first);
					if (d < best) {
						best = d;
						next = e;
					}
				}
				if (next != null) {
					MagicFx.beam(level, first.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), 0.05F, SPARK, 6);
					shock(level, ball, owner, next, SHOCK);
				}
			}
			return true;
		}
		return false;
	}

	private static void shock(ServerLevel level, Entity ball, @Nullable Entity owner, LivingEntity e, float harm) {
		e.hurtServer(level, ball.damageSources().thrown(ball, owner), harm);
		e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHOCK_TICKS, 9, false, false), owner);
		e.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, SHOCK_TICKS, 3, false, false), owner);
		if (e instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		Vec3 c = e.getBoundingBox().getCenter();
		level.sendParticles(MagicFx.spark(SPARK, 0.8F), c.x, c.y, c.z, 10, e.getBbWidth() * 0.4, e.getBbHeight() * 0.4, e.getBbWidth() * 0.4, 0.1);
	}
}
