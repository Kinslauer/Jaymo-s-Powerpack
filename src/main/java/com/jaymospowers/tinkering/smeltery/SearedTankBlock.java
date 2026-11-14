package com.jaymospowers.tinkering.smeltery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A seared tank: a faucet or channel over it fills it, a faucet on its side empties it. An empty hand reads it. */
public class SearedTankBlock extends MoltenBlock {
	public SearedTankBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SearedTankEntity(pos, state);
	}
}
