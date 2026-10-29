package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.wild.WildBeast;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.329) The wild boar, drawn as lukygive's Wild Boar (Blockbench Workshop, CC BY 4.0): a bristling tusker of the
 * forests, taiga and savanna that roots about in small sounders, and lowers its tusks and charges anyone who comes
 * within {@link #TEMPER_RANGE}. It takes Naturalist's boar's place (out of natural spawning and the encounters). Hunted
 * for pork and hide; Jaymo's Kitchen butchers it as any animal.
 */
public class WildBoar extends WildBeast implements Acting {
	static final double HEALTH = 24.0, SPEED = 0.28, DAMAGE = 5.0, ATTACK_KNOCKBACK = 1.2, KNOCKBACK_RESISTANCE = 0.3;
	/** How close a player can come before it charges (blocks). */
	static final double TEMPER_RANGE = 8.0;
	/** Its gore shows this long (ticks). */
	static final int GORE_TICKS = 20;
	public static final byte GORE = 61;

	/** (Client) the tick it last gored. */
	private int goredAt = -1000;

	public WildBoar(EntityType<? extends WildBoar> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE).add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	protected int variants() {
		return 1;
	}

	@Override
	protected double chaseSpeed() {
		return 1.6;
	}

	@Override
	protected boolean hunting(LivingEntity target) {
		return target.distanceTo(this) < TEMPER_RANGE;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			level.broadcastEntityEvent(this, GORE);
		}
		return hit;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == GORE) {
			this.goredAt = this.tickCount;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public @Nullable String action() {
		return this.tickCount - this.goredAt < GORE_TICKS ? "attack" : null;
	}

	@Override
	public int actionAge() {
		return this.tickCount - this.goredAt;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HOGLIN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HOGLIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HOGLIN_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.PIG_STEP.value(), 0.3F, 0.8F);
	}

	@Override
	public float getVoicePitch() {
		return 1.25F;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6F;
	}
}
