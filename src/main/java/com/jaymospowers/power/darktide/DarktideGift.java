package com.jaymospowers.power.darktide;

import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.core.gifted.GiftKit;
import com.jaymospowers.core.gifted.Gifted;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.3) Darktide for a gifted villager ({@link Gifted}): a lash of dark water, a drowning grip that slows and chokes, a
 * surge of water that throws anyone too close back, and a slip away through the shadows. Water never drowns them, as
 * with {@link DarktidePower}'s users. A ruler's ultimate is the Tidal Surge over the foe; out of a fight they let their
 * dark water run over the fires near them. Darktide's corruption is its own state, never a villager's, so the kit has no
 * corrupted moves, and no water block is ever placed.
 */
final class DarktideGift implements GiftKit {
	/** The dark ink of the player's moves. */
	static final int INK_COLOR = 0x0A1838;
	/** The lash: its reach, damage and cooldown (ticks). */
	static final double LASH_RANGE = 14.0;
	static final float LASH_DAMAGE = 4.0F;
	static final int LASH_COOLDOWN = 50;
	/** The grip: its reach, damage, slow (ticks, level), air taken (ticks of breath) and cooldown. */
	static final double GRIP_RANGE = 10.0;
	static final float GRIP_DAMAGE = 2.0F;
	static final int GRIP_SLOW_TICKS = 80, GRIP_SLOW_LEVEL = 2, GRIP_AIR = 60, GRIP_COOLDOWN = 130;
	/** The surge: its radius, throw (horizontal, up), damage and cooldown. */
	static final double SURGE_RADIUS = 4.0, SURGE_THROW = 1.2, SURGE_LIFT = 0.35;
	static final float SURGE_DAMAGE = 2.0F;
	static final int SURGE_COOLDOWN = 100;
	/** The slip: how hard and high they go, and its cooldown. */
	static final double SLIP_SPEED = 1.5, SLIP_LIFT = 0.25;
	static final int SLIP_COOLDOWN = 120;
	/** A ruler's Tidal Surge: its radius, throw (horizontal, up), damage, slow (ticks) and film (ticks). */
	static final double TIDE_RADIUS = 7.0, TIDE_THROW = 1.0, TIDE_LIFT = 0.8;
	static final float TIDE_DAMAGE = 7.0F;
	static final int TIDE_SLOW_TICKS = 100, TIDE_FILM = 50;
	/** Everyday: fires this near are washed out. */
	static final int WASH_RADIUS = 5;

	static void init() {
		Gifted.register(new DarktideGift());
		// (0.17.103) a Rubber-folk who swallowed one: a dark strike, as a lash of ink
		com.jaymospowers.core.SwallowedPower.register(DarktidePower.ID, 50, DarktideGift::swallowed);
	}

	@Override
	public Identifier power() {
		return DarktidePower.ID;
	}

	@Override
	public List<Move> moves() {
		return List.of(
			Move.of("grip", Kind.STRIKE, GRIP_COOLDOWN, 2.0, GRIP_RANGE, DarktideGift::grip),
			Move.of("lash", Kind.STRIKE, LASH_COOLDOWN, 2.0, LASH_RANGE, DarktideGift::lash),
			Move.of("surge", Kind.CLOSE, SURGE_COOLDOWN, 0.0, SURGE_RADIUS, DarktideGift::surge),
			Move.of("slip", Kind.ESCAPE, SLIP_COOLDOWN, 0.0, 5.0, DarktideGift::slip));
	}

	private static void push(LivingEntity e, Vec3 horizontal, double lift) {
		Vec3 v = e.getDeltaMovement().add(horizontal);
		e.setDeltaMovement(v.x, Math.max(v.y, 0.0) + lift, v.z);
		e.syncVelocity = true;
	}

	private static boolean lash(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 from = caster.getEyePosition(), to = foe.getBoundingBox().getCenter();
		Vec3 step = to.subtract(from);
		int steps = Math.max(1, (int)(step.length() * 2.0));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(step.scale((double)i / steps));
			level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			level.sendParticles(ParticleTypes.FALLING_WATER, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		level.sendParticles(ParticleTypes.SPLASH, to.x, to.y, to.z, 14, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, caster.getX(), caster.getEyeY(), caster.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.8F, 0.9F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.8F, 1.1F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), LASH_DAMAGE);
		return true;
	}

