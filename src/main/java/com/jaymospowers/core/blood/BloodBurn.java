package com.jaymospowers.core.blood;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where fire scrubs blood: a stain on or next to a fire, lava, a lit campfire or a burning furnace is burned away. The
 * server uses it for its lasting stains ({@link BloodTrail}), the client for the passing blood only it draws.
 */
public final class BloodBurn {
	private BloodBurn() {
	}

	/** Whether {@code pos}, the block above it, or any block beside it burns. */
	public static boolean near(Level level, BlockPos pos) {
		if (burns(level, pos) || burns(level, pos.above())) {
			return true;
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (burns(level, pos.relative(side))) {
				return true;
			}
		}
		return false;
	}

	private static boolean burns(Level level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA) || CampfireBlock.isLitCampfire(state)
			|| state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
				&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
				&& (state.is(net.minecraft.world.level.block.Blocks.FURNACE) || state.is(net.minecraft.world.level.block.Blocks.BLAST_FURNACE)
					|| state.is(net.minecraft.world.level.block.Blocks.SMOKER));
	}
}
