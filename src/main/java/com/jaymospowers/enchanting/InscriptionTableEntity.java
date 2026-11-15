package com.jaymospowers.enchanting;

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

/** Medieval picks (me-inscribe): what lies on an Inscription Table: the gear being carved, and the gem beside it. */
public class InscriptionTableEntity extends BlockEntity {
	private ItemStack gear = ItemStack.EMPTY, gem = ItemStack.EMPTY;

	public InscriptionTableEntity(BlockPos pos, BlockState state) {
		super(Inscription.ENTITY, pos, state);
	}

	public ItemStack gear() {
		return gear;
	}

	public ItemStack gem() {
		return gem;
	}

	void set(ItemStack gear, ItemStack gem) {
		this.gear = gear;
		this.gem = gem;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), gear);
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), gem);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		gear = input.read("gear", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		gem = input.read("gem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("gear", ItemStack.OPTIONAL_CODEC, gear);
		output.store("gem", ItemStack.OPTIONAL_CODEC, gem);
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
