package com.jaymospowers.power.web;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** The Web Shot: a fast, flat-flying ball of web that ensnares what it hits. */
public class WebBall extends ThrowableItemProjectile {
	/** Damage on a hit (it's a snare, not a weapon). */
	public static final float DAMAGE = 1.0F;
	/** Gravity per tick (a snowball is 0.03): it flies nearly flat. */
	private static final double GRAVITY = 0.012;
	/** Despawns after this many ticks in flight. */
	private static final int LIFETIME = 100;

	public WebBall(final EntityType<? extends WebBall> type, final Level level) {
		super(type, level);
	}

	public WebBall(final Level level, final LivingEntity owner, final ItemStack stack) {
		super(WebPower.WEB_BALL, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return WebPower.WEB_BALL_ITEM;
	}

	@Override
	protected double getDefaultGravity() {
		return GRAVITY;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			if (this.tickCount > 1) {
				this.level().addParticle(ParticleTypes.ITEM_COBWEB, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
			}
		} else if (this.tickCount > LIFETIME) {
			this.discard();
		}
	}

	private ParticleOptions particle() {
		ItemStack item = this.getItem();
		return item.isEmpty() ? ParticleTypes.ITEM_COBWEB : new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(item));
	}

	@Override
	public void handleEntityEvent(final @EntityEvent.Value byte id) {
		if (id == 3) {
			ParticleOptions particle = this.particle();
			for (int i = 0; i < 10; i++) {
				this.level().addParticle(particle, this.getX(), this.getY(), this.getZ(),
					(this.random.nextDouble() - 0.5) * 0.15, this.random.nextDouble() * 0.1, (this.random.nextDouble() - 0.5) * 0.15);
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected void onHitEntity(final EntityHitResult hitResult) {
		super.onHitEntity(hitResult);
		Entity entity = hitResult.getEntity();
		if (this.level() instanceof ServerLevel level) {
			entity.hurtServer(level, this.damageSources().thrown(this, this.getOwner()), DAMAGE);
			if (entity instanceof LivingEntity living && living.isAlive()) {
				WebTraps.ensnare(level, living);
			}
		}
	}

	@Override
	protected void onHit(final HitResult hitResult) {
		super.onHit(hitResult);
		if (!this.level().isClientSide()) {
			this.level().broadcastEntityEvent(this, (byte)3);
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.7F, 1.4F);
			this.discard();
		}
	}
}
