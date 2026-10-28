package com.jaymospowers.power.smoke;

import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.core.gifted.GiftKit;
import com.jaymospowers.core.gifted.Gifted;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.3) Smoke for a gifted villager ({@link Gifted}): a smoke screen that blinds the foe, a choking cloud on them, a
 * hot burst of smoke round anyone too close, and a smoke step: they vanish in smoke and re-form a few blocks away. Smoke
 * never sets anything alight. A ruler's ultimate is the Ash Storm ({@link SmokeAshStorm}'s soot); out of a fight they
 * blow smoke rings and draw the smoke off a campfire.
 */
final class SmokeGift implements GiftKit {
	/** The screen: its reach, how long it blinds, and cooldown (ticks). */
	static final double SCREEN_RANGE = 14.0;
	static final int SCREEN_BLIND = 60, SCREEN_COOLDOWN = 120;
	/** The choke: its reach, damage, how long it weakens and slows, and cooldown. */
	static final double CHOKE_RANGE = 12.0;
	static final float CHOKE_DAMAGE = 3.0F;
	static final int CHOKE_TICKS = 80, CHOKE_COOLDOWN = 100;
	/** The burst: radius, damage, how long it blinds, and cooldown. */
	static final double BURST_RADIUS = 3.5;
	static final float BURST_DAMAGE = 3.0F;
	static final int BURST_BLIND = 40, BURST_COOLDOWN = 80;
	/** The step: how far from where they stood they re-form (blocks), the tries at finding room, and cooldown. */
	static final double STEP_DISTANCE = 6.0;
	static final int STEP_TRIES = 8, STEP_COOLDOWN = 140;
	/** A ruler's Ash Storm: radius, damage, how long it blinds and slows (ticks), and the film. */
	static final double ASH_RADIUS = 8.0;
	static final float ASH_DAMAGE = 4.0F;
	static final int ASH_TICKS = 100, ASH_FILM = 50;
	/** Everyday: how far they look for a campfire to draw smoke off, and the size of a smoke ring (blocks). */
	static final int CAMPFIRE_RADIUS = 5;
	static final double RING_RADIUS = 0.35;

	static void init() {
		Gifted.register(new SmokeGift());
		// (0.17.103) a Rubber-folk who swallowed one: Vapour Form, as a dart of smoke ahead
		com.jaymospowers.core.SwallowedPower.register(SmokePower.ID, 60, SmokeGift::swallowed);
	}

	@Override
	public Identifier power() {
		return SmokePower.ID;
	}

	@Override
	public List<Move> moves() {
		return List.of(
			Move.of("choke", Kind.STRIKE, CHOKE_COOLDOWN, 2.0, CHOKE_RANGE, SmokeGift::choke),
			Move.of("screen", Kind.STRIKE, SCREEN_COOLDOWN, 2.0, SCREEN_RANGE, SmokeGift::screen),
			Move.of("burst", Kind.CLOSE, BURST_COOLDOWN, 0.0, BURST_RADIUS, SmokeGift::burst),
			Move.of("step", Kind.ESCAPE, STEP_COOLDOWN, 0.0, 5.0, SmokeGift::step));
	}

