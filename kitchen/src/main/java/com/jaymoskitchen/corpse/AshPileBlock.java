package com.jaymoskitchen.corpse;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ash left where a burnt-away corpse lay: 1-8 layers (a zombie a small heap, a ghast or giant a big pile),
 * placed snow-style on the ground where it fell. Shaped like its layers, so it doesn't stand as a full block. */
public class AshPileBlock extends Block {
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;

    public AshPileBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0, 0, 0, 16, state.getValue(LAYERS) * 2, 16);
    }

    /** Like snow: you sink one layer in, and a single layer doesn't stop you at all. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int layers = state.getValue(LAYERS);
        return layers <= 1 ? Shapes.empty() : Block.box(0, 0, 0, 16, (layers - 1) * 2, 16);
    }
}
