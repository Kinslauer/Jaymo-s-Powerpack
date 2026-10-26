package com.jaymospowers.power.web;

import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.core.gifted.GiftKit;
import com.jaymospowers.core.gifted.Gifted;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.3) The web-slinger for a gifted villager ({@link Gifted}): a web shot that binds the foe, a yank that pulls them
 * off their feet, a web-wrapped punch on anyone too close, and a zip up and away. No cobweb block is ever placed (the
 * strands are only drawn). A ruler's ultimate is the Cocoon, which binds the foe for a few seconds; out of a fight they
 * catch falling things on a strand and spin strands that fade.
 */
final class WebGift implements GiftKit {
	/** The shot: reach, damage, how long and how hard it binds (slow), and cooldown (ticks). */
	static final double SHOT_RANGE = 16.0;
	static final float SHOT_DAMAGE = 1.0F;
	static final int SHOT_TICKS = 80, SHOT_SLOW_LEVEL = 3, SHOT_COOLDOWN = 80;
	/** The yank: reach, pull strength, lift, damage and cooldown. */
	static final double YANK_RANGE = 14.0, YANK_PULL = 1.1, YANK_LIFT = 0.4;
	static final float YANK_DAMAGE = 2.0F;
	static final int YANK_COOLDOWN = 120;
	/** The punch: reach, damage, how long it weakens and slows, and cooldown. */
	static final double PUNCH_RANGE = 3.5;
	static final float PUNCH_DAMAGE = 5.0F;
	static final int PUNCH_TICKS = 60, PUNCH_COOLDOWN = 40;
	/** The zip: how hard and high they go, and cooldown. */
	static final double ZIP_SPEED = 1.0, ZIP_LIFT = 0.7;
	static final int ZIP_COOLDOWN = 140;
	/** A ruler's Cocoon: its radius, how long the foe is bound (ticks), how hard (slow level), and the film. */
	static final double COCOON_RADIUS = 4.0;
	static final int COCOON_TICKS = 100, COCOON_SLOW_LEVEL = 6, COCOON_FILM = 40;
	/** Everyday: how far they catch a falling item, how hard they draw it in, the chance of spinning a strand, and its length. */
	static final double CATCH_RADIUS = 6.0, CATCH_PULL = 0.5, STRAND_LENGTH = 3.0;
	static final float STRAND_CHANCE = 0.5F;

	static void init() {
		Gifted.register(new WebGift());
	}

	@Override
	public Identifier power() {
		return WebPower.ID;
	}

	@Override
	public List<Move> moves() {
		return List.of(
			Move.of("yank", Kind.STRIKE, YANK_COOLDOWN, 3.0, YANK_RANGE, WebGift::yank),
			Move.of("shot", Kind.STRIKE, SHOT_COOLDOWN, 2.0, SHOT_RANGE, WebGift::shot),
			Move.of("punch", Kind.CLOSE, PUNCH_COOLDOWN, 0.0, PUNCH_RANGE, WebGift::punch),
			Move.of("zip", Kind.ESCAPE, ZIP_COOLDOWN, 0.0, 5.0, WebGift::zip));
	}

	/** A strand of web drawn from one point to another (particles only). */
	private static void strand(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 step = to.subtract(from);
		int steps = Math.max(1, (int)(step.length() * 2.0));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(step.scale((double)i / steps));
			level.sendParticles(ParticleTypes.ITEM_COBWEB, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	private static void bind(LivingEntity e, PathfinderMob caster, int ticks, int slowLevel) {
		e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, slowLevel), caster);
	}

	private static boolean shot(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 to = foe.getBoundingBox().getCenter();
		strand(level, caster.getEyePosition(), to);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, to.x, to.y, to.z, 16, 0.3, 0.4, 0.3, 0.05);
		level.playSound(null, caster.getX(), caster.getEyeY(), caster.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.8F, 1.4F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.COBWEB_PLACE, SoundSource.NEUTRAL, 1.0F, 0.8F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), SHOT_DAMAGE);
		bind(foe, caster, SHOT_TICKS, SHOT_SLOW_LEVEL);
		return true;
	}

	private static boolean yank(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 toward = caster.position().subtract(foe.position()).horizontal();
		if (toward.lengthSqr() < 1.0E-4) {
			return false;
		}
		strand(level, caster.getEyePosition(), foe.getBoundingBox().getCenter());
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.LEAD_BREAK, SoundSource.NEUTRAL, 1.0F, 0.7F);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.8F, 0.9F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), YANK_DAMAGE);
		Vec3 v = toward.normalize().scale(YANK_PULL);
		foe.setDeltaMovement(v.x, YANK_LIFT, v.z);
		foe.syncVelocity = true;
		return true;
	}

	private static boolean punch(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 to = foe.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.ITEM_COBWEB, to.x, to.y, to.z, 14, 0.3, 0.3, 0.3, 0.08);
		level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 8, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 1.0F, 1.1F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.8F, 1.2F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), PUNCH_DAMAGE);
		foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PUNCH_TICKS, 0), caster);
		bind(foe, caster, PUNCH_TICKS, 1);
		return true;
	}

	private static boolean zip(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 away = caster.position().subtract(foe.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal().reverse() : away;
		away = away.normalize();
		strand(level, caster.getEyePosition(), caster.position().add(away.scale(4.0)).add(0.0, 5.0, 0.0));
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 1.0F, 1.6F);
		Vec3 v = away.scale(ZIP_SPEED);
		caster.setDeltaMovement(v.x, ZIP_LIFT, v.z);
		caster.syncVelocity = true;
		caster.fallDistance = 0.0;
		return true;
	}

	@Override
	public void everyday(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		// a falling item caught on a strand and drawn in; otherwise a strand spun a little way (only drawn, gone at once)
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(CATCH_RADIUS),
			e -> e.isAlive() && !e.onGround() && e.getDeltaMovement().y < -0.1)) {
			Vec3 to = mob.getEyePosition().subtract(item.position());
			strand(level, mob.getEyePosition(), item.position());
			item.setDeltaMovement(to.normalize().scale(CATCH_PULL));
			item.needsSync = true;
			level.playSound(null, item.getX(), item.getY(), item.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 1.8F);
			return;
		}
		if (mob.getRandom().nextFloat() < STRAND_CHANCE) {
			double a = mob.getRandom().nextDouble() * Math.PI * 2.0;
			Vec3 from = mob.position().add(0.0, 1.0, 0.0);
			strand(level, from, from.add(Math.cos(a) * STRAND_LENGTH, 0.5, Math.sin(a) * STRAND_LENGTH));
		}
	}

	@Override
	public Ultimate ultimate() {
		return new Ultimate(UltimateFilm.CHASE, COCOON_FILM, false, WebGift::cocoon);
	}

	private static boolean cocoon(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = foe.position();
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 6.0, y = (i % 12) * foe.getBbHeight() / 12.0;
			Vec3 p = new Vec3(c.x + Math.cos(a) * 0.9, c.y + y, c.z + Math.sin(a) * 0.9);
			strand(level, caster.getEyePosition(), p);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.COBWEB_PLACE, SoundSource.NEUTRAL, 2.0F, 0.5F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.NEUTRAL, 1.5F, 0.6F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, foe.getBoundingBox().inflate(COCOON_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy || e.getLastHurtByMob() == caster || caster.getLastHurtByMob() == e))) {
			bind(e, caster, COCOON_TICKS, COCOON_SLOW_LEVEL);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, COCOON_TICKS, 1), caster);
		}
		return true;
	}
}
