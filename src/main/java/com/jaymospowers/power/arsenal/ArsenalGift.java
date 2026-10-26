package com.jaymospowers.power.arsenal;

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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.1) The Pocket Arsenal for a gifted villager ({@link Gifted}): a blade out of the pocket thrown at the foe, a
 * spear lunge that carries them in, a few blades raining over the foe, and a summoned shield to parry and step back
 * behind. The blades are only drawn (rift light and sparks), never kept: nothing is dropped or left lying about. A ruler's
 * ultimate is the Hundred Blades; out of a fight they turn a summoned blade in the hand a moment and mend their tools a
 * little.
 */
final class ArsenalGift implements GiftKit {
	/** The thrown blade: reach, damage and cooldown (ticks). */
	static final double THROW_RANGE = 16.0;
	static final float THROW_DAMAGE = 4.0F;
	static final int THROW_COOLDOWN = 50;
	/** The lunge: the range it fits, the speed and lift it carries them in with, how close counts as a hit, its damage and cooldown. */
	static final double LUNGE_MIN = 2.5, LUNGE_MAX = 9.0, LUNGE_SPEED = 1.1, LUNGE_LIFT = 0.15, LUNGE_REACH = 3.5;
	static final float LUNGE_DAMAGE = 5.0F;
	static final int LUNGE_COOLDOWN = 100;
	/** The rain: reach, how widely and from how high the blades fall, how many, each one's damage, and the cooldown. */
	static final double RAIN_RANGE = 18.0, RAIN_SPREAD = 1.8, RAIN_HEIGHT = 7.0;
	static final int RAIN_BLADES = 4, RAIN_COOLDOWN = 160;
	static final float RAIN_BLADE_DAMAGE = 2.0F;
	/** The parry: the resistance it gives (ticks, level), the step back it takes, and its cooldown. */
	static final int PARRY_TICKS = 60, PARRY_LEVEL = 1, PARRY_COOLDOWN = 140;
	static final double PARRY_STEP = 0.9, PARRY_LIFT = 0.25;

	/** A ruler's ultimate, the Hundred Blades: the radius they land in, how high the rifts open, streaks drawn, rifts, film (ticks) and damage. */
	static final double HUNDRED_RADIUS = 4.5, HUNDRED_HEIGHT = 11.0;
	static final int HUNDRED_STREAKS = 40, HUNDRED_RIFTS = 5, HUNDRED_FILM = 50;
	static final float HUNDRED_DAMAGE = 12.0F;
	/** Everyday: a tool in hand loses this much wear. */
	static final int MEND_AMOUNT = 6;

	static void init() {
		Gifted.register(new ArsenalGift());
	}

	@Override
	public Identifier power() {
		return ArsenalPower.ID;
	}

	@Override
	public List<Move> moves() {
		return List.of(
			Move.of("rain", Kind.STRIKE, RAIN_COOLDOWN, 3.0, RAIN_RANGE, ArsenalGift::rain),
			Move.of("throw", Kind.STRIKE, THROW_COOLDOWN, 2.5, THROW_RANGE, ArsenalGift::throwBlade),
			Move.of("lunge", Kind.CLOSE, LUNGE_COOLDOWN, LUNGE_MIN, LUNGE_MAX, ArsenalGift::lunge),
			Move.of("parry", Kind.ESCAPE, PARRY_COOLDOWN, 0.0, 5.0, ArsenalGift::parry));
	}

