package com.jaymospowers.aether.cloudsea;

import com.jaymospowers.aether.AercloudBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cloudbank: what the Cloud Sea ({@link CloudSea}) is made of. A cold aercloud in every way but one (you sink into a single
 * layer and stand in it, two deep is solid, no fall hurts on it): daylight goes straight down through it. So the sky under
 * the overcast is as bright as the sky over it, and a chunk that is nothing but air and cloud has no shadows to work out.
 */
public class CloudbankBlock extends AercloudBlock {
	public CloudbankBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}
}
