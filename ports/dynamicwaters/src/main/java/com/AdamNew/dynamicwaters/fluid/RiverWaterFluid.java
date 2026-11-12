package com.AdamNew.dynamicwaters.fluid;

import com.AdamNew.dynamicwaters.block.RiverWaterBlock;
import com.AdamNew.dynamicwaters.registry.ModBlocks;
import com.AdamNew.dynamicwaters.registry.ModFluids;
import com.AdamNew.dynamicwaters.river.RiverFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.Vec3;

public class RiverWaterFluid extends WaterFluid {
    public static final EnumProperty<RiverFlow> FLOW = EnumProperty.create("flow", RiverFlow.class);

    private static boolean isSameAbove(FluidState fluid, BlockGetter level, BlockPos pos) {
        return fluid.getType().isSame(level.getFluidState(pos.above()).getType());
    }

    @Override
    public boolean isSource(FluidState state) {
        return true;
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return super.isSame(fluid) || fluid == ModFluids.RIVER_WATER.get();
    }


    @Override
    public float getHeight(FluidState fluid, BlockGetter level, BlockPos pos) {
        return isSameAbove(fluid, level, pos) ? 1f : getOwnHeight(fluid);
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return ModBlocks.RIVER_WATER.get().defaultBlockState().setValue(RiverWaterBlock.FLOW, state.getValue(FLOW));
    }

    @Override
    public FluidState getSource(boolean falling) {
        return ModFluids.RIVER_WATER.get().defaultFluidState().setValue(BlockStateProperties.FALLING, false).setValue(FLOW, RiverFlow.NONE);
    }

    @Override
    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        super.createFluidStateDefinition(builder.add(FLOW));
    }

    @Override
    public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState fluid) {
        if (level instanceof LevelReader levelReader) {
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder = levelReader.getBiome(pos);
            if (biomeHolder.is(net.minecraft.tags.BiomeTags.IS_OCEAN)) {
                return Vec3.ZERO;
            }
            if (com.AdamNew.dynamicwaters.Config.isBiomeBlacklisted(biomeHolder)) {
                return Vec3.ZERO;
            }
            net.minecraft.resources.Identifier id = biomeHolder.unwrapKey().map(net.minecraft.resources.ResourceKey::identifier).orElse(null);
            if (id != null && (id.getPath().contains("ocean") || id.getPath().contains("deep_"))) {
                return Vec3.ZERO;
            }
        }

        final RiverFlow flow = fluid.getValue(FLOW);
        Vec3 vector = flow.getVector();
        if (fluid.getValue(FALLING)) {
            final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                cursor.setWithOffset(pos, direction);
                if (isSolidFace(level, cursor, direction) || isSolidFace(level, cursor.above(), direction)) {
                    vector = vector.normalize().add(0.0D, -6.0D, 0.0D);
                    break;
                }
            }
        }
        if (vector.lengthSqr() < 1.0E-4D) {
            return Vec3.ZERO;
        }
        return vector.normalize();
    }

    @Override
    public int getAmount(FluidState state) {
        return 8;
    }

    private RiverFlow getFlowFromDirection(LevelReader level, BlockPos pos, Direction direction) {
        final FluidState adjacentFluid = level.getFluidState(pos.relative(direction));
        if (adjacentFluid.hasProperty(FLOW)) {
            return adjacentFluid.getValue(FLOW);
        }
        return RiverFlow.NONE;
    }
}
