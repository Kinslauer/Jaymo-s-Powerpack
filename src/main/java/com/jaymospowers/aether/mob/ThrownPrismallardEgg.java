package com.jaymospowers.aether.mob;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A thrown prismallard egg: it breaks where it lands, and one egg in {@link #HATCH_ODDS} hatches a chick. */
public class ThrownPrismallardEgg extends ThrowableItemProjectile {
	/** One egg in this many hatches. */
	public static final int HATCH_ODDS = 8;

	public ThrownPrismallardEgg(EntityType<? extends ThrownPrismallardEgg> type, Level level) {
		super(type, level);
	}

	public ThrownPrismallardEgg(Level level, LivingEntity owner, ItemStack stack) {
		super(AetherAnimals.PRISMALLARD_EGG_ENTITY, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return AetherAnimals.PRISMALLARD_EGG;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 3 && !this.getItem().isEmpty()) {
			ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(this.getItem()));
			for (int i = 0; i < 8; i++) {
				this.level().addParticle(particle, this.getX(), this.getY(), this.getZ(), (this.random.nextFloat() - 0.5) * 0.08,
					(this.random.nextFloat() - 0.5) * 0.08, (this.random.nextFloat() - 0.5) * 0.08);
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		hit.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			if (this.random.nextInt(HATCH_ODDS) == 0) {
				Prismallard chick = AetherAnimals.PRISMALLARD.create(level, EntitySpawnReason.TRIGGERED);
				if (chick != null) {
					chick.setAge(-24000);
					chick.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
					level.addFreshEntity(chick);
				}
			}
			level.broadcastEntityEvent(this, (byte)3);
			this.discard();
		}
	}
}
