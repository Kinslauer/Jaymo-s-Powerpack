package com.jaymoskingdoms.glowwood.creatures;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A lobbed glob of spores (Sporemaw, Myceliarch): 3 damage on a hit and a small Mycosis cloud where it lands. */
public class SporeGlobEntity extends ThrowableItemProjectile {
	private float damage = 3.0F;

	public SporeGlobEntity(EntityType<? extends SporeGlobEntity> type, Level level) {
		super(type, level);
	}

	/** A glob spat by {@code shooter}, starting at its eyes. */
	public SporeGlobEntity(Level level, LivingEntity shooter) {
		this(GlowCreatures.SPORE_GLOB, level);
		this.setOwner(shooter);
		this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
	}

	public SporeGlobEntity(Level level, double x, double y, double z) {
		this(GlowCreatures.SPORE_GLOB, level);
		this.setPos(x, y, z);
	}

	public void setDamage(float damage) {
		this.damage = damage;
	}

	@Override
	protected Item getDefaultItem() {
		return GlowCreatures.SPORE_GLOB_ITEM;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.05;
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		return super.canHitEntity(target) && !target.is(GlowCreatures.FUNGAL);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide() && this.random.nextInt(2) == 0) {
			this.level().addParticle(GlowFx.spore(), this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		Entity target = result.getEntity();
		if (this.level() instanceof ServerLevel level) {
			target.hurtServer(level, this.damageSources().mobProjectile(this, this.getOwner() instanceof LivingEntity l ? l : null), damage);
		}
		if (target instanceof LivingEntity living) {
			Mycosis.infect(living, 200, 0, this.getOwner());
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (this.level() instanceof ServerLevel level) {
			LivingEntity owner = this.getOwner() instanceof LivingEntity living ? living : null;
			SporeClouds.spawn(level, result.getLocation(), 1.5F, 60, owner,
					Mycosis.instance(200, 0));
			level.playSound(null, this.getX(), this.getY(), this.getZ(), GlowSounds.SPORE_GLOB_SPLAT,
					SoundSource.HOSTILE, 0.8F, 0.9F + this.random.nextFloat() * 0.25F);
			this.discard();
		}
	}
}
