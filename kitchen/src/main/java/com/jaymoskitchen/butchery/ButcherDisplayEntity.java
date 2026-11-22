package com.jaymoskitchen.butchery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
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

/** What's laid out on a butcher's display ({@link ButcherDisplayBlock}): up to {@link #HOLDS} pieces of food, one to a spot. */
public class ButcherDisplayEntity extends BlockEntity {
	/** Spots on the counter top (two rows of two). */
	public static final int HOLDS = 4;
	private final NonNullList<ItemStack> items = NonNullList.withSize(HOLDS, ItemStack.EMPTY);

	public ButcherDisplayEntity(BlockPos pos, BlockState state) {
		super(ButcherShop.BUTCHER_DISPLAY_ENTITY, pos, state);
	}

	public NonNullList<ItemStack> items() {
		return items;
	}

	/** Lays one piece of the held food in the first free spot. PASS: not food. */
	public InteractionResult lay(Player player, ItemStack stack) {
		if (!stack.has(DataComponents.FOOD)) {
			return InteractionResult.PASS;
		}
		int free = -1;
		for (int i = 0; i < HOLDS && free < 0; i++) {
			if (items.get(i).isEmpty()) {
				free = i;
			}
		}
		if (free < 0) {
			return InteractionResult.PASS;
		}
		if (level == null || level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		items.set(free, stack.copyWithCount(1));
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 0.9F);
		markUpdated();
		return InteractionResult.SUCCESS;
	}

	/** Takes back the last piece laid. */
	public InteractionResult takeBack(Player player) {
		for (int i = HOLDS - 1; i >= 0; i--) {
			if (!items.get(i).isEmpty()) {
				if (level == null || level.isClientSide()) {
					return InteractionResult.SUCCESS;
				}
				player.getInventory().placeItemBackInInventory(items.get(i), Prediction.SERVER_ONLY);
				items.set(i, ItemStack.EMPTY);
				level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 0.9F);
				markUpdated();
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropContents(level, pos, items);
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
		items.clear();
		ContainerHelper.loadAllItems(input, items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items, true);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		ContainerHelper.saveAllItems(output, items, true);
		return output.buildResult();
	}
}
