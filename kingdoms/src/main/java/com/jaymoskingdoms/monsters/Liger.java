package com.jaymoskingdoms.monsters;

import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The liger and the tigon (after Mo' Creatures' liger, GPL-3, see {@code licenses/mocreatures}): the rare cross of a lion
 * and a tiger that were raised from cubs by the same keeper ({@code BigCats}). Born tame to that keeper, big, sterile, and
 * as loyal to them as the cubs it came of: it follows, sits when told, and fights whatever hurts its keeper or that its
 * keeper strikes. Fed raw or cooked meat it heals. Never found wild.
 */
public class Liger extends TamableAnimal {
	static final double HEALTH = 44.0, SPEED = 0.34, DAMAGE = 8.0;
	/** A grown one's follow: how near it keeps, how far it comes before it comes to you (blocks). */
	static final float FOLLOW_NEAR = 3.0F, FOLLOW_FAR = 10.0F;
	/** The meat it heals on: a point of health for every {@link #MEAT_HEAL}. */
	static final float MEAT_HEAL = 6.0F;

	private static final EntityDataAccessor<Integer> COAT = SynchedEntityData.defineId(Liger.class, EntityDataSerializers.INT);

	public Liger(EntityType<? extends Liger> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return TamableAnimal.createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.FOLLOW_RANGE, 24.0).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(COAT, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.1, true));
		this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0, FOLLOW_FAR, FOLLOW_NEAR));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
		this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
	}

	/** 0 a liger (a lion's mane on the male), 1 a tigon (without). */
	public int coat() {
		return this.entityData.get(COAT);
	}

	public void setCoat(int coat) {
		this.entityData.set(COAT, coat & 1);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(ItemTags.MEAT);
	}

	@Override
	public boolean canMate(net.minecraft.world.entity.animal.Animal other) {
		return false;
	}

	@Override
	public boolean canFallInLove() {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
		return null;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (this.isTame() && this.isOwnedBy(player)) {
			if (this.isFood(stack) && this.getHealth() < this.getMaxHealth()) {
				if (!this.level().isClientSide()) {
					stack.consume(1, player);
					this.heal(MEAT_HEAL);
				}
				return InteractionResult.SUCCESS;
			}
			if (hand == InteractionHand.MAIN_HAND && stack.isEmpty()) {
				if (!this.level().isClientSide()) {
					this.setOrderedToSit(!this.isOrderedToSit());
					this.getNavigation().stop();
					this.setTarget(null);
				}
				return InteractionResult.SUCCESS;
			}
		} else if (!this.isTame() && this.isFood(stack)) {
			if (!this.level().isClientSide()) {
				stack.consume(1, player);
				if (this.random.nextInt(3) == 0) {
					this.tame(player);
					this.setOrderedToSit(false);
					this.level().broadcastEntityEvent(this, (byte) 7);
				} else {
					this.level().broadcastEntityEvent(this, (byte) 6);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		this.setCoat(this.random.nextInt(2));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.OCELOT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.OCELOT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.OCELOT_DEATH;
	}

	@Override
	public void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("coat", this.coat());
	}

	@Override
	public void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setCoat(input.getIntOr("coat", 0));
	}
}
