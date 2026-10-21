package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Purple aercloud: has a facing (the way you looked when you placed it) and flings whatever is inside it two blocks a
 * tick along that way. Sneaking settles into it like a cold cloud.
 */
public class PurpleAercloudBlock extends AercloudBlock {
	// ---- tuning ----
	/** Speed (blocks a tick) of the fling. */
	public static final double FLING_SPEED = 2.0;

	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	public PurpleAercloudBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (entity.isShiftKeyDown() || entity instanceof Projectile) {
			this.settle(entity);
			return;
		}
		Direction way = state.getValue(FACING);
		entity.fallDistance = 0;
		entity.setDeltaMovement(way.getStepX() * FLING_SPEED, way.getStepY() * FLING_SPEED, way.getStepZ() * FLING_SPEED);
		entity.setOnGround(false);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) == 0) {
			Direction way = state.getValue(FACING);
			level.addParticle(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				way.getStepX() * 0.15, way.getStepY() * 0.15, way.getStepZ() * 0.15);
		}
	}
}
