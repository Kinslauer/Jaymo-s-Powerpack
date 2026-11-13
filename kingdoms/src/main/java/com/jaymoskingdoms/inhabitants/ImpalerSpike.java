package com.jaymoskingdoms.inhabitants;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * One of the spikes an {@link Impaler} throws off its arm. What it hits is concussed ({@link ImpalerGear#concuss}). It is
 * the Impaler's own spike, grown on its body, not something anyone was carrying, so it breaks where it lands and leaves
 * nothing; the spikes it never threw drop when it dies.
 */
public class ImpalerSpike extends ThrowableItemProjectile {
	/** What a spike does to whatever it hits (half hearts). */
	static final float DAMAGE = 5.0F;

	public ImpalerSpike(EntityType<? extends ImpalerSpike> type, Level level) {
		super(type, level);
	}

	ImpalerSpike(Level level, LivingEntity thrower) {
		super(Inhabitants.IMPALER_SPIKE, thrower, level, new ItemStack(ImpalerGear.IMPALER_SPIKE));
	}

	@Override
	protected Item getDefaultItem() {
		return ImpalerGear.IMPALER_SPIKE;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Entity target = hit.getEntity();
		if (this.level() instanceof ServerLevel level && !(target instanceof Impaler)) {
			Entity owner = this.getOwner();
			boolean hurt = target.hurtServer(level, owner instanceof LivingEntity living ? this.damageSources().mobProjectile(this, living)
				: this.damageSources().thrown(this, owner), DAMAGE);
			if (hurt && target instanceof LivingEntity struck) {
				ImpalerGear.concuss(struck, ImpalerGear.SPIKE_CONCUSSION);
			}
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.POINTED_DRIPSTONE.defaultBlockState()), this.getX(), this.getY(),
				this.getZ(), 8, 0.1, 0.1, 0.1, 0.05);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.HOSTILE, 0.8F, 1.3F);
			this.discard();
		}
	}
}
