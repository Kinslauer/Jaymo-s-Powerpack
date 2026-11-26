package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A ridden dragon's dive slam (after Dragonforge's, RochStudio): dived hard and hit the ground, it shakes it, throws whoever
 * stands round it outward and hurts them, and roars; then its breath's element marks the ground ({@link WindDragon.Element}):
 * fire sets it alight, frost freezes it, a storm calls down light, acid poisons and stone tosses them up.
 */
final class DragonSlam {
	/** Reach (blocks, plus a share of its width), harm, and how hard it throws. */
	static final double RADIUS = 4.0, RADIUS_PER_WIDTH = 0.8, THROW = 1.4, LIFT = 0.5;
	static final float DAMAGE = 12.0F;
	static final double SHAKE_RANGE = 28.0;
	static final float SHAKE = 2.2F;

	private DragonSlam() {
	}

	static void slam(ServerLevel level, WindDragon dragon, @Nullable LivingEntity rider) {
		Vec3 at = dragon.position();
		double radius = RADIUS + dragon.getBbWidth() * RADIUS_PER_WIDTH;
		WindDragon.Element element = dragon.element();
		level.playSound(null, dragon.blockPosition(), DragonSounds.roar(element).value(), SoundSource.NEUTRAL, 4.0F, 0.9F);
		level.playSound(null, dragon.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1.6F, 0.6F);
		com.jaymoskingdoms.PowersCompat.shakeAround(level, at, SHAKE_RANGE, SHAKE * Math.max(0.6F, dragon.getScale()));
		BlockState under = level.getBlockState(dragon.blockPosition().below());
		if (!under.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.2, at.z, 60, radius * 0.4, 0.2, radius * 0.4, 0.2);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, radius * 0.3, 0.2, radius * 0.3, 0.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 3.0, radius), dragon::spareable)) {
			if (e == rider || e.distanceToSqr(at) > radius * radius) {
				continue;
			}
			Vec3 away = e.position().subtract(at).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 0.01 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			double near = 1.0 - Math.sqrt(e.distanceToSqr(at)) / radius * 0.5;
			if (e.hurtServer(level, dragon.damageSources().mobAttack(dragon), (float)(DAMAGE * near))) {
				double lift = element == WindDragon.Element.EARTH ? LIFT * 2.2 : LIFT;
				e.push(away.x * THROW * near, lift, away.z * THROW * near);
				e.syncVelocity = true;
				mark(level, dragon, e, element);
			}
		}
		ground(level, dragon, radius, element);
	}

	/** What the element does to someone it caught. */
	private static void mark(ServerLevel level, WindDragon dragon, LivingEntity e, WindDragon.Element element) {
		switch (element) {
			case FIRE -> e.igniteForSeconds(5.0F);
			case FROST -> {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2), dragon);
				e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 80));
			}
			case ACID -> e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), dragon);
			case STORM -> {
				LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					// the light and thunder only: the blow is the slam's
					bolt.setVisualOnly(true);
					bolt.snapTo(e.getX(), e.getY(), e.getZ());
					level.addFreshEntity(bolt);
				}
				e.hurtServer(level, dragon.damageSources().lightningBolt(), 4.0F);
			}
			case EARTH -> e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), dragon);
		}
	}

	/** What the element leaves on the ground round it (fire, frosted water, sparks, a sickly haze or dust). */
	private static void ground(ServerLevel level, WindDragon dragon, double radius, WindDragon.Element element) {
		Vec3 at = dragon.position();
		switch (element) {
			case FIRE -> {
				for (int i = 0; i < 10; i++) {
					double a = dragon.getRandom().nextDouble() * Mth.TWO_PI, r = radius * (0.4 + dragon.getRandom().nextDouble() * 0.6);
					BlockPos pos = BlockPos.containing(at.x + Math.cos(a) * r, at.y + 0.5, at.z + Math.sin(a) * r);
					DragonBreath.light(level, pos);
				}
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.4, at.z, 60, radius * 0.4, 0.2, radius * 0.4, 0.08);
			}
			case FROST -> {
				int reach = Mth.ceil(radius);
				BlockPos centre = dragon.blockPosition();
				for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -2, -reach), centre.offset(reach, 1, reach))) {
					if (pos.distSqr(centre) <= radius * radius && level.getBlockState(pos).is(Blocks.WATER)
						&& level.getFluidState(pos).isSource() && level.getBlockState(pos.above()).isAir()) {
						level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
						level.scheduleTick(pos.immutable(), Blocks.FROSTED_ICE, Mth.nextInt(dragon.getRandom(), 60, 120));
					}
				}
				level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 0.5, at.z, 80, radius * 0.4, 0.3, radius * 0.4, 0.05);
			}
			case STORM -> level.sendParticles(Fx.CRACKLE, at.x, at.y + 0.5, at.z, 50, radius * 0.4, 0.3, radius * 0.4, 0.2);
			case ACID -> level.sendParticles(ParticleTypes.SNEEZE, at.x, at.y + 0.4, at.z, 50, radius * 0.4, 0.2, radius * 0.4, 0.03);
			case EARTH -> level.sendParticles(DragonSounds.RUBBLE, at.x, at.y + 0.4, at.z, 50, radius * 0.4, 0.2, radius * 0.4, 0.15);
		}
	}
}
