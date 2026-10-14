package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Torches;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** (0.7.17) Torches (floor and wall) take random ticks, on which they burn down ({@link Torches}). */
@Mixin(TorchBlock.class)
abstract class TorchBlockMixin extends BaseTorchBlock {
	protected TorchBlockMixin(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return Torches.burns(state) || super.isRandomlyTicking(state);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (Torches.burns(state)) {
			Torches.randomTick(state, level, pos, random);
		} else {
			super.randomTick(state, level, pos, random);
		}
	}
}
