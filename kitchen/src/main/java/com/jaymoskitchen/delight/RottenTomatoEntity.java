package com.jaymoskitchen.delight;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * (Farmer's Delight's rotten tomato, MIT; our own code.) A rotten tomato thrown: it does no harm, but splatters on
 * whatever it hits, in a burst of tomato.
 */
public class RottenTomatoEntity extends ThrowableItemProjectile {
	/** The entity event for a splat, and how many bits of tomato it throws up. */
	private static final byte SPLAT_EVENT = 3;
	private static final int SPLAT_PARTICLES = 12;

	public RottenTomatoEntity(EntityType<? extends RottenTomatoEntity> type, Level level) {
		super(type, level);
	}

	public RottenTomatoEntity(Level level, LivingEntity thrower) {
		super(DelightFarm.ROTTEN_TOMATO_ENTITY, thrower, level, DelightFarm.ROTTEN_TOMATO.getDefaultInstance());
	}

	public RottenTomatoEntity(Level level, double x, double y, double z) {
		super(DelightFarm.ROTTEN_TOMATO_ENTITY, x, y, z, level, DelightFarm.ROTTEN_TOMATO.getDefaultInstance());
	}

	@Override
	protected Item getDefaultItem() {
		return DelightFarm.ROTTEN_TOMATO;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == SPLAT_EVENT) {
			ParticleOptions particle = new ItemParticleOption(ParticleTypes.ITEM, getDefaultItem());
			for (int i = 0; i < SPLAT_PARTICLES; ++i) {
				level().addParticle(particle, getX(), getY(), getZ(),
					(random.nextFloat() * 2.0D - 1.0D) * 0.1F,
					(random.nextFloat() * 2.0D - 1.0D) * 0.1F + 0.1F,
					(random.nextFloat() * 2.0D - 1.0D) * 0.1F);
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		Entity entity = result.getEntity();
		entity.hurt(damageSources().thrown(this, getOwner()), 0);
		playSound(DelightFarm.ROTTEN_TOMATO_HIT, 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide()) {
			level().broadcastEntityEvent(this, SPLAT_EVENT);
			playSound(DelightFarm.ROTTEN_TOMATO_HIT, 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
			discard();
		}
	}
}
