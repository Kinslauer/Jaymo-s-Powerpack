package com.jaymoskingdoms.trophies;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** What a trophy stand holds: one item (a stack of one). Breaking the stand drops it. */
public class TrophyStandEntity extends BlockEntity {
	private ItemStack stack = ItemStack.EMPTY;

	public TrophyStandEntity(BlockPos pos, BlockState state) {
		super(Trophies.STAND_ENTITY, pos, state);
	}

	public ItemStack stack() {
		return this.stack;
	}

	public void setStack(ItemStack stack) {
		this.stack = stack;
		this.changed();
	}

	/** Takes the item off (leaving the stand empty). */
	public ItemStack take() {
		ItemStack out = this.stack;
		this.stack = ItemStack.EMPTY;
		this.changed();
		return out;
	}

	private void changed() {
		this.setChanged();
		if (this.level != null) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null) {
			Containers.dropItemStack(this.level, pos.getX(), pos.getY(), pos.getZ(), this.stack);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.stack = input.read("stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("stack", ItemStack.OPTIONAL_CODEC, this.stack);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		this.saveAdditional(out);
		return out.buildResult();
	}
}
