package com.jaymoskitchen.corpse;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Ash left where a burnt-away corpse lay: 1-8 layers (a zombie a small heap, a ghast or giant a big pile),
 * placed snow-style on the ground where it fell. */
public class AshPileBlock extends Block {
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;

    public AshPileBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }
}
