package com.jaymospowers.power.gravity;

import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.core.gifted.GiftKit;
import com.jaymospowers.core.gifted.Gifted;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.2) Gravity for a gifted villager ({@link Gifted}): a pull that drags the foe in, a crush that presses them to the
 * ground, a lift that heaves them up to fall, and a weightless leap away. A ruler's ultimate is the Meteor, kept as a
 * last resort (it is the heaviest thing the power has): it falls on the foe in light and smoke, and unlike a player's it
 * breaks no blocks. Out of a fight, small motes float round them.
 */
final class GravityGift implements GiftKit {
	/** The pull: reach, how hard it drags and lifts, and cooldown (ticks). */
	static final double PULL_RANGE = 16.0, PULL_SPEED = 1.1, PULL_LIFT = 0.2;
	static final int PULL_COOLDOWN = 80;
	/** The crush: reach, how hard it presses down, damage, the slow (ticks, level), and cooldown. */
	static final double CRUSH_RANGE = 6.0, CRUSH_PRESS = -1.2;
	static final float CRUSH_DAMAGE = 5.0F;
	static final int CRUSH_SLOW_TICKS = 60, CRUSH_SLOW_LEVEL = 3, CRUSH_COOLDOWN = 120;
	/** The lift: reach, how high it heaves them (the fall is the harm), and cooldown. */
	static final double LIFT_RANGE = 12.0, LIFT_SPEED = 1.2;
	static final int LIFT_COOLDOWN = 180;
	/** The leap: how hard and high it carries them, how long they stay weightless (ticks), and cooldown. */
	static final double LEAP_SPEED = 1.0, LEAP_LIFT = 0.9;
	static final int LEAP_FEATHER = 60, LEAP_COOLDOWN = 130;

	/** A ruler's Meteor (a last resort): the radius it lands in, how high it starts, the throw and lift, its damage, and the film (ticks). */
	static final double METEOR_RADIUS = 6.0, METEOR_HEIGHT = 16.0, METEOR_THROW = 1.0, METEOR_LIFT = 0.6;
	static final float METEOR_DAMAGE = 14.0F;
	static final int METEOR_FILM = 60;
	/** Everyday: this many motes float round them. */
	static final int MOTES = 5;

	private static final DustParticleOptions SHADOW = new DustParticleOptions(0x120A1A, 2.0F);
	private static final DustParticleOptions WHITE_HOT = new DustParticleOptions(0xFFE6A8, 4.0F);

	static void init() {
		Gifted.register(new GravityGift());
	}

	@Override
	public Identifier power() {
		return GravityPower.ID;
	}

	@Override
	public List<Move> moves() {
		return List.of(
			Move.of("lift", Kind.STRIKE, LIFT_COOLDOWN, 2.0, LIFT_RANGE, GravityGift::lift),
			Move.of("pull", Kind.STRIKE, PULL_COOLDOWN, 4.0, PULL_RANGE, GravityGift::pull),
			Move.of("crush", Kind.CLOSE, CRUSH_COOLDOWN, 0.0, CRUSH_RANGE, GravityGift::crush),
			Move.of("leap", Kind.ESCAPE, LEAP_COOLDOWN, 0.0, 5.0, GravityGift::leap));
	}

	private static void push(LivingEntity e, Vec3 horizontal, double y) {
		e.setDeltaMovement(horizontal.x, y, horizontal.z);
		e.syncVelocity = true;
	}

