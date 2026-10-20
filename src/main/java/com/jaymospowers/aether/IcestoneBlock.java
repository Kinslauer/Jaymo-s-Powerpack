package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * Icestone: freezes the water and lava around it. Water (source blocks) within {@link #RADIUS_SQUARED} becomes ice, lava
 * becomes obsidian (flowing lava, cobblestone). It looks when placed and then every {@link #LOOK_EVERY} ticks, so water
 * poured in later freezes too.
 */
public class IcestoneBlock extends Block {
	// ---- tuning ----
	/** Freezes everything within sqrt(8) = about 2.83 blocks. */
	public static final int RADIUS_SQUARED = 8, REACH = 2;
	/** Ticks between looks (a scheduled tick; saved with the world). */
	public static final int LOOK_EVERY = 20;

	public IcestoneBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(this)) {
			level.scheduleTick(pos, this, LOOK_EVERY);
		}
	}

	/** A neighbour changed (water poured next to it, say): look at once (a scheduled tick may already be pending 20 ticks out). */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
		boolean movedByPiston) {
		if (level instanceof ServerLevel server) {
			freeze(server, pos);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		freeze(level, pos);
		level.scheduleTick(pos, this, LOOK_EVERY);
	}

	/** A random tick also looks (in case the scheduled one was lost, say to a world edited outside the game). */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		freeze(level, pos);
	}

	private static void freeze(ServerLevel level, BlockPos center) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-REACH, -REACH, -REACH), center.offset(REACH, REACH, REACH))) {
			if (pos.distSqr(center) > RADIUS_SQUARED) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			BlockState frozen = null;
			if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
				frozen = Blocks.ICE.defaultBlockState();
			} else if (state.is(Blocks.LAVA)) {
				frozen = state.getFluidState().isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
			}
			if (frozen != null) {
				level.setBlockAndUpdate(pos, frozen);
				level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
			}
		}
	}
}