	private static void cloud(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, count, spread, spread * 0.8, spread, 0.02);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, count / 3 + 1, spread, spread * 0.8, spread, 0.01);
	}

	private static boolean screen(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 to = foe.getEyePosition();
		cloud(level, to, 40, 0.7);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.0F, 0.7F);
		foe.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SCREEN_BLIND, 0), caster);
		return true;
	}

	private static boolean choke(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 to = foe.getBoundingBox().getCenter();
		cloud(level, to, 30, 0.5);
		level.sendParticles(ParticleTypes.ASH, to.x, to.y, to.z, 20, 0.5, 0.5, 0.5, 0.02);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_HURT_DROWN, SoundSource.NEUTRAL, 1.0F, 0.8F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), CHOKE_DAMAGE);
		foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CHOKE_TICKS, 0), caster);
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHOKE_TICKS, 1), caster);
		return true;
	}

	private static boolean burst(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = caster.position().add(0.0, caster.getBbHeight() * 0.5, 0.0);
		cloud(level, c, 50, BURST_RADIUS * 0.4);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.5F, 1.6F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(BURST_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy))) {
			e.hurtServer(level, level.damageSources().mobAttack(caster), BURST_DAMAGE);
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BURST_BLIND, 0), caster);
		}
		return true;
	}

	private static boolean step(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 away = caster.position().subtract(foe.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal().reverse() : away;
		away = away.normalize();
		for (int i = 0; i < STEP_TRIES; i++) {
			// straight away from the foe first, then fanning to either side
			double turn = (i % 2 == 0 ? 1 : -1) * (i / 2) * 0.5;
			double cos = Math.cos(turn), sin = Math.sin(turn);
			Vec3 dir = new Vec3(away.x * cos - away.z * sin, 0.0, away.x * sin + away.z * cos);
			Vec3 dest = caster.position().add(dir.scale(STEP_DISTANCE));
			var box = caster.getBoundingBox().move(dest.subtract(caster.position()));
			// room to stand, and ground under it
			if (level.noCollision(caster, box) && !level.noCollision(caster, box.move(0.0, -0.5, 0.0))) {
				cloud(level, caster.position().add(0.0, 1.0, 0.0), 40, 0.5);
				level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.8F, 1.3F);
				caster.teleportTo(dest.x, dest.y, dest.z);
				caster.getNavigation().stop();
				cloud(level, dest.add(0.0, 1.0, 0.0), 40, 0.5);
				level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.BREEZE_JUMP, SoundSource.NEUTRAL, 0.8F, 1.5F);
				return true;
			}
		}
		return false;
	}

	@Override
	public void everyday(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		// the smoke off a campfire close by is drawn to them, the fire itself untouched
		BlockPos at = mob.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-CAMPFIRE_RADIUS, -2, -CAMPFIRE_RADIUS), at.offset(CAMPFIRE_RADIUS, 2, CAMPFIRE_RADIUS))) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
				Vec3 from = new Vec3(p.getX() + 0.5, p.getY() + 0.9, p.getZ() + 0.5), step = mob.getEyePosition().subtract(from);
				for (int i = 1; i <= 8; i++) {
					Vec3 q = from.add(step.scale(i / 8.0));
					level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.0);
				}
				return;
			}
		}
		// otherwise a smoke ring, blown ahead of them
		Vec3 look = mob.getLookAngle().horizontal().normalize();
		Vec3 mouth = mob.getEyePosition().add(look.scale(0.6));
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6.0;
			level.sendParticles(ParticleTypes.SMOKE, mouth.x + Math.cos(a) * RING_RADIUS * -look.z, mouth.y + Math.sin(a) * RING_RADIUS,
				mouth.z + Math.cos(a) * RING_RADIUS * look.x, 0, look.x, 0.0, look.z, 0.1);
		}
		level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.BREEZE_IDLE_AIR, SoundSource.NEUTRAL, 0.4F, 1.6F);
	}

	@Override
	public Ultimate ultimate() {
		return new Ultimate(UltimateFilm.CRANE, ASH_FILM, false, SmokeGift::ashStorm);
	}

	private static boolean ashStorm(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = foe.position();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 2.0, c.z, 160, ASH_RADIUS * 0.5, 1.5, ASH_RADIUS * 0.5, 0.05);
		level.sendParticles(ParticleTypes.ASH, c.x, c.y + 2.0, c.z, 200, ASH_RADIUS * 0.5, 2.0, ASH_RADIUS * 0.5, 0.1);
		level.sendParticles(ParticleTypes.WHITE_ASH, c.x, c.y + 2.0, c.z, 100, ASH_RADIUS * 0.5, 2.0, ASH_RADIUS * 0.5, 0.1);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.5F, 0.5F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BREEZE_WHIRL, SoundSource.NEUTRAL, 2.0F, 0.5F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, foe.getBoundingBox().inflate(ASH_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy || e.getLastHurtByMob() == caster || caster.getLastHurtByMob() == e))) {
			e.hurtServer(level, level.damageSources().mobAttack(caster), ASH_DAMAGE);
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ASH_TICKS, 0), caster);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ASH_TICKS, 1), caster);
		}
		return true;
	}

	/** (0.17.103) Smoke's R for a Rubber-folk who swallowed it ({@link com.jaymospowers.core.SwallowedPower}): they go to smoke and come out ahead. */
	static boolean swallowed(net.minecraft.server.level.ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 ahead = player.getLookAngle().horizontal();
		if (ahead.lengthSqr() < 1.0E-4) {
			return false;
		}
		ahead = ahead.normalize();
		for (double d = STEP_DISTANCE; d >= 2.0; d -= 1.0) {
			Vec3 dest = player.position().add(ahead.scale(d));
			if (level.noCollision(player, player.getBoundingBox().move(dest.subtract(player.position())))) {
				cloud(level, player.position().add(0.0, 1.0, 0.0), 40, 0.5);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.3F);
				player.teleportTo(dest.x, dest.y, dest.z);
				player.resetFallDistance();
				cloud(level, dest.add(0.0, 1.0, 0.0), 40, 0.5);
				return true;
			}
		}
		return false;
	}
}
