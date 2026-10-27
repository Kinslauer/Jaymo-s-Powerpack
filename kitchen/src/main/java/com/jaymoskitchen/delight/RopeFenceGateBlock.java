package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's rope fence gate, MIT; their art, our own code.) A gate of rope: it unties and ties (the sounds of
 * a lead) in place of a wooden gate's creak, through a wood type of its own.
 */
public class RopeFenceGateBlock extends FenceGateBlock {
	/**
	 * Its own kind of "wood": the sounds of a lead being tied and untied. Not registered (that would give it sign
	 * textures to look for); the gate only reads its sounds.
	 */
	private static final WoodType ROPE = new WoodType("jaymoskitchen_rope", BlockSetType.OAK, SoundType.WOOL, SoundType.WOOL,
		SoundEvents.LEAD_TIED, SoundEvents.LEAD_UNTIED);
	private static final VoxelShape Z_SHAPE = Block.box(0.0D, 0.0D, 7.0D, 16.0D, 14.0D, 9.0D);
	private static final VoxelShape X_SHAPE = Block.box(7.0D, 0.0D, 0.0D, 9.0D, 14.0D, 16.0D);
	private static final VoxelShape Z_COLLISION_SHAPE = Block.box(0.0D, 0.0D, 7.0D, 16.0D, 24.0D, 9.0D);
	private static final VoxelShape X_COLLISION_SHAPE = Block.box(7.0D, 0.0D, 0.0D, 9.0D, 24.0D, 16.0D);

	public RopeFenceGateBlock(Properties properties) {
		super(ROPE, properties);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		Direction.Axis axis = direction.getAxis();
		if (state.getValue(FACING).getClockWise().getAxis() != axis) {
			return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
		}
		boolean borderedByWalls = isWall(state) && isWall(level.getBlockState(pos.relative(direction.getOpposite())));
		return state.setValue(IN_WALL, borderedByWalls);
	}

	private boolean isWall(BlockState state) {
		return state.is(BlockTags.WALLS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(OPEN)) {
			return Shapes.empty();
		}
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? Z_COLLISION_SHAPE : X_COLLISION_SHAPE;
	}
}
