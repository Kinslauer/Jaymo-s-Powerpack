package com.jaymospowers.power.wind;

import com.jaymospowers.core.gifted.GiftKit;
import com.jaymospowers.core.gifted.Gifted;
import java.util.List;
import com.jaymospowers.core.UltimateFilm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.1) Wind for a gifted villager ({@link Gifted}): a gale that throws the foe back, an updraft that lifts them to fall,
 * a burst that clears the ground round them, and a dash away on the wind. Storm-taken (corrupted, Wind's Stormbound),
 * they call lightning down, and the storm crackles round them in the rain. A ruler's ultimate is the Tempest; out of a
 * fight they blow out the fires near them.
 */
final class WindGift implements GiftKit {
	/** The gale: its reach, damage, throw (horizontal, up) and cooldown (ticks). */
	static final double GALE_RANGE = 16.0, GALE_THROW = 1.3, GALE_LIFT = 0.3;
	static final float GALE_DAMAGE = 3.0F;
	static final int GALE_COOLDOWN = 60;
	/** The updraft: its reach, lift and cooldown. */
	static final double UPDRAFT_RANGE = 12.0, UPDRAFT_LIFT = 1.1;
	static final int UPDRAFT_COOLDOWN = 160;
	/** The burst: its radius, throw, damage and cooldown. */
	static final double BURST_RADIUS = 4.0, BURST_THROW = 1.1, BURST_LIFT = 0.4;
	static final float BURST_DAMAGE = 2.0F;
	static final int BURST_COOLDOWN = 90;
	/** The dash: how hard and high they go, and its cooldown. */
	static final double DASH_SPEED = 1.4, DASH_LIFT = 0.45;
	static final int DASH_COOLDOWN = 120;
	/** Storm-taken: lightning's reach, damage and cooldown; the crackle in the rain this often (in passives). */
	static final double THUNDER_RANGE = 20.0;
	static final float THUNDER_DAMAGE = 8.0F;
	static final int THUNDER_COOLDOWN = 200;
	static final float CRACKLE_CHANCE = 0.3F;

	/** A ruler's ultimate, the Tempest: a whirlwind round the foe that throws everything near it high (its radius, lift, damage). */
	static final double TEMPEST_RADIUS = 6.0, TEMPEST_LIFT = 1.6;
	static final float TEMPEST_DAMAGE = 6.0F;
	static final int TEMPEST_FILM = 50;
	/** Everyday: fires this near are blown out. */
	static final int DOUSE_RADIUS = 5;

	static void init() {
		Gifted.register(new WindGift());
	}

	@Override
	public Identifier power() {
		return WindPower.ID;
	}

	@Override
	public List<Move> moves() {
		return List.of(
			Move.corrupt("thunder", Kind.STRIKE, THUNDER_COOLDOWN, 3.0, THUNDER_RANGE, WindGift::thunder),
			Move.of("gale", Kind.STRIKE, GALE_COOLDOWN, 2.0, GALE_RANGE, WindGift::gale),
			Move.of("burst", Kind.CLOSE, BURST_COOLDOWN, 0.0, BURST_RADIUS, WindGift::burst),
			Move.of("updraft", Kind.STRIKE, UPDRAFT_COOLDOWN, 2.0, UPDRAFT_RANGE, WindGift::updraft),
			Move.of("dash", Kind.ESCAPE, DASH_COOLDOWN, 0.0, 5.0, WindGift::dash));
	}

	private static boolean gale(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 from = caster.getEyePosition(), to = foe.getBoundingBox().getCenter();
		Vec3 step = to.subtract(from);
		int steps = Math.max(1, (int)(step.length() * 2.0));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(step.scale((double)i / steps));
			level.sendParticles(ParticleTypes.SMALL_GUST, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		level.sendParticles(ParticleTypes.GUST, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.NEUTRAL, 0.8F, 1.4F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.NEUTRAL, 0.8F, 1.2F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), GALE_DAMAGE);
		WindStaff.push(foe, step.horizontal().normalize().scale(GALE_THROW), GALE_LIFT);
		return true;
	}

	private static boolean updraft(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!foe.onGround()) {
			return false;
		}
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, foe.getX(), foe.getY(), foe.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, foe.getX(), foe.getY() + 0.1, foe.getZ(), 16, 0.4, 0.1, 0.4, 0.08);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.NEUTRAL, 1.0F, 1.2F);
		WindStaff.push(foe, Vec3.ZERO, UPDRAFT_LIFT);
		return true;
	}

	private static boolean burst(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = caster.position();
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y + 0.5, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(BURST_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy))) {
			Vec3 away = e.position().subtract(c).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal() : away;
			e.hurtServer(level, level.damageSources().mobAttack(caster), BURST_DAMAGE);
			WindStaff.push(e, away.normalize().scale(BURST_THROW), BURST_LIFT);
		}
		return true;
	}

	private static boolean dash(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!caster.onGround()) {
			return false;
		}
		Vec3 away = caster.position().subtract(foe.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal().reverse() : away;
		level.sendParticles(ParticleTypes.CLOUD, caster.getX(), caster.getY() + 0.1, caster.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.NEUTRAL, 1.0F, 1.2F);
		WindStaff.push(caster, away.normalize().scale(DASH_SPEED), DASH_LIFT);
		caster.fallDistance = 0.0;
		return true;
	}

	private static boolean thunder(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!level.canSeeSky(foe.blockPosition())) {
			return false;
		}
		bolt(level, foe.position());
		foe.hurtServer(level, level.damageSources().lightningBolt(), THUNDER_DAMAGE);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, caster.getX(), caster.getY(0.7), caster.getZ(), 14, 0.3, 0.5, 0.3, 0.2);
		return true;
	}

	/** A bolt that only looks and sounds like one (it sets no town alight; its harm is the move's own). */
	private static void bolt(ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	@Override
	public void passive(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		if (gift.corrupted() && level.isRainingAt(mob.blockPosition()) && mob.getRandom().nextFloat() < CRACKLE_CHANCE) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY(0.6), mob.getZ(), 4, 0.3, 0.4, 0.3, 0.1);
		}
	}

	@Override
	public void everyday(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		// a breath of wind round them, and any fire close by blown out
		BlockPos at = mob.blockPosition();
		boolean doused = false;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-DOUSE_RADIUS, -2, -DOUSE_RADIUS), at.offset(DOUSE_RADIUS, 3, DOUSE_RADIUS))) {
			if (level.getBlockState(p).getBlock() instanceof BaseFireBlock) {
				level.removeBlock(p, false);
				level.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.02);
				doused = true;
			}
		}
		level.sendParticles(ParticleTypes.SMALL_GUST, mob.getX(), mob.getY(0.5), mob.getZ(), 3, 0.6, 0.4, 0.6, 0.0);
		if (doused) {
			level.playSound(null, at, SoundEvents.BREEZE_WHIRL, SoundSource.NEUTRAL, 0.8F, 1.4F);
			level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
		}
	}

	@Override
	public Ultimate ultimate() {
		return new Ultimate(UltimateFilm.SKYWARD, TEMPEST_FILM, false, WindGift::tempest);
	}

	private static boolean tempest(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = foe.position();
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y + 1.0, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 40; i++) {
			double a = i * Math.PI / 10.0, r = TEMPEST_RADIUS * (i % 10) / 10.0;
			level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * r, c.y + i * 0.15, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.02);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.NEUTRAL, 2.0F, 0.6F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BREEZE_WHIRL, SoundSource.NEUTRAL, 2.0F, 0.6F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, foe.getBoundingBox().inflate(TEMPEST_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy || e.getLastHurtByMob() == caster || caster.getLastHurtByMob() == e))) {
			e.hurtServer(level, level.damageSources().mobAttack(caster), TEMPEST_DAMAGE);
			WindStaff.push(e, Vec3.ZERO, TEMPEST_LIFT);
		}
		if (gift.corrupted()) {
			bolt(level, c);
		}
		return true;
	}

	@Override
	public void corrupted(ServerLevel level, PathfinderMob mob) {
		bolt(level, mob.position());
	}
}
