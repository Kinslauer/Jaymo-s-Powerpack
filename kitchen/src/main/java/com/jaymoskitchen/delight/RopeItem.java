package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * (Farmer's Delight's rope, MIT; our own code.) Rope used on a rope adds to the end of the run: the bottom of it, or from
 * the end of a rope in the face you clicked when sneaking.
 */
public class RopeItem extends BlockItem {
	public RopeItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	@Nullable
	public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if (!level.getBlockState(pos).is(getBlock())) {
			return context;
		}
		Direction direction = context.isSecondaryUseActive() ? context.getClickedFace() : Direction.DOWN;
		BlockPos.MutableBlockPos at = pos.mutable().move(direction);
		while (at.getY() >= level.getMinY()) {
			BlockState state = level.getBlockState(at);
			if (!state.is(getBlock())) {
				FluidState fluid = state.getFluidState();
				if (!fluid.is(FluidTags.WATER) && !fluid.isEmpty()) {
					return null;
				}
				if (state.canBeReplaced(context)) {
					return BlockPlaceContext.at(context, at, direction);
				}
				break;
			}
			if (direction != Direction.DOWN) {
				return null;
			}
			at.move(direction);
		}
		return null;
	}

	/** A rope hangs in the air: it needn't be able to stand where it's put. */
	@Override
	protected boolean mustSurvive() {
		return false;
	}
}
