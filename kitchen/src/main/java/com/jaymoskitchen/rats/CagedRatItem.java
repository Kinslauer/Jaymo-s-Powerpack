package com.jaymoskitchen.rats;

import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A cage trap with a rat shut in it, to carry off and let go somewhere else: it sets down as the cage with the rat. */
public class CagedRatItem extends BlockItem {
	public CagedRatItem(Block cage, Properties properties) {
		super(cage, properties);
	}

	/** The cage's own item stays the block's item (for pick-block and the like). */
	@Override
	public void registerBlocks(Map<Block, Item> map, Item item) {
	}

	@Override
	protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
		BlockState state = super.getPlacementState(context);
		return state == null ? null : state.setValue(CageTrapBlock.CAUGHT, true).setValue(CageTrapBlock.COAT, context.getLevel().getRandom()
			.nextInt(Rat.COATS));
	}
}
