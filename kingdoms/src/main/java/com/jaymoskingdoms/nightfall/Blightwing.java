package com.jaymoskingdoms.nightfall;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Night Bleeder brought down ({@link NightBleeder}): a big bald bat in the rags of its cloak. It's a coward: it flies
 * from whoever's near, and if it's struck it may turn and dive at them to buy time. Given half a minute it lands and is
 * a Night Bleeder again (at half its strength). Killed, it's dead for good.
 */
public class Blightwing extends Bat {
	static final double HEALTH = 14.0;
	/** How long until it's a Night Bleeder again (ticks), and the share of its health it comes back with. */
	static final int RETURN_TICKS = 20 * 30;
	static final float RETURN_HEALTH = 0.5F;
	/** It flies from anyone within this (blocks); struck, this chance it dives at them, and what the dive does. */
	static final double SHY = 14.0;
	static final float DIVE_CHANCE = 0.45F, DIVE_DAMAGE = 3.0F;

	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Blightwing.class, EntityDataSerializers.INT);

	private int returnIn = RETURN_TICKS;
	private @Nullable LivingEntity diveAt;
	private int diveTicks;

	public Blightwing(EntityType<? extends Blightwing> type, Level level) {
		super(type, level);
		this.setResting(false);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Bat.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ATTACK_DAMAGE, DIVE_DAMAGE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(VARIANT, 0);
	}

	public int variant() {
		return this.entityData.get(VARIANT);
	}

	void setVariant(int variant) {
		this.entityData.set(VARIANT, Math.floorMod(variant, NightBleeder.VARIANTS));
	}

	/** It'll be a Night Bleeder again in this many ticks. */
	void returnIn(int ticks) {
		this.returnIn = ticks;
	}

	@Override
	public boolean isResting() {
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (--this.returnIn <= 0) {
			this.becomeBleeder(level);
			return;
		}
		if (this.diveAt != null) {
			if (!this.diveAt.isAlive() || --this.diveTicks <= 0) {
				this.diveAt = null;
			} else {
				Vec3 to = this.diveAt.getEyePosition().subtract(this.position());
				this.setDeltaMovement(to.normalize().scale(0.75));
				this.lookAt(this.diveAt, 30.0F, 30.0F);
				if (to.length() < 1.3) {
					this.diveAt.hurtServer(level, this.damageSources().mobAttack(this), DIVE_DAMAGE);
					this.diveAt.knockback(0.6, this.getX() - this.diveAt.getX(), this.getZ() - this.diveAt.getZ(), this.damageSources().mobAttack(this), DIVE_DAMAGE);
					this.diveAt = null;
				}
				return;
			}
		}
		Player near = level.getNearestPlayer(this, SHY);
		if (near != null && !near.isSpectator() && !near.isCreative()) {
			// away, and up
			Vec3 away = this.position().subtract(near.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			Vec3 v = this.getDeltaMovement();
			this.setDeltaMovement(v.add((away.x * 0.6 - v.x) * 0.15, (0.35 - v.y) * 0.1, (away.z * 0.6 - v.z) * 0.15));
			this.setYRot((float) (Math.atan2(away.z, away.x) * 180.0 / Math.PI) - 90.0F);
			this.zza = 0.5F;
			return;
		}
		super.customServerAiStep(level);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.isAlive() && source.getEntity() instanceof LivingEntity attacker && this.random.nextFloat() < DIVE_CHANCE) {
			this.diveAt = attacker;
			this.diveTicks = 40;
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 0.8F, 1.4F);
		}
		return hurt;
	}

	/** Lands, and is a Night Bleeder again. */
	private void becomeBleeder(ServerLevel level) {
		NightBleeder bleeder = Nightfall.NIGHT_BLEEDER.create(level, EntitySpawnReason.CONVERSION);
		if (bleeder == null) {
			this.discard();
			return;
		}
		Vec3 at = Nightfall.ground(level, this.position());
		bleeder.snapTo(at.x, at.y, at.z, this.getYRot(), 0.0F);
		bleeder.finalizeSpawn(level, level.getCurrentDifficultyAt(bleeder.blockPosition()), EntitySpawnReason.CONVERSION, null);
		bleeder.setVariant(this.variant());
		bleeder.setHealth(bleeder.getMaxHealth() * RETURN_HEALTH);
		if (this.hasCustomName()) {
			bleeder.setCustomName(this.getCustomName());
		}
		if (this.isPersistenceRequired()) {
			bleeder.setPersistenceRequired();
		}
		level.addFreshEntity(bleeder);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 30, 0.4, 0.8, 0.4, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0F, 0.6F);
		this.discard();
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Variant", this.variant());
		output.putInt("ReturnIn", this.returnIn);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setVariant(input.getIntOr("Variant", 0));
		this.returnIn = input.getIntOr("ReturnIn", RETURN_TICKS);
	}
}
