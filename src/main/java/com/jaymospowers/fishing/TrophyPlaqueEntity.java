package com.jaymospowers.fishing;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Prediction;
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

/**
 * Track C and D: a trophy plaque (Legendary Fishing's idea, our own code). Hang it on a wall and mount a landed fish on it
 * (any fish that carries its catch: a legend, a personal best, a shark's jaw): it shows the fish, and under it its name,
 * weight, length, who caught it and when. Empty hand takes it down again.
 */
public class TrophyPlaqueEntity extends BlockEntity {
	private ItemStack mounted = ItemStack.EMPTY;

	public TrophyPlaqueEntity(BlockPos pos, BlockState state) {
		super(FishingBlocks.TROPHY_PLAQUE_ENTITY, pos, state);
	}

	public ItemStack mounted() {
		return mounted;
	}

	public FishCatch.@Nullable Info info() {
		return FishCatch.info(mounted);
	}

	InteractionResult useWith(ItemStack stack, Player player) {
		if (FishCatch.info(stack) == null) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level == null || level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!mounted.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.plaque_full").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		mounted = stack.split(1);
		FishCatch.Info info = FishCatch.info(mounted);
		// a mounted fish is a dead one
		mounted.set(FishCatch.CATCH, info.withAliveUntil(0L));
		level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 0.9F);
		changed();
		return InteractionResult.SUCCESS;
	}

	InteractionResult useEmpty(Player player) {
		if (mounted.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (level != null && !level.isClientSide()) {
			player.getInventory().placeItemBackInInventory(mounted, Prediction.SERVER_ONLY);
			mounted = ItemStack.EMPTY;
			level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 0.9F);
			changed();
		}
		return InteractionResult.SUCCESS;
	}

	private void changed() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), mounted);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		mounted = input.read("mounted", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("mounted", ItemStack.OPTIONAL_CODEC, mounted);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		saveAdditional(out);
		return out.buildResult();
	}
}
