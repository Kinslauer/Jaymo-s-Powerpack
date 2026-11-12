package com.AdamNew.dynamicwaters.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FlowOverrideBlock extends AirBlock {
    public static final IntegerProperty FLOW_DIRECTION = IntegerProperty.create("flow_direction", 0, 15);

    public static final BooleanProperty FLOW_ENABLED = BooleanProperty.create("flow_enabled");

    public FlowOverrideBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FLOW_DIRECTION, 0)
                .setValue(FLOW_ENABLED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLOW_DIRECTION, FLOW_ENABLED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return true;
    }

    @Override
    protected BlockState updateShape(BlockState stateHere, net.minecraft.world.level.LevelReader level,
                                  net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos here, Direction direction,
                                  BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction == Direction.DOWN) {
            if (neighborState.getFluidState().isEmpty()) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
        }
        return stateHere;
    }

    public static Vec3 directionToVec3(int direction) {
        double yawDeg = direction * 22.5;
        double yawRad = Math.toRadians(yawDeg);

        double x = -Math.sin(yawRad);
        double z =  Math.cos(yawRad);
        return new Vec3(x, 0, z);
    }

    public static int vec3ToDirection(double dx, double dz) {
        double yawRad = Math.atan2(-dx, dz);
        double yawDeg = Math.toDegrees(yawRad);
        if (yawDeg < 0) yawDeg += 360.0;
        int segment = (int) Math.round(yawDeg / 22.5) % 16;
        return segment;
    }
}
