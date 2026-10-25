package com.jaymoskitchen.delight;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

/** (Farmer's Delight's cabbages, MIT; their art, our own code.) Cabbages: a crop on farmland like wheat. */
public class CabbageCropBlock extends CropBlock {
	public CabbageCropBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return Delight.CABBAGE_SEEDS;
	}
}
