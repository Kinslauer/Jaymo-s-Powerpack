package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * Gravitite ore floats: sand in reverse. Whenever it has free space above it, it lifts off as a rising
 * {@link FloatingBlockEntity} and settles back into the world against the first ceiling. Enchanted gravitite floats
 * only while powered by redstone, and lands as an item instead of a block.
 */
public class FloatingBlock extends Block {
	// ---- tuning ----
	/** Ticks between the checks a nudge starts. */
	public static final int DELAY = 2;

	private final boolean powered;

	public FloatingBlock(BlockBehaviour.Properties properties, boolean powered) {
		super(properties);
		this.powered = powered;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		level.scheduleTick(pos, this, DELAY);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		ticks.scheduleTick(pos, this, DELAY);
		return state;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
		boolean movedByPiston) {
		if (this.powered) {
			level.scheduleTick(pos, this, DELAY);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (pos.getY() >= level.getMaxY() || !free(level.getBlockState(pos.above())) || this.powered && !level.hasNeighborSignal(pos)) {
			return;
		}
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		level.addFreshEntity(FloatingBlockEntity.create(level, pos, state, this.powered));
	}

	/** Space a floating block can rise into: air, fire, fluid, plants. */
	public static boolean free(BlockState state) {
		return state.isAir() || state.is(BlockTags.REPLACEABLE);
	}
}
