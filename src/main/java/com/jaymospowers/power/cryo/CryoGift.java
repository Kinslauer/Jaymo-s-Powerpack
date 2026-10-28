package com.jaymospowers.power.cryo;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.3) Cryokinesis for a gifted villager ({@link Gifted}): an ice shard that slows and chills, frost under the foe's
 * feet, a burst of cold on anyone too close, and an ice wall thrown up between them and a foe to get away (it melts, as
 * all the power's ice does, {@link CryoIce}). Cold never hurts them. A ruler's ultimate is the Blizzard; out of a fight
 * they frost the water near them (the larder's ice).
 */
final class CryoGift implements GiftKit {
	/** The shard: reach, damage, how much it freezes (ticks of frost), its slow and cooldown. */
	static final double SHARD_RANGE = 16.0;
	static final float SHARD_DAMAGE = 4.0F;
	static final int SHARD_FREEZE = 80, SHARD_SLOW_TICKS = 60, SHARD_COOLDOWN = 40;
	/** The frost: reach, slow (ticks, level), freeze and cooldown. */
	static final double FROST_RANGE = 12.0;
	static final int FROST_SLOW_TICKS = 100, FROST_SLOW_LEVEL = 1, FROST_FREEZE = 140, FROST_COOLDOWN = 140;
	/** The burst: radius, damage, slow and cooldown. */
	static final double NOVA_RADIUS = 3.5;
	static final float NOVA_DAMAGE = 3.0F;
	static final int NOVA_SLOW_TICKS = 40, NOVA_SLOW_LEVEL = 2, NOVA_COOLDOWN = 120;
	/** The wall: how far in front of them, its half width and height, how long it stands (ticks), and its cooldown. */
	static final double WALL_AHEAD = 2.0;
	static final int WALL_HALF = 1, WALL_HEIGHT = 2, WALL_TICKS = 100, WALL_COOLDOWN = 200;
	/** A ruler's Blizzard: radius, damage, how long it binds (slow, blind) and freezes. */
	static final double BLIZZARD_RADIUS = 8.0;
	static final float BLIZZARD_DAMAGE = 5.0F;
	static final int BLIZZARD_TICKS = 100, BLIZZARD_FREEZE = 200, BLIZZARD_FILM = 50;
	/** Everyday: water this near can be frosted, for this long (ticks). */
	static final int LARDER_RADIUS = 4, LARDER_TICKS = 600;

	static void init() {
		Gifted.register(new CryoGift());
	}

	@Override
	public Identifier power() {
		return CryoPower.ID;
	}

	@Override
	public List<Move> moves() {
		return List.of(
			Move.of("frost", Kind.STRIKE, FROST_COOLDOWN, 2.0, FROST_RANGE, CryoGift::frost),
			Move.of("shard", Kind.STRIKE, SHARD_COOLDOWN, 2.0, SHARD_RANGE, CryoGift::shard),
			Move.of("nova", Kind.CLOSE, NOVA_COOLDOWN, 0.0, NOVA_RADIUS, CryoGift::nova),
			Move.of("wall", Kind.ESCAPE, WALL_COOLDOWN, 1.5, 6.0, CryoGift::wall));
	}

	private static void chill(LivingEntity e, int freeze) {
		e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + freeze, e.getTicksFrozen() + freeze));
	}

	private static boolean shard(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 from = caster.getEyePosition(), to = foe.getBoundingBox().getCenter();
		Vec3 step = to.subtract(from);
		int steps = Math.max(1, (int)(step.length() * 2.0));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(step.scale((double)i / steps));
			level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.sendParticles(ParticleTypes.ITEM_SNOWBALL, to.x, to.y, to.z, 8, 0.15, 0.15, 0.15, 0.05);
		level.playSound(null, caster.getX(), caster.getEyeY(), caster.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.8F, 0.6F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.7F, 1.6F);
		foe.hurtServer(level, level.damageSources().freeze(), SHARD_DAMAGE);
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHARD_SLOW_TICKS, 0), caster);
		chill(foe, SHARD_FREEZE);
		return true;
	}

	private static boolean frost(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!foe.onGround()) {
			return false;
		}
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI / 8.0;
			level.sendParticles(ParticleTypes.SNOWFLAKE, foe.getX() + Math.cos(a) * 1.2, foe.getY() + 0.1, foe.getZ() + Math.sin(a) * 1.2, 1, 0.05, 0.02, 0.05, 0.01);
		}
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.POWDER_SNOW_STEP, SoundSource.NEUTRAL, 1.0F, 0.6F);
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FROST_SLOW_TICKS, FROST_SLOW_LEVEL), caster);
		chill(foe, FROST_FREEZE);
		return true;
	}

	private static boolean nova(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		level.sendParticles(ParticleTypes.SNOWFLAKE, caster.getX(), caster.getY(0.5), caster.getZ(), 40, NOVA_RADIUS * 0.4, 0.4, NOVA_RADIUS * 0.4, 0.15);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.NEUTRAL, 1.0F, 0.8F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(NOVA_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy))) {
			e.hurtServer(level, level.damageSources().freeze(), NOVA_DAMAGE);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, NOVA_SLOW_TICKS, NOVA_SLOW_LEVEL), caster);
			chill(e, SHARD_FREEZE);
		}
		return true;
	}

	private static boolean wall(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 toward = foe.position().subtract(caster.position()).horizontal();
		if (toward.lengthSqr() < 1.0E-4) {
			return false;
		}
		toward = toward.normalize();
		Vec3 mid = caster.position().add(toward.scale(WALL_AHEAD));
		Vec3 across = new Vec3(-toward.z, 0.0, toward.x);
		int placed = 0;
		for (int w = -WALL_HALF; w <= WALL_HALF; w++) {
			for (int h = 0; h < WALL_HEIGHT; h++) {
				BlockPos p = BlockPos.containing(mid.add(across.scale(w))).above(h);
				if (CryoIce.place(level, p, Blocks.PACKED_ICE.defaultBlockState(), WALL_TICKS, CryoIce.Mode.AIR)) {
					placed++;
				}
			}
		}
		if (placed == 0) {
			return false;
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, mid.x, mid.y + 1.0, mid.z, 20, 0.8, 0.6, 0.8, 0.05);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.NEUTRAL, 1.0F, 0.7F);
		return true;
	}

	@Override
	public Ultimate ultimate() {
		return new Ultimate(UltimateFilm.CRANE, BLIZZARD_FILM, false, CryoGift::blizzard);
	}

	private static boolean blizzard(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = caster.position();
		level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 1.5, c.z, 200, BLIZZARD_RADIUS * 0.5, 1.5, BLIZZARD_RADIUS * 0.5, 0.3);
		level.sendParticles(ParticleTypes.WHITE_ASH, c.x, c.y + 1.5, c.z, 120, BLIZZARD_RADIUS * 0.5, 1.5, BLIZZARD_RADIUS * 0.5, 0.1);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BREEZE_JUMP, SoundSource.WEATHER, 2.0F, 0.3F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.NEUTRAL, 1.5F, 0.5F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(BLIZZARD_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy || e.getLastHurtByMob() == caster || caster.getLastHurtByMob() == e))) {
			e.hurtServer(level, level.damageSources().freeze(), BLIZZARD_DAMAGE);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BLIZZARD_TICKS, 3), caster);
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIZZARD_TICKS / 2, 0), caster);
			chill(e, BLIZZARD_FREEZE);
		}
		return true;
	}

	@Override
	public void passive(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		// cold never hurts them
		if (mob.getTicksFrozen() > 0) {
			mob.setTicksFrozen(0);
		}
	}

	@Override
	public void everyday(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		BlockPos at = mob.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-LARDER_RADIUS, -2, -LARDER_RADIUS), at.offset(LARDER_RADIUS, 1, LARDER_RADIUS))) {
			if (level.getFluidState(p).is(Fluids.WATER) && level.getFluidState(p).isSource() && level.getBlockState(p.above()).isAir()
				&& CryoIce.place(level, p.immutable(), Blocks.ICE.defaultBlockState(), LARDER_TICKS, CryoIce.Mode.WATER)) {
				level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.01);
				level.playSound(null, p, SoundEvents.GLASS_STEP, SoundSource.BLOCKS, 0.5F, 1.4F);
				return;
			}
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, mob.getX(), mob.getY(0.8), mob.getZ(), 4, 0.4, 0.3, 0.4, 0.01);
	}
}
