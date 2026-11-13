package com.jaymoskitchen.butchery;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** What stands on a bone stand ({@link BoneStandBlock}): a set of bones, and which of its poses it's in. Drawn with GeckoLib. */
public class BoneStandEntity extends BlockEntity implements GeoBlockEntity {
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private ItemStack bones = ItemStack.EMPTY;
	private int pose;

	public BoneStandEntity(BlockPos pos, BlockState state) {
		super(Skeletons.BONE_STAND_ENTITY, pos, state);
	}

	public ItemStack bones() {
		return bones;
	}

	public Skeletons.@Nullable Kind kind() {
		return bones.isEmpty() ? null : Skeletons.kind(bones);
	}

	public boolean dirty() {
		return !bones.isEmpty() && Skeletons.clean(bones) == Skeletons.Clean.DIRTY;
	}

	/** The pose's animation name. */
	public String poseName() {
		Skeletons.Kind kind = kind();
		return kind == null ? "standing" : kind.poses().get(Math.floorMod(pose, kind.poses().size()));
	}

	InteractionResult standUp(Player player, ItemStack held) {
		if (!bones.isEmpty() || level == null) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		bones = held.copyWithCount(1);
		pose = 0;
		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
		}
		level.playSound(null, worldPosition, SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		markUpdated();
		return InteractionResult.SUCCESS;
	}

	InteractionResult nextPose() {
		Skeletons.Kind kind = kind();
		if (kind == null || level == null) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			pose = (pose + 1) % kind.poses().size();
			level.playSound(null, worldPosition, SoundEvents.BONE_BLOCK_HIT, SoundSource.BLOCKS, 0.8F, 1.1F);
			markUpdated();
		}
		return InteractionResult.SUCCESS;
	}

	InteractionResult takeDown(Player player) {
		if (bones.isEmpty() || level == null) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			player.getInventory().placeItemBackInInventory(bones.copy(), Prediction.SERVER_ONLY);
			bones = ItemStack.EMPTY;
			pose = 0;
			level.playSound(null, worldPosition, SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
			markUpdated();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !bones.isEmpty()) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, bones);
		}
	}

	private void markUpdated() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		bones = input.read("bones", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		pose = input.getIntOr("pose", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!bones.isEmpty()) {
			output.store("bones", ItemStack.CODEC, bones);
		}
		output.putInt("pose", pose);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		saveAdditional(output);
		return output.buildResult();
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<BoneStandEntity>("pose", 6, test -> {
			if (test.animatable().kind() == null) {
				return PlayState.STOP;
			}
			return test.setAndContinue(RawAnimation.begin().thenLoop(test.animatable().poseName()));
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
