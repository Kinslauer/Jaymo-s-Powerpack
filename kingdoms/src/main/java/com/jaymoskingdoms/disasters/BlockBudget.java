package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.Loaded;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One budget of block changes a tick shared by every great disaster (volcanoes, tsunamis, hurricanes, slides, falling
 * stones, quakes, the lightning rods and the world's ending), so however many are going at once the server never changes
 * more than {@link #PER_TICK} blocks for them in a tick, and no one kind of them more than {@link #ONE_MOST}; what
 * doesn't fit waits for the next tick. Only land that is made and loaded is ever touched ({@link Loaded#made}).
 */
public final class BlockBudget {
	/** Block changes a tick, all the great disasters together. */
	static final int PER_TICK = 1200;
	/** Of those, the most a single one may take in a tick, so one can't starve the rest. */
	static final int ONE_MOST = 500;

	/** Who is spending: each has its own {@link #ONE_MOST} a tick out of the shared {@link #PER_TICK}. */
	enum Of {
		VOLCANO, TSUNAMI, HURRICANE, SLIDE, STONE, QUAKE, RODS
	}

	private static int left = PER_TICK;
	private static final int[] USED = new int[Of.values().length];

	private BlockBudget() {
	}

	/** A new tick: the budget fills again. */
	static void refill() {
		left = PER_TICK;
		java.util.Arrays.fill(USED, 0);
	}

	/** How many changes {@code who} may still make this tick. */
	static int share(Of who) {
		return Math.max(0, Math.min(left, ONE_MOST - USED[who.ordinal()]));
	}

	/** Whether there's room for one more change by {@code who} this tick (and takes it). */
	static boolean spend(Of who) {
		if (share(who) <= 0) {
			return false;
		}
		left--;
		USED[who.ordinal()]++;
		return true;
	}

	/** Whether {@code who} has spent all it may this tick. */
	static boolean empty(Of who) {
		return share(who) <= 0;
	}

	/** Sets {@code state} at {@code pos} if it's loaded and the budget allows; whether it did. */
	static boolean set(Of who, ServerLevel level, BlockPos pos, BlockState state) {
		if (!Loaded.made(level, pos) || !spend(who)) {
			return false;
		}
		level.setBlock(pos, state, Block.UPDATE_ALL);
		return true;
	}

	/** Breaks the block at {@code pos} (dropping it or not) if it's loaded and the budget allows; whether it did. */
	static boolean destroy(Of who, ServerLevel level, BlockPos pos, boolean drop) {
		if (!Loaded.made(level, pos) || !spend(who)) {
			return false;
		}
		level.destroyBlock(pos, drop);
		return true;
	}
}
