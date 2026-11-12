package com.AdamNew.dynamicwaters.block;

import com.AdamNew.dynamicwaters.fluid.RiverWaterFluid;
import com.AdamNew.dynamicwaters.river.RiverFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
public class RiverWaterBlock extends LiquidBlock {
    public static final EnumProperty<RiverFlow> FLOW = EnumProperty.create("flow", RiverFlow.class);
    private final FlowingFluid customFluid;

    @SuppressWarnings("deprecation")
    public RiverWaterBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
        this.customFluid = fluid;
        registerDefaultState(getStateDefinition().any().setValue(LEVEL, 0).setValue(FLOW, RiverFlow.NONE));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return this.customFluid.defaultFluidState().setValue(RiverWaterFluid.FLOW, state.getValue(FLOW));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FLOW));
    }
}
