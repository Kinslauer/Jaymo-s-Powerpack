package com.jaymoskitchen.delight;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** (Farmer's Delight's mushroom colonies, MIT; our own code.) A colony set down is a fully grown one. */
public class MushroomColonyItem extends BlockItem {
	public MushroomColonyItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	@Nullable
	protected BlockState getPlacementState(BlockPlaceContext context) {
		BlockState original = getBlock().getStateForPlacement(context);
		if (original != null) {
			BlockState mature = original.setValue(MushroomColonyBlock.COLONY_AGE, 3);
			return canPlace(context, mature) ? mature : null;
		}
		return null;
	}
}