	private static boolean grip(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = foe.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 20, 0.3, 0.5, 0.3, 0.05);
		level.sendParticles(new DustParticleOptions(INK_COLOR, 1.4F), c.x, c.y, c.z, 16, 0.3, 0.5, 0.3, 0.0);
		level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.3, c.z, 10, 0.3, 0.4, 0.3, 0.05);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL, 1.0F, 0.6F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.CONDUIT_AMBIENT_SHORT, SoundSource.NEUTRAL, 0.8F, 0.7F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), GRIP_DAMAGE);
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GRIP_SLOW_TICKS, GRIP_SLOW_LEVEL), caster);
		foe.setAirSupply(Math.max(-19, foe.getAirSupply() - GRIP_AIR));
		return true;
	}

	private static boolean surge(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = caster.position();
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 12.0;
			level.sendParticles(ParticleTypes.SPLASH, c.x + Math.cos(a) * 1.5, c.y + 0.4, c.z + Math.sin(a) * 1.5, 3, 0.1, 0.3, 0.1, 0.2);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.5, c.z, 20, 0.5, 0.3, 0.5, 0.08);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.NEUTRAL, 1.0F, 0.8F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(SURGE_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy))) {
			Vec3 away = e.position().subtract(c).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal() : away;
			e.hurtServer(level, level.damageSources().mobAttack(caster), SURGE_DAMAGE);
			push(e, away.normalize().scale(SURGE_THROW), SURGE_LIFT);
		}
		return true;
	}

	private static boolean slip(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!caster.onGround()) {
			return false;
		}
		Vec3 away = caster.position().subtract(foe.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal().reverse() : away;
		level.sendParticles(ParticleTypes.SQUID_INK, caster.getX(), caster.getY() + 0.2, caster.getZ(), 30, 0.4, 0.1, 0.4, 0.02);
		level.sendParticles(ParticleTypes.SPLASH, caster.getX(), caster.getY() + 0.1, caster.getZ(), 20, 0.4, 0.05, 0.4, 0.1);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL, 0.9F, 1.1F);
		push(caster, away.normalize().scale(SLIP_SPEED), SLIP_LIFT);
		caster.fallDistance = 0.0;
		return true;
	}

	@Override
	public void passive(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		// Darktide users never drown
		if (mob.getAirSupply() < mob.getMaxAirSupply()) {
			mob.setAirSupply(mob.getMaxAirSupply());
		}
	}

	@Override
	public void everyday(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		// dark water running off their hands, and any fire close by washed out
		BlockPos at = mob.blockPosition();
		boolean washed = false;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-WASH_RADIUS, -2, -WASH_RADIUS), at.offset(WASH_RADIUS, 3, WASH_RADIUS))) {
			if (level.getBlockState(p).getBlock() instanceof BaseFireBlock) {
				level.removeBlock(p, false);
				level.sendParticles(ParticleTypes.SPLASH, p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5, 10, 0.25, 0.25, 0.25, 0.1);
				level.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.02);
				washed = true;
			}
		}
		level.sendParticles(ParticleTypes.FALLING_WATER, mob.getX(), mob.getY(0.6), mob.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
		level.sendParticles(new DustParticleOptions(INK_COLOR, 1.0F), mob.getX(), mob.getY(0.6), mob.getZ(), 3, 0.4, 0.3, 0.4, 0.0);
		if (washed) {
			level.playSound(null, at, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.8F, 1.2F);
			level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
		}
	}

	@Override
	public Ultimate ultimate() {
		return new Ultimate(UltimateFilm.CRANE, TIDE_FILM, false, DarktideGift::tide);
	}

	private static boolean tide(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = foe.position();
		for (int i = 0; i < 60; i++) {
			double a = i * Math.PI / 10.0, r = TIDE_RADIUS * (i % 10) / 10.0;
			level.sendParticles(ParticleTypes.SPLASH, c.x + Math.cos(a) * r, c.y + 0.3 + i * 0.05, c.z + Math.sin(a) * r, 3, 0.1, 0.2, 0.1, 0.2);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 1.0, c.z, 80, TIDE_RADIUS * 0.4, 1.0, TIDE_RADIUS * 0.4, 0.1);
		level.sendParticles(ParticleTypes.FALLING_WATER, c.x, c.y + 4.0, c.z, 120, TIDE_RADIUS * 0.4, 0.5, TIDE_RADIUS * 0.4, 0.0);
		level.sendParticles(new DustParticleOptions(INK_COLOR, 2.0F), c.x, c.y + 1.0, c.z, 40, TIDE_RADIUS * 0.4, 1.0, TIDE_RADIUS * 0.4, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.NEUTRAL, 2.0F, 0.5F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.CONDUIT_ACTIVATE, SoundSource.NEUTRAL, 2.0F, 0.6F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, foe.getBoundingBox().inflate(TIDE_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy || e.getLastHurtByMob() == caster || caster.getLastHurtByMob() == e))) {
			Vec3 away = e.position().subtract(c).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(TIDE_THROW);
			e.hurtServer(level, level.damageSources().mobAttack(caster), TIDE_DAMAGE);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TIDE_SLOW_TICKS, 2), caster);
			push(e, away, TIDE_LIFT);
		}
		return true;
	}

	/** (0.17.103) Darktide's R for a Rubber-folk who swallowed it ({@link com.jaymospowers.core.SwallowedPower}): a lash of ink at what they look at. */
	static boolean swallowed(net.minecraft.server.level.ServerPlayer player) {
		LivingEntity foe = com.jaymospowers.core.SwallowedPower.sighted(player, LASH_RANGE);
		if (foe == null) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 from = player.getEyePosition(), to = foe.getBoundingBox().getCenter();
		Vec3 step = to.subtract(from);
		int steps = Math.max(1, (int)(step.length() * 2.0));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(step.scale((double)i / steps));
			level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.sendParticles(ParticleTypes.SPLASH, to.x, to.y, to.z, 14, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.9F);
		foe.hurtServer(level, level.damageSources().playerAttack(player), LASH_DAMAGE * 1.5F);
		return true;
	}
}
