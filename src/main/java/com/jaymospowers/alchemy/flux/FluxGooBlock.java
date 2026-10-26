package com.jaymospowers.alchemy.flux;

import com.jaymospowers.alchemy.Corruption;
import com.jaymospowers.alchemy.Flux;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A puddle of Flux Goo: a thin layer that drags at whoever wades through it and soaks a little fleeting corruption into
 * them, then, after a while, evaporates back into its chunk as flux.
 */
public class FluxGooBlock extends Block {
	// ---- tuning ----
	/** Share of normal speed a creature keeps in it. */
	public static final double DRAG = 0.55;
	/** Fleeting corruption a player gets each second spent in it. */
	public static final int SOAK = 1;
	/** One random tick in this many dries it up; and the flux it gives back to the chunk. */
	public static final int EVAPORATE_ODDS = 3, EVAPORATES_TO = 12;

	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

	public FluxGooBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		return this.canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean intersects) {
		entity.makeStuckInBlock(state, new Vec3(DRAG, 1.0, DRAG));
		if (entity instanceof ServerPlayer player && player.tickCount % 20 == 0) {
			Corruption.add(player, Corruption.Kind.FLEETING, SOAK);
		}
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(EVAPORATE_ODDS) == 0) {
			level.removeBlock(pos, false);
			Flux.add(level, pos, EVAPORATES_TO);
			level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 4, 0.3, 0.05, 0.3, 0.01);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(10) == 0) {
			level.addParticle(ParticleTypes.WITCH, pos.getX() + random.nextDouble(), pos.getY() + 0.15, pos.getZ() + random.nextDouble(), 0.0, 0.02, 0.0);
		}
	}
}
