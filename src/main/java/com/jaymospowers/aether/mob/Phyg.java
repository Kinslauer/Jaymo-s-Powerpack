package com.jaymospowers.aether.mob;

import com.jaymospowers.aether.AetherItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The phyg, the Aether's flying pig: a pig with white feathered wings. Saddle it (a saddle on its back) and ride it:
 * it jumps high, flaps once more in mid-air and glides while jump is held (see {@link WingedAnimal}). It breeds on
 * blue berries.
 */
public class Phyg extends WingedAnimal {
	// ---- tuning ----
	public static final double HEALTH = 10.0, SPEED = 0.25;
	/** Its rider sits this high above its feet (blocks). */
	public static final double SEAT = 0.8;

	public Phyg(EntityType<? extends Phyg> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.SAFE_FALL_DISTANCE, SAFE_FALL);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.25));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.2, stack -> stack.is(AetherItems.BLUE_BERRY), false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	protected double seatHeight() {
		return SEAT;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(AetherItems.BLUE_BERRY);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return AetherMobs.PHYG.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PIG_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PIG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PIG_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.PIG_STEP, 0.15F, 1.0F);
	}
}