	/** A glinting streak of a blade's flight from {@code from} to {@code to}. */
	private static void streak(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 step = to.subtract(from);
		int steps = Math.max(1, (int)(step.length() * 2.0));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(step.scale((double)i / steps));
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	private static void rift(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 10, 0.3, 0.1, 0.3, 0.1);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 6, 0.3, 0.1, 0.3, 0.03);
	}

	private static boolean throwBlade(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 from = caster.position().add(0.0, caster.getBbHeight() * 0.7, 0.0), to = foe.getBoundingBox().getCenter();
		rift(level, from);
		streak(level, from, to);
		level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 10, 0.2, 0.2, 0.2, 0.3);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.NEUTRAL, 0.8F, 1.4F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.8F, 1.2F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), THROW_DAMAGE);
		return true;
	}

	private static boolean lunge(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!caster.onGround()) {
			return false;
		}
		Vec3 toward = foe.position().subtract(caster.position()).horizontal();
		if (toward.lengthSqr() < 1.0E-4) {
			return false;
		}
		Vec3 from = caster.getEyePosition(), to = foe.getBoundingBox().getCenter();
		streak(level, from, to);
		level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 8, 0.2, 0.2, 0.2, 0.3);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.0F, 0.8F);
		Vec3 v = toward.normalize().scale(LUNGE_SPEED);
		caster.setDeltaMovement(v.x, LUNGE_LIFT, v.z);
		caster.syncVelocity = true;
		if (caster.distanceTo(foe) <= LUNGE_REACH) {
			foe.hurtServer(level, level.damageSources().mobAttack(caster), LUNGE_DAMAGE);
		}
		return true;
	}

	private static boolean rain(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = foe.position();
		double top = c.y + RAIN_HEIGHT;
		for (int i = 0; i < RAIN_BLADES; i++) {
			double a = caster.getRandom().nextDouble() * Math.PI * 2.0, r = Math.sqrt(caster.getRandom().nextDouble()) * RAIN_SPREAD;
			Vec3 land = new Vec3(c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r);
			Vec3 start = new Vec3(land.x, top, land.z);
			rift(level, start);
			streak(level, start, land);
			level.sendParticles(ParticleTypes.CRIT, land.x, land.y, land.z, 4, 0.15, 0.1, 0.15, 0.2);
		}
		level.playSound(null, c.x, top, c.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 0.8F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1.0F, 0.9F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), RAIN_BLADE_DAMAGE * RAIN_BLADES);
		return true;
	}

	private static boolean parry(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 away = caster.position().subtract(foe.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal().reverse() : away;
		Vec3 toward = away.reverse().normalize();
		Vec3 shield = caster.position().add(toward.scale(0.9)).add(0.0, caster.getBbHeight() * 0.55, 0.0);
		Vec3 across = new Vec3(-toward.z, 0.0, toward.x);
		for (int i = -3; i <= 3; i++) {
			for (int j = -2; j <= 2; j++) {
				Vec3 p = shield.add(across.scale(i * 0.2)).add(0.0, j * 0.25, 0.0);
				level.sendParticles(ParticleTypes.ENCHANT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.CRIT, shield.x, shield.y, shield.z, 10, 0.4, 0.4, 0.4, 0.2);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.NEUTRAL, 1.0F, 1.1F);
		caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, PARRY_TICKS, PARRY_LEVEL), caster);
		if (caster.onGround()) {
			Vec3 v = away.normalize().scale(PARRY_STEP);
			caster.setDeltaMovement(v.x, PARRY_LIFT, v.z);
			caster.syncVelocity = true;
		}
		return true;
	}

	@Override
	public void everyday(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		// a blade summoned and turned in the hand a moment
		Vec3 hand = mob.position().add(0.0, mob.getBbHeight() * 0.6, 0.0).add(mob.getLookAngle().horizontal().scale(0.5));
		rift(level, hand);
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6.0;
			level.sendParticles(ParticleTypes.END_ROD, hand.x + Math.cos(a) * 0.35, hand.y + Math.sin(a) * 0.35, hand.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 0.5F, 1.6F);
		// and the tool in hand mended a little
		ItemStack held = mob.getMainHandItem();
		if (!held.isEmpty() && held.isDamaged()) {
			held.setDamageValue(Math.max(0, held.getDamageValue() - MEND_AMOUNT));
			level.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y, hand.z, 6, 0.3, 0.3, 0.3, 0.1);
		}
	}

	@Override
	public Ultimate ultimate() {
		return new Ultimate(UltimateFilm.SKYWARD, HUNDRED_FILM, false, ArsenalGift::hundredBlades);
	}

	private static boolean hundredBlades(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = foe.position();
		double top = c.y + HUNDRED_HEIGHT;
		for (int i = 0; i < HUNDRED_RIFTS; i++) {
			double a = i * Math.PI * 2.0 / HUNDRED_RIFTS, r = i == 0 ? 0.0 : 2.6;
			rift(level, new Vec3(c.x + Math.cos(a) * r, top, c.z + Math.sin(a) * r));
		}
		for (int i = 0; i < HUNDRED_STREAKS; i++) {
			double a = caster.getRandom().nextDouble() * Math.PI * 2.0, r = Math.sqrt(caster.getRandom().nextDouble()) * HUNDRED_RADIUS;
			Vec3 land = new Vec3(c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r);
			streak(level, new Vec3(c.x + Math.cos(a) * 1.5, top, c.z + Math.sin(a) * 1.5), land);
			level.sendParticles(ParticleTypes.CRIT, land.x, land.y, land.z, 2, 0.1, 0.1, 0.1, 0.15);
		}
		level.playSound(null, c.x, top, c.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.NEUTRAL, 1.5F, 1.6F);
		level.playSound(null, c.x, top, c.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 2.0F, 0.5F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 2.0F, 0.6F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, foe.getBoundingBox().inflate(HUNDRED_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy || e.getLastHurtByMob() == caster || caster.getLastHurtByMob() == e))) {
			e.hurtServer(level, level.damageSources().mobAttack(caster), HUNDRED_DAMAGE);
		}
		return true;
	}
}
