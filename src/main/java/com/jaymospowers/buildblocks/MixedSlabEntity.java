package com.jaymospowers.buildblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** What a {@link MixedSlabBlock} holds: the slab in its lower half and the slab in its upper half, each as the slab's own state. */
public class MixedSlabEntity extends BlockEntity {
	private BlockState bottom = single(Blocks.STONE_SLAB, SlabType.BOTTOM);
	private BlockState top = single(Blocks.STONE_SLAB, SlabType.TOP);

	public MixedSlabEntity(BlockPos pos, BlockState state) {
		super(MixedSlabs.ENTITY, pos, state);
	}

	/** {@code slab} on its own in the given half, dry. */
	static BlockState single(Block slab, SlabType half) {
		BlockState state = slab.defaultBlockState();
		if (state.hasProperty(SlabBlock.TYPE)) {
			state = state.setValue(SlabBlock.TYPE, half);
		}
		return state.hasProperty(SlabBlock.WATERLOGGED) ? state.setValue(SlabBlock.WATERLOGGED, false) : state;
	}

	public BlockState bottom() {
		return bottom;
	}

	public BlockState top() {
		return top;
	}

	/** The half's slab, {@code upper} for the top one. */
	public BlockState half(boolean upper) {
		return upper ? top : bottom;
	}

	public void set(BlockState bottom, BlockState top) {
		this.bottom = bottom;
		this.top = top;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		bottom = input.read("bottom", BlockState.CODEC).orElse(bottom);
		top = input.read("top", BlockState.CODEC).orElse(top);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("bottom", BlockState.CODEC, bottom);
		output.store("top", BlockState.CODEC, top);
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
