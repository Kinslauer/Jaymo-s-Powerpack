package com.jaymoskitchen.rats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A rat trap's bait: one piece of food (everyone sees it, see the traps' renderers). */
public abstract class BaitTrapEntity extends BlockEntity {
	private ItemStack bait = ItemStack.EMPTY;

	protected BaitTrapEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public ItemStack bait() {
		return bait;
	}

	public boolean baited() {
		return FoodStores.ratFood(bait);
	}

	/** Set and ready to draw rats in. */
	public abstract boolean armed();

	public void setBait(ItemStack stack) {
		bait = stack;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	public ItemStack takeBait() {
		ItemStack taken = bait;
		setBait(ItemStack.EMPTY);
		return taken;
	}

	/** The rat got (or got caught by) the bait. */
	public void eatBait() {
		setBait(ItemStack.EMPTY);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !bait.isEmpty()) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, bait);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		bait = input.read("bait", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!bait.isEmpty()) {
			output.store("bait", ItemStack.OPTIONAL_CODEC, bait);
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		output.store("bait", ItemStack.OPTIONAL_CODEC, bait);
		return output.buildResult();
	}
}