	private static boolean pull(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 in = caster.position().subtract(foe.position()).horizontal();
		if (in.lengthSqr() < 1.0E-4) {
			return false;
		}
		Vec3 from = caster.getEyePosition(), to = foe.getBoundingBox().getCenter();
		Vec3 step = to.subtract(from);
		int steps = Math.max(1, (int)(step.length() * 2.0));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(step.scale((double)i / steps));
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y, to.z, 15, 0.3, 0.4, 0.3, 0.1);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.NEUTRAL, 0.9F, 0.6F);
		push(foe, in.normalize().scale(PULL_SPEED), Math.max(foe.getDeltaMovement().y, 0.0) + PULL_LIFT);
		return true;
	}

	private static boolean crush(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI / 8.0;
			level.sendParticles(SHADOW, foe.getX() + Math.cos(a) * 1.0, foe.getY() + 0.1, foe.getZ() + Math.sin(a) * 1.0, 1, 0.05, 0.0, 0.05, 0.0);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, foe.getX(), foe.getY(1.0), foe.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.NEUTRAL, 1.0F, 0.6F);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.6F, 0.5F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), CRUSH_DAMAGE);
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CRUSH_SLOW_TICKS, CRUSH_SLOW_LEVEL), caster);
		push(foe, Vec3.ZERO, CRUSH_PRESS);
		return true;
	}

	private static boolean lift(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!foe.onGround()) {
			return false;
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, foe.getX(), foe.getY() + 0.2, foe.getZ(), 25, 0.3, 0.3, 0.3, 0.2);
		level.sendParticles(ParticleTypes.PORTAL, foe.getX(), foe.getY() + 0.2, foe.getZ(), 15, 0.4, 0.1, 0.4, 0.3);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.NEUTRAL, 1.0F, 0.4F);
		push(foe, Vec3.ZERO, LIFT_SPEED);
		return true;
	}

	private static boolean leap(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!caster.onGround()) {
			return false;
		}
		Vec3 away = caster.position().subtract(foe.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal().reverse() : away;
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, caster.getX(), caster.getY() + 0.2, caster.getZ(), 20, 0.3, 0.2, 0.3, 0.1);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.SHULKER_BULLET_HURT, SoundSource.NEUTRAL, 0.8F, 1.6F);
		push(caster, away.normalize().scale(LEAP_SPEED), LEAP_LIFT);
		caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LEAP_FEATHER, 0), caster);
		caster.fallDistance = 0.0;
		return true;
	}

	@Override
	public void everyday(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		// small things float round them
		double base = mob.tickCount * 0.15;
		for (int i = 0; i < MOTES; i++) {
			double a = base + i * Math.PI * 2.0 / MOTES;
			level.sendParticles(ParticleTypes.END_ROD, mob.getX() + Math.cos(a) * 0.8, mob.getY(0.6) + Math.sin(a * 2.0) * 0.3,
				mob.getZ() + Math.sin(a) * 0.8, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY(0.5), mob.getZ(), 4, 0.5, 0.4, 0.5, 0.02);
	}

	@Override
	public Ultimate ultimate() {
		return new Ultimate(UltimateFilm.TRACK, METEOR_FILM, true, GravityGift::meteor);
	}

	private static boolean meteor(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = foe.position();
		// the streak of it falling, then the blow; it breaks nothing
		for (int i = 0; i <= 24; i++) {
			double y = c.y + METEOR_HEIGHT * (1.0 - i / 24.0), spread = 0.4 + 0.5 * (i / 24.0);
			level.sendParticles(ParticleTypes.FLAME, c.x, y, c.z, 6, spread, spread, spread, 0.05);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, y + 1.0, c.z, 3, spread, spread, spread, 0.03);
		}
		level.sendParticles(WHITE_HOT, c.x, c.y + 1.0, c.z, 10, 0.8, 0.6, 0.8, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1.0, c.z, 2, METEOR_RADIUS * 0.4, 0.5, METEOR_RADIUS * 0.4, 0.0);
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 1.0, c.z, 20, METEOR_RADIUS * 0.4, 0.5, METEOR_RADIUS * 0.4, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y + 1.0, c.z, 40, METEOR_RADIUS * 0.5, 1.0, METEOR_RADIUS * 0.5, 0.0);
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 12.0;
			level.sendParticles(SHADOW, c.x + Math.cos(a) * METEOR_RADIUS * 0.7, c.y + 0.1, c.z + Math.sin(a) * METEOR_RADIUS * 0.7, 1, 0.1, 0.0, 0.1, 0.0);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 4.0F, 0.4F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.NEUTRAL, 3.0F, 0.4F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 3.0F, 0.5F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, foe.getBoundingBox().inflate(METEOR_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy || e.getLastHurtByMob() == caster || caster.getLastHurtByMob() == e))) {
			Vec3 away = e.position().subtract(c).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(METEOR_THROW);
			e.hurtServer(level, level.damageSources().mobAttack(caster), METEOR_DAMAGE);
			push(e, away, METEOR_LIFT);
		}
		return true;
	}
}
