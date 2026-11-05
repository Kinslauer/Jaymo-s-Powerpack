package com.jaymospowers.aether.sky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.20.96) An upfall: water pouring up off an island's underside, a rare kin of the heavenfalls (The Upfall's idea, our
 * own blocks). A column hangs from the underside; its water runs upward into the rock, and it carries anything in it up
 * with it, slowly, putting out fire. It holds while the block over it is more upfall or the island it pours into.
 */
public class UpfallBlock extends Block {
	static final double LIFT_MOST = 0.35, LIFT_STEP = 0.08;

	public UpfallBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
		return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
		BlockState neighbour, RandomSource random) {
		if (direction == Direction.UP && !neighbour.is(this) && neighbour.isAir()) {
			return Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		Vec3 v = entity.getDeltaMovement();
		entity.setDeltaMovement(v.x * 0.9, Math.min(LIFT_MOST, Math.max(v.y, -0.05) + LIFT_STEP), v.z * 0.9);
		entity.resetFallDistance();
		if (entity.isOnFire()) {
			entity.clearFire();
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.SPLASH, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.3, 0.0);
		}
		if (!level.getBlockState(pos.below()).is(this)) {
			level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + random.nextDouble(), pos.getY(), pos.getZ() + random.nextDouble(), 0.0, 0.05, 0.0);
			if (random.nextInt(40) == 0) {
				level.playLocalSound(pos, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.6F, 1.2F, false);
			}
		}
	}
}
