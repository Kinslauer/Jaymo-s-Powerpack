package com.peterwolf.forestfire.firefighting.hose;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IntakeStrainerBlock extends Block implements SimpleWaterloggedBlock {
   public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
   private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0);

   public IntakeStrainerBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(WATERLOGGED, false));
   }


   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{WATERLOGGED});
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
      boolean inWater = fluid.is(Fluids.WATER);
      return (BlockState)this.defaultBlockState().setValue(WATERLOGGED, inWater);
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   protected FluidState getFluidState(BlockState state) {
      return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
   }
}
