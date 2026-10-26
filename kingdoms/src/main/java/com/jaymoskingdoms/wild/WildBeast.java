package com.jaymoskingdoms.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A wild beast of the encounters ({@link Wild}): an animal (so Jaymo's Kitchen butchers its corpse) that hunts players and (0.7.217)
 * villagers while {@link #hunting} says so (never on Peaceful), calls its own kind in when hurt, and wears one of its looks.
 * They never breed or grow up; they come grown.
 */
public abstract class WildBeast extends Animal implements WildLook {
	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(WildBeast.class, EntityDataSerializers.INT);

	protected WildBeast(EntityType<? extends WildBeast> type, Level level) {
		super(type, level);
	}

	/** How many looks it has. */
	protected abstract int variants();

	/** Its look for where it was born. */
	protected int variantFor(ServerLevelAccessor level, BlockPos pos) {
		return this.random.nextInt(this.variants());
	}

	/** How fast it closes in (a multiple of its speed). */
	protected double chaseSpeed() {
		return 1.2;
	}

	/** Whether it goes for {@code player} on sight. */
	protected boolean hunting(LivingEntity target) {
		return true;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.4F));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, this.chaseSpeed(), true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL && this.hunting(target)));
		// (0.7.217) villagers too, for less ({@link com.jaymoskingdoms.VillagerHunters#VILLAGER_DAMAGE})
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.npc.villager.AbstractVillager.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL && this.hunting(target)));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(VARIANT, 0);
	}

	@Override
	public int variant() {
		return this.entityData.get(VARIANT);
	}

	public void setVariant(int variant) {
		this.entityData.set(VARIANT, Math.floorMod(variant, this.variants()));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		this.setVariant(this.variantFor(level, this.blockPosition()));
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		this.setAge(0);
		return data;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("variant", this.variant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setVariant(input.getIntOr("variant", 0));
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public boolean canFallInLove() {
		return false;
	}
}
