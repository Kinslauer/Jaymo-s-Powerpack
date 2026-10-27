package com.jaymoskitchen.delight;

import com.jaymoskitchen.cooking.Quality;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A cooked feast (or, 0.5.29, pie or rice roll medley) in hand: set down, it keeps the quality it was cooked to (see
 * {@link FeastBlock#QUALITY}, which they all share).
 */
public class FeastItem extends BlockItem {
	public FeastItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
		BlockState state = super.getPlacementState(context);
		if (state == null) {
			return null;
		}
		Quality quality = Quality.of(context.getItemInHand());
		return state.setValue(FeastBlock.QUALITY, (quality == null ? Quality.GOOD : quality).ordinal());
	}
}
