package com.jaymospowers.tinkering.smeltery;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Seared bricks. (GTNH picks, the forge's dangers) A smeltery's bricks crack with use and in the rain ({@link #CRACKED});
 * a cracked one still holds the wall, but leaks the pool now and then until it's mended with grout and a hammer
 * ({@code tinkering/dangers/Cracks}). Broken out, a cracked brick stays cracked.
 */
public class SearedBricksBlock extends Block {
	public static final BooleanProperty CRACKED = BooleanProperty.create("cracked");

	public SearedBricksBlock(Block.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(CRACKED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CRACKED);
	}
}
