package com.jaymospowers.alchemy.herbs;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;

/**
 * Farmland poisoned with paste (Hunter's Road, patch 7, "T-B1"): made by working a paste ({@code Pastes#PASTES}) into
 * ploughed farmland ({@link Garden}). It waters, dries and is trampled back to dirt like farmland, but only the poison plants
 * ({@link HerbCropBlock}) take root on it; wheat and the rest will not.
 */
public class PoisonedFarmlandBlock extends FarmlandBlock {
	public PoisonedFarmlandBlock(Properties properties) {
		super(Blocks.DIRT, properties);
	}
}
