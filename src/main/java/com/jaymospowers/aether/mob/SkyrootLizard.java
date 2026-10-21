package com.jaymospowers.aether.mob;

import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.aether.AetherFlora;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The skyroot lizard: a flat, frilled lizard that shows up when leaves are broken or decay (one time in ten, in the colour
 * of that leaf; see {@link AetherAnimals#lizardFromLeaves}). It never fights and runs from a sprinting player. A stick used
 * on it turns it into a lizard on a stick, a snack.
 */
public class SkyrootLizard extends PathfinderMob {
	// ---- tuning ----
	public static final double HEALTH = 4.0, SPEED = 0.3;
	/** A sprinting player within this many blocks makes it run. */
	public static final float FLEE_RANGE = 8.0F;
	public static final double FLEE_SPEED = 2.2;

	/** The leaf kinds, by variant index (the skins are {@code skyroot_lizard_<name>}). */
	public static final String[] VARIANTS = {"skyroot", "amberoot", "greatboa", "greatoak", "greatroot", "skybirch", "skypine", "skyplane", "wisproot", "wisptop"};

	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(SkyrootLizard.class, EntityDataSerializers.INT);

	public SkyrootLizard(EntityType<? extends SkyrootLizard> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return mobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	private static AttributeSupplier.Builder mobAttributes() {
		return net.minecraft.world.entity.Mob.createMobAttributes();
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, FLEE_SPEED));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, FLEE_RANGE, FLEE_SPEED, FLEE_SPEED, p -> p instanceof Player pl && pl.isSprinting()));
		this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(VARIANT, 0);
	}

	public int variant() {
		return this.entityData.get(VARIANT);
	}

	public void setVariant(int variant) {
		this.entityData.set(VARIANT, Math.floorMod(variant, VARIANTS.length));
	}

	/** The variant of the lizard a leaf block gives, or -1 for a block that gives none. */
	public static int variantOfLeaves(Block block) {
		if (block == AetherBlocks.SKYROOT_LEAVES) {
			return 0;
		}
		Block[] leaves = {null, AetherFlora.AMBEROOT_LEAVES, AetherFlora.GREATBOA_LEAVES, AetherFlora.GREATOAK_LEAVES, AetherFlora.GREATROOT_LEAVES,
			AetherFlora.SKYBIRCH_LEAVES, AetherFlora.SKYPINE_LEAVES, AetherFlora.SKYPLANE_LEAVES, AetherFlora.WISPROOT_LEAVES, AetherFlora.WISPTOP_LEAVES};
		for (int i = 1; i < leaves.length; i++) {
			if (leaves[i] == block) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (AetherAnimals.isStick(held)) {
			if (!this.level().isClientSide()) {
				held.consume(1, player);
				ItemStack snack = new ItemStack(AetherAnimals.SKYROOT_LIZARD_ON_A_STICK);
				if (!player.getInventory().add(snack) && this.level() instanceof ServerLevel level) {
					this.spawnAtLocation(level, snack);
				}
				this.playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.5F);
				this.discard();
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
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
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.FROG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FROG_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.FROG_STEP, 0.1F, 1.4F);
	}
}
