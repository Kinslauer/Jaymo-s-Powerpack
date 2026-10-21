package com.jaymospowers.aether.monster;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The carrion sprout: a pale stalk with six curved leaves that does nothing by day. At night it is a trap: anything that
 * touches it is held fast, and bitten for 2 every 30 ticks until it lets go of the trigger for a moment.
 */
public class CarrionSprout extends PlantMonster {
	// ---- tuning ----
	public static final double HEALTH = 8.0, BITE = 2.0;
	/** It bites every this many ticks of contact; the trigger resets after this long without contact. */
	public static final int SNAP = 30;
	/** What holds the prey: slowness this strong (0-based), refreshed each tick. */
	public static final int HOLD = 3;

	/** Whether it is armed (night), for its model. */
	private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(CarrionSprout.class, EntityDataSerializers.BOOLEAN);
	/** Set for a moment after a snap, for its model. */
	private static final EntityDataAccessor<Integer> SNAPPED = SynchedEntityData.defineId(CarrionSprout.class, EntityDataSerializers.INT);

	private int held, since;

	public CarrionSprout(EntityType<? extends CarrionSprout> type, Level level) {
		super(type, level, 0, 0, 0);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return plantAttributes(HEALTH, 2.0).add(Attributes.ATTACK_DAMAGE, BITE);
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ARMED, false);
		entityData.define(SNAPPED, 0);
	}

	public boolean armed() {
		return this.entityData.get(ARMED);
	}

	/** Ticks left of the snap (0 when not snapping). */
	public int snapped() {
		return this.entityData.get(SNAPPED);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		boolean armed = !level.isBrightOutside();
		if (armed != this.armed()) {
			this.entityData.set(ARMED, armed);
		}
		if (this.snapped() > 0) {
			this.entityData.set(SNAPPED, this.snapped() - 1);
		}
		if (!armed) {
			this.held = 0;
			return;
		}
		List<LivingEntity> touching = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.05),
			e -> e != this && e.isAlive() && !(e instanceof Enemy) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
		if (touching.isEmpty()) {
			if (++this.since > SNAP) {
				this.held = 0;
			}
			return;
		}
		this.since = 0;
		for (LivingEntity prey : touching) {
			prey.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, HOLD, true, false), this);
		}
		if (++this.held >= SNAP) {
			this.held = 0;
			this.entityData.set(SNAPPED, 8);
			float bite = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
			for (LivingEntity prey : touching) {
				prey.hurtServer(level, this.damageSources().mobAttack(this), bite);
			}
			this.playSound(MonsterSounds.get("block.sweet_berry_bush.pick_berries"), 1.0F, 0.6F);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, this.getX(), this.getY() + 0.6, this.getZ(), 4, 0.3, 0.2, 0.3, 0.1);
		}
	}
}
