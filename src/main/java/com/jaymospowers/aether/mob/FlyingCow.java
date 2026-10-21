package com.jaymospowers.aether.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The flying cow: a cow with feathered wings, ridden like the phyg: with valkyrie wings in hand, or saddled
 * ({@link WingedAnimal}). A bucket milks it; it breeds on oranges and blue berries.
 */
public class FlyingCow extends WingedAnimal {
	// ---- tuning ----
	public static final double HEALTH = 10.0, SPEED = 0.2;
	/** Its rider sits this high above its feet (blocks). */
	public static final double SEAT = 1.1;
	/** A saddled flying cow carries its rider at this share of its speed (Aether II's 0.7). */
	public static final float RIDE_SPEED = 0.7F;

	public FlyingCow(EntityType<? extends FlyingCow> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createAnimalAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.SAFE_FALL_DISTANCE, SAFE_FALL);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new SpookPanicGoal(2.0));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.2, AetherAnimals::isAnimalFood, false));
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
	protected float rideSpeed() {
		return RIDE_SPEED;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.BUCKET) && !this.isBaby()) {
			player.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.MILK_BUCKET)));
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return AetherAnimals.isAnimalFood(stack);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return AetherMobs.FLYING_COW.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return MobSounds.get("entity.cow.ambient");
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return MobSounds.get("entity.cow.hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MobSounds.get("entity.cow.death");
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(MobSounds.get("entity.cow.step"), 0.15F, 1.0F);
	}
}
