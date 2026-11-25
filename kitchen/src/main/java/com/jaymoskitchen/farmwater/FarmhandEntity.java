package com.jaymoskitchen.farmwater;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** A woken scarecrow's keeping ({@link Farmhand}): the straw it has left to work on, and the chest it carries the harvest to. */
public class FarmhandEntity extends BlockEntity {
	private int straw;
	private @Nullable BlockPos chest;

	public FarmhandEntity(BlockPos pos, BlockState state) {
		super(FarmWater.FARMHAND_ENTITY, pos, state);
	}

	public int straw() {
		return straw;
	}

	public void setStraw(int value) {
		straw = Math.max(0, Math.min(Farmhand.MOST_STRAW, value));
		setChanged();
	}

	public @Nullable BlockPos chest() {
		return chest;
	}

	public void setChest(@Nullable BlockPos pos) {
		chest = pos;
		setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		straw = input.getIntOr("straw", 0);
		chest = input.read("chest", BlockPos.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("straw", straw);
		output.storeNullable("chest", BlockPos.CODEC, chest);
	}
}
