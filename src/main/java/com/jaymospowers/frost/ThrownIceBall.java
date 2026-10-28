package com.jaymospowers.frost;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A thrown {@link Frost#ICE_BALL} (26.4's, thrown by players and Frostbites): a hard lump of ice that hits for
 * {@link #DAMAGE}, less the slower it's going, knocks back like a snowball, and shatters on whatever it meets.
 */
public class ThrownIceBall extends ThrowableItemProjectile {
	// ---- tuning ----
	/** Damage at full speed, and the speed (blocks a tick) that counts as full. */
	public static final float DAMAGE = 4.0F, FULL_SPEED = 1.5F;

	public ThrownIceBall(EntityType<? extends ThrownIceBall> type, Level level) {
		super(type, level);
	}

	public ThrownIceBall(Level level, LivingEntity owner, ItemStack stack) {
		super(Frost.ICE_BALL_ENTITY, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Frost.ICE_BALL;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 3) {
			ItemStack stack = this.getItem().isEmpty() ? new ItemStack(Frost.ICE_BALL) : this.getItem();
			ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(stack));
			for (int i = 0; i < 10; i++) {
				this.level().addParticle(particle, this.getX(), this.getY(), this.getZ(), (this.random.nextFloat() - 0.5) * 0.15,
					this.random.nextFloat() * 0.12, (this.random.nextFloat() - 0.5) * 0.15);
			}
			this.level().addParticle(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.05, 0.0);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		float speed = (float)this.getDeltaMovement().length();
		hit.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), DAMAGE * Math.min(1.0F, speed / FULL_SPEED));
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.5F,
				1.5F + this.random.nextFloat() * 0.3F);
			level.broadcastEntityEvent(this, (byte)3);
			this.discard();
		}
	}
}
