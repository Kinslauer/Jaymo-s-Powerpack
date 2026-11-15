package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.Loaded;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One budget of block changes a tick shared by every great disaster (volcanoes, tsunamis, hurricanes, slides, falling
 * stones and the world's ending), so however many are going at once the server never changes more than
 * {@link #PER_TICK} blocks for them in a tick; what doesn't fit waits for the next. Only land that is made and loaded
 * is ever touched ({@link Loaded#made}).
 */
public final class BlockBudget {
	/** Block changes a tick, all the great disasters together. */
	static final int PER_TICK = 1200;
	/** Of those, the most a single one may take in a tick, so one can't starve the rest. */
	static final int ONE_MOST = 500;

	private static int left = PER_TICK;

	private BlockBudget() {
	}

	/** A new tick: the budget fills again. */
	static void refill() {
		left = PER_TICK;
	}

	/** How many changes one disaster may make this tick. */
	static int share() {
		return Math.min(left, ONE_MOST);
	}

	/** Whether there's room for one more change this tick (and takes it). */
	static boolean spend() {
		if (left <= 0) {
			return false;
		}
		left--;
		return true;
	}

	static boolean empty() {
		return left <= 0;
	}

	/** Sets {@code state} at {@code pos} if it's loaded and the budget allows; whether it did. */
	static boolean set(ServerLevel level, BlockPos pos, BlockState state) {
		if (!Loaded.made(level, pos) || !spend()) {
			return false;
		}
		level.setBlock(pos, state, Block.UPDATE_ALL);
		return true;
	}

	/** Breaks the block at {@code pos} (dropping it or not) if it's loaded and the budget allows; whether it did. */
	static boolean destroy(ServerLevel level, BlockPos pos, boolean drop) {
		if (!Loaded.made(level, pos) || !spend()) {
			return false;
		}
		level.destroyBlock(pos, drop);
		return true;
	}
}
