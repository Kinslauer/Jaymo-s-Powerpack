package com.jaymoskingdoms.dragons;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.137) Boreas's frost breath: a cone of freezing mist from its mouth along where it's aimed. Everything alive in the
 * cone (that the dragon can see) takes freeze damage, is slowed and chilled (its frozen ticks, the powder-snow freeze
 * shown on the screen, climb); fires it meets go out (burning creatures, fire blocks, lit campfires); and the still
 * water it meets turns to frosted ice, which melts again by itself in time (vanilla's frosted ice, as Frost Walker
 * leaves). Only the ice obeys the mobGriefing rule, as Notus's fire does ({@link DragonBreath}); a ridden dragon's always
 * freezes. It spares what {@link DragonBreath#spared} spares.
 */
public final class DragonFrost {
	/** How far the mist reaches (blocks) and the cone's half-angle (its cosine). */
	public static final double RANGE = 12.0, CONE_COS = 0.92;
	/** Damage each hit (every {@link #HIT_EVERY} ticks), how long the slowness lasts (ticks) and its level (0 is Slowness I). */
	public static final float DAMAGE = 3.0F;
	public static final int HIT_EVERY = 5, SLOW_TICKS = 80, SLOW_LEVEL = 1;
	/** Frozen ticks added each hit, and how far past the freezing point they may climb (so it holds a few seconds, no more). */
	static final int FREEZE_STEP = 40, FREEZE_HOLD = 100;
	/** Mist and snowflake particles a tick, and how many rays look for fires and water each hit. */
	static final int SNOWFLAKES = 5, MIST = 3, BLOCK_RAYS = 4;
	/** How long (ticks) a patch of frosted ice waits before it starts to melt: at least, and up to this much more. */
	static final int ICE_MIN = 60, ICE_SPREAD = 60;

	private DragonFrost() {
	}

	/**
	 * One tick of breath from {@code mouth} along {@code aim} (a unit vector). {@code tick} paces the hits;
	 * {@code freezeWater} whether the water it meets turns to ice.
	 */
	public static void breathe(ServerLevel level, LivingEntity dragon, Vec3 mouth, Vec3 aim, int tick, boolean freezeWater) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < SNOWFLAKES + MIST; i++) {
			Vec3 d = DragonBreath.spread(aim, random, 0.18);
			double speed = 0.45 + random.nextDouble() * 0.35;
			// (count 0: the particle flies along (dx, dy, dz) times speed)
			level.sendParticles(i < SNOWFLAKES ? ParticleTypes.SNOWFLAKE : ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 0, d.x, d.y, d.z, speed);
		}
		if (tick % 6 == 0) {
			// a cold hiss, now and then a crunch of snow
			level.playSound(null, mouth.x, mouth.y, mouth.z, tick % 12 == 0 ? SoundEvents.SNOW_BREAK : SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.0F,
				1.5F + random.nextFloat() * 0.3F);
		}
		if (tick % HIT_EVERY != 0) {
			return;
		}
		AABB box = new AABB(mouth, mouth).inflate(RANGE);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != dragon && !e.isSpectator())) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(mouth);
			double distance = to.length();
			if (distance > RANGE || distance > 0.5 && to.normalize().dot(aim) < CONE_COS || !DragonBreath.clear(level, dragon, mouth,
				target.getBoundingBox().getCenter())) {
				continue;
			}
			// the mist puts out whatever burns in it, friend or foe
			if (target.isOnFire()) {
				target.clearFire();
			}
			if (DragonBreath.spared(dragon, target)) {
				continue;
			}
			if (target.hurtServer(level, dragon.damageSources().source(DamageTypes.FREEZE, dragon), DAMAGE)) {
				chill(target);
			}
		}
		for (int i = 0; i < BLOCK_RAYS; i++) {
			Vec3 dir = DragonBreath.spread(aim, random, 0.14);
			BlockHitResult hit = level.clip(new ClipContext(mouth, mouth.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, dragon));
			boolean met = hit.getType() == HitResult.Type.BLOCK;
			double reach = met ? hit.getLocation().distanceTo(mouth) : RANGE;
			// fire has no collision, so the ray runs through it: look for it block by block along the way
			for (double d = 1.0; d <= reach + 0.5; d += 1.0) {
				snuff(level, BlockPos.containing(mouth.add(dir.scale(d))));
			}
			if (met && freezeWater) {
				freeze(level, hit.getBlockPos(), random);
			}
		}
	}

	/** Adds to {@code target}'s frozen ticks and slows it (unless it's a creature that can't freeze, a stray or a snow golem). */
	static void chill(LivingEntity target) {
		if (!target.canFreeze()) {
			return;
		}
		target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + FREEZE_HOLD, target.getTicksFrozen() + FREEZE_STEP));
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_LEVEL));
	}

	/** Puts out the fire at {@code pos}: a fire block goes, a lit campfire is doused. */
	static void snuff(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(BlockTags.FIRE)) {
			level.removeBlock(pos, false);
		} else if (CampfireBlock.isLitCampfire(state)) {
			level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
		} else {
			return;
		}
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.2F);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.03);
	}

	/** Turns the still water at {@code pos} and round it (the surface, a 3x3 patch) to frosted ice, which melts again soon. */
	static void freeze(ServerLevel level, BlockPos pos, RandomSource random) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos at = pos.offset(dx, 0, dz);
				BlockState state = level.getBlockState(at);
				if (state.is(Blocks.WATER) && state.getFluidState().isSource() && level.getBlockState(at.above()).isAir()) {
					level.setBlockAndUpdate(at, Blocks.FROSTED_ICE.defaultBlockState());
					level.scheduleTick(at, Blocks.FROSTED_ICE, ICE_MIN + random.nextInt(ICE_SPREAD + 1));
				}
			}
		}
	}
}
