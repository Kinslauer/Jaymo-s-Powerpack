package com.jaymoskingdoms.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * (0.5.55) The feral wolf (after Better Animals Plus): a big, lean grey wolf that hunts in packs of 3-5, goes for
 * players on sight and runs down sheep and rabbits. Six coats: black, snowy, timber, arctic, brown, red (the snowy and
 * arctic ones in the cold).
 */
public class FeralWolf extends WildBeast {
	public static final String[] COATS = {"black", "snowy", "timber", "arctic", "brown", "red"};
	static final double HEALTH = 14.0, SPEED = 0.32, DAMAGE = 5.0;

	public FeralWolf(EntityType<? extends FeralWolf> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Sheep.class, false));
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Rabbit.class, false));
	}

	@Override
	protected int variants() {
		return COATS.length;
	}

	@Override
	protected int variantFor(ServerLevelAccessor level, BlockPos pos) {
		boolean cold = level.getBiome(pos).is(BiomeTags.SPAWNS_SNOW_FOXES) || level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
		return cold ? (this.random.nextBoolean() ? 1 : 3) : new int[] {0, 2, 4, 5}[this.random.nextInt(4)];
	}

	/** A vanilla wolf sound set, grown. */
	static WolfSoundVariant.WolfSoundSet sounds(SoundSet set) {
		return SoundEvents.WOLF_SOUNDS.get(set).adultSounds();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.getTarget() != null ? sounds(SoundSet.ANGRY).growlSound().value() : sounds(SoundSet.BIG).ambientSound().value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return sounds(SoundSet.BIG).hurtSound().value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return sounds(SoundSet.BIG).deathSound().value();
	}

	@Override
	public float getVoicePitch() {
		return 0.8F;
	}
}
