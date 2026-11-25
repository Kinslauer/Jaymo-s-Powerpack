package com.jaymoskingdoms.harvest;

import com.jaymoskingdoms.Fx;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A rotten pumpkin the Harvest Lich lobs in an arc. Where it lands it bursts: everyone within {@link #BURST_RADIUS} blocks that
 * isn't on the lich's side is hurt, thrown up and sickened with poison.
 */
public final class ThrownRottenPumpkin extends AbstractHurtingProjectile {
	/** Its pull downward each tick, how fast it flies across (blocks per tick) and the arc's longest flight (ticks). */
	static final double GRAVITY = 0.05, SPEED = 0.9;
	static final int LONGEST_FLIGHT = 60, SHORTEST_FLIGHT = 8;
	static final float DAMAGE = 7.0F;
	static final double BURST_RADIUS = 3.5, KNOCKBACK = 0.8, LIFT = 0.45;
	static final int POISON_TICKS = 120;

	public ThrownRottenPumpkin(EntityType<? extends ThrownRottenPumpkin> type, Level level) {
		super(type, level);
	}

	/** A pumpkin from {@code thrower} at {@code from}, arcing to land on {@code to}; false if it is out of reach. */
	static boolean lob(ServerLevel level, LivingEntity thrower, Vec3 from, Vec3 to, double longest) {
		Vec3 flat = new Vec3(to.x - from.x, 0.0, to.z - from.z);
		double dist = flat.length();
		if (dist > longest || dist < 1.0E-3) {
			return false;
		}
		double flight = Math.max(SHORTEST_FLIGHT, Math.min(LONGEST_FLIGHT, dist / SPEED));
		double vy = (to.y - from.y) / flight + GRAVITY * (flight - 1.0) / 2.0;
		ThrownRottenPumpkin pumpkin = new ThrownRottenPumpkin(WickedHarvest.THROWN_PUMPKIN, level);
		pumpkin.setOwner(thrower);
		pumpkin.setPos(from.x, from.y, from.z);
		pumpkin.accelerationPower = 0.0;
		Vec3 along = flat.scale(1.0 / flight);
		pumpkin.setDeltaMovement(along.x, vy, along.z);
		level.addFreshEntity(pumpkin);
		return true;
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
		return ParticleTypes.SPORE_BLOSSOM_AIR;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return !HarvestLich.isOnLichSide(entity) && super.canHitEntity(entity);
	}

	@Override
	public void tick() {
		this.setDeltaMovement(this.getDeltaMovement().add(0.0, -GRAVITY, 0.0));
		super.tick();
		if (this.level() instanceof ServerLevel && this.tickCount > LONGEST_FLIGHT * 2) {
			this.discard();
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			burst(level);
			this.discard();
		}
	}

	private void burst(ServerLevel level) {
		Vec3 at = this.position();
		DamageSource source = this.damageSources().mobProjectile(this, this.getOwner() instanceof LivingEntity owner ? owner : null);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(BURST_RADIUS),
			e -> e.isAlive() && !HarvestLich.isOnLichSide(e) && e.position().distanceToSqr(at) <= BURST_RADIUS * BURST_RADIUS)) {
			target.hurtServer(level, source, DAMAGE);
			Vec3 away = target.position().subtract(at).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 0.0) : away.normalize().scale(KNOCKBACK);
			target.push(away.x, LIFT, away.z);
			target.syncVelocity = true;
			target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0));
		}
		Fx.blast(level, at, (float)BURST_RADIUS * 0.6F, 0x9BB04A);
		level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 0.3, at.z, 24, 1.2, 0.5, 1.2, 0.0);
		level.playSound(null, this.blockPosition(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 1.2F, 0.6F);
	}
}
