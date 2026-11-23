package com.jaymoskingdoms.inhabitants.fts;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A froglin's vomit (from FrogVomit in From the Shadows Reborn by Sonmok14 and L_Ender, AGPL v3; the logic is ported, the
 * code our own, see licenses/from-the-shadows/). It spits it in an arc: it blinds whoever it hits and hurts them, and
 * where it lands it leaves a small sick cloud. Drawn as a slime ball (the original's own loot), where the original has a
 * model of its own.
 */
public class FrogVomit extends ThrowableItemProjectile {
	/** What a hit does (half hearts; the original's config default is 7), how long it blinds, the cloud's size and life. */
	static final float DAMAGE = 5.0F;
	static final int BLIND_TICKS = 90;
	static final float CLOUD_RADIUS = 2.0F;
	static final int CLOUD_TICKS = 100, CLOUD_POISON_TICKS = 90;

	public FrogVomit(EntityType<? extends FrogVomit> type, Level level) {
		super(type, level);
	}

	FrogVomit(Level level, LivingEntity spitter) {
		super(Fts.FROG_VOMIT, spitter, level, new ItemStack(Items.SLIME_BALL));
	}

	@Override
	protected Item getDefaultItem() {
		return Items.SLIME_BALL;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Entity target = hit.getEntity();
		if (this.level() instanceof ServerLevel level && !(target instanceof Froglin)) {
			Entity owner = this.getOwner();
			boolean hurt = target.hurtServer(level, owner instanceof LivingEntity living ? this.damageSources().mobProjectile(this, living)
				: this.damageSources().thrown(this, owner), DAMAGE);
			if (hurt && target instanceof LivingEntity struck) {
				struck.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS), this);
			}
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			AreaEffectCloud cloud = new AreaEffectCloud(level, this.getX(), this.getY(), this.getZ());
			if (this.getOwner() instanceof LivingEntity owner) {
				cloud.setOwner(owner);
			}
			cloud.setRadius(CLOUD_RADIUS);
			cloud.setRadiusOnUse(-0.5F);
			cloud.setWaitTime(10);
			cloud.setDuration(CLOUD_TICKS);
			cloud.setRadiusPerTick(-CLOUD_RADIUS / CLOUD_TICKS);
			cloud.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS));
			cloud.addEffect(new MobEffectInstance(MobEffects.POISON, CLOUD_POISON_TICKS));
			level.addFreshEntity(cloud);
			level.sendParticles(ParticleTypes.SPIT, this.getX(), this.getY(), this.getZ(), 7, 0.15, 0.15, 0.15, 0.05);
			this.discard();
		}
	}
}
