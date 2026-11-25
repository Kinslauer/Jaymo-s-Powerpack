package com.jaymoskingdoms.harvest;

import com.jaymoskingdoms.Fx;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The pale green fire a {@link WickedPumpkin} spits: it flies straight, burns whoever it hits and is gone. Drawn only as a trail of light. */
public final class WickedOrb extends AbstractHurtingProjectile {
	static final float DAMAGE = 5.0F;
	static final double SPEED = 0.75;
	static final int LIFE = 100;

	public WickedOrb(EntityType<? extends WickedOrb> type, Level level) {
		super(type, level);
	}

	/** An orb from {@code shooter} at {@code from}, towards {@code dir}. */
	static WickedOrb shoot(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir) {
		WickedOrb orb = new WickedOrb(WickedHarvest.ORB, level);
		orb.setOwner(shooter);
		orb.setPos(from.x, from.y, from.z);
		orb.accelerationPower = 0.0;
		orb.setDeltaMovement(dir.normalize().scale(SPEED));
		level.addFreshEntity(orb);
		return orb;
	}

	@Override
	protected float getInertia() {
		return 1.0F;
	}

	@Override
	protected boolean shouldBurn() {
		return false;
	}

	@Override
	protected ParticleOptions getTrailParticle() {
		return ParticleTypes.SOUL_FIRE_FLAME;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return !HarvestLich.isOnLichSide(entity) && super.canHitEntity(entity);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(Fx.wisp(WickedHarvest.SOUL, 0.5F), this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
			if (this.tickCount > LIFE) {
				this.discard();
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (this.level() instanceof ServerLevel level) {
			DamageSource source = this.damageSources().indirectMagic(this, this.getOwner());
			hit.getEntity().hurtServer(level, source, DAMAGE);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			Fx.blast(level, this.position(), 0.8F, WickedHarvest.SOUL);
			this.discard();
		}
	}
}
