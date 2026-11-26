package com.jaymoskingdoms;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Andy (2026-11-20, a clip of a burnt-out birch wood full of logs hanging in the air): "trees shouldnt float anymore,
 * any natural wood block without support should "fell" or fall like sand if it can't tie into our tree physics".
 * <p>
 * Whenever something that held wood up goes (a log burned, blown, broken or chopped out, or the ground dug from under
 * a trunk, by anyone or anything), the wood next to it is looked at once the tick is over: each piece of logs joined to
 * one another (corners too) that touches nothing solid but other wood and leaves comes down. A piece that's a tree (it
 * has leaves left, or more than {@link #LOOSE_LOGS} logs) drops as a falling tree ({@link FallingTree#drop}), tumbling to
 * its heavier side, crashing and striking what it lands on; a few bare logs fall as loose blocks do, like sand, and land
 * as logs.
 * <p>
 * Builds never come down: a piece with any log a player placed ({@link Placed}) or any stripped log is left as it is,
 * and logs in a building touch its planks, stone or earth, which hold them. Leaves, vines, fire, cocoa pods and bee
 * nests hold nothing up. A piece too big to look through ({@link #LOOK_MOST}), or running into land that isn't loaded,
 * is left standing.
 */
public final class LooseWood {
	/** The most logs looked through for one piece; a bigger one is taken as held (a great tree, or a building). */
	static final int LOOK_MOST = Trees.FELL_MOST;
	/** A bare piece (no leaves, no nest) of this many logs or fewer falls like sand; a bigger one comes down as a tree. */
	static final int LOOSE_LOGS = 3;
	/** The most changed places looked round in a tick, per world (the rest wait for the next tick). */
	static final int CHECKS_PER_TICK = 128;
	/** A log falling like sand strikes what it lands on for this much a block it fell, at most this much. */
	static final float HURT_PER_BLOCK = 1.0F;
	static final int HURT_MOST = 8;

	/** The most block changes from other threads (world generation) waiting to be looked at; the rest are dropped. */
	static final int OFF_THREAD_MOST = 4096;

	/** A block change seen off the server thread: what the block was, and where. */
	private record Pending(ServerLevel level, long pos, BlockState was) {
	}

	/** Block changes made off the server thread (the world generating), looked at on the server thread next tick. */
	private static final ConcurrentLinkedQueue<Pending> OFF_THREAD = new ConcurrentLinkedQueue<>();
	private static final AtomicInteger OFF_THREAD_SIZE = new AtomicInteger();
	private static volatile boolean warnedFull;

	/** Per world: where something that held wood up has gone since they were last looked at. */
	private static final Map<ServerLevel, LongLinkedOpenHashSet> CHANGED = new IdentityHashMap<>();

	private LooseWood() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LooseWood::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHANGED.clear();
			OFF_THREAD.clear();
			OFF_THREAD_SIZE.set(0);
			warnedFull = false;
		});
	}

	/** (from the world's block changes) The block at {@code pos} went from {@code was} to {@code now}. */
	public static void changed(ServerLevel level, BlockPos pos, BlockState was, BlockState now) {
		if (!level.getServer().isSameThread()) {
			// (not the server thread: the world is not ours to read, nor CHANGED to touch; look at it at this tick's end.
			// A log being placed takes no support away, and the world generating places a great many: those aren't queued)
			if (was == now || now.is(BlockTags.LOGS)) {
				return;
			}
			if (OFF_THREAD_SIZE.incrementAndGet() <= OFF_THREAD_MOST) {
				OFF_THREAD.add(new Pending(level, pos.asLong(), was));
			} else {
				OFF_THREAD_SIZE.decrementAndGet();
				if (!warnedFull) {
					warnedFull = true;
					JaymosKingdoms.LOGGER.warn("Loose wood: too many block changes from other threads at once; dropping the extra");
				}
			}
			return;
		}
		note(level, pos, was, now);
	}

	private static void note(ServerLevel level, BlockPos pos, BlockState was, BlockState now) {
		if (was == now || now.is(BlockTags.LOGS) || !(was.is(BlockTags.LOGS) || holds(level, pos, was)) || holds(level, pos, now)) {
			return;
		}
		CHANGED.computeIfAbsent(level, l -> new LongLinkedOpenHashSet()).add(pos.asLong());
	}

	private static void tick(MinecraftServer server) {
		for (Pending p; (p = OFF_THREAD.poll()) != null;) {
			OFF_THREAD_SIZE.decrementAndGet();
			BlockPos pos = BlockPos.of(p.pos());
			// (it may have changed again since: what counts is the block now, and what it was then)
			if (ready(p.level(), pos)) {
				note(p.level(), pos, p.was(), p.level().getBlockState(pos));
			}
		}
		if (CHANGED.isEmpty()) {
			return;
		}
		for (ServerLevel level : List.copyOf(CHANGED.keySet())) {
			LongLinkedOpenHashSet changed = CHANGED.get(level);
			// (what falls this tick changes the world too: those wait for the next)
			List<BlockPos> now = new ArrayList<>();
			while (!changed.isEmpty() && now.size() < CHECKS_PER_TICK) {
				now.add(BlockPos.of(changed.removeFirstLong()));
			}
			if (changed.isEmpty()) {
				CHANGED.remove(level);
			}
			Set<BlockPos> seen = new HashSet<>();
			for (BlockPos pos : now) {
				if (!ready(level, pos)) {
					continue;
				}
				for (BlockPos n : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
					if (!seen.contains(n) && ready(level, n) && level.getBlockState(n).is(BlockTags.LOGS)) {
						look(level, n.immutable(), seen);
					}
				}
			}
		}
	}

	/** The piece of wood {@code start} is in: if nothing holds it up, it comes down. Its logs are added to {@code seen}. */
	private static void look(ServerLevel level, BlockPos start, Set<BlockPos> seen) {
		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> piece = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		piece.add(start);
		open.add(start);
		try {
			while (!open.isEmpty()) {
				BlockPos at = open.poll();
				logs.add(at);
				if (logs.size() > LOOK_MOST || built(level, at)) {
					return;
				}
				for (Direction d : Direction.values()) {
					BlockPos q = at.relative(d);
					if (!ready(level, q)) {
						return;
					}
					BlockState s = level.getBlockState(q);
					if (!s.is(BlockTags.LOGS) && holds(level, q, s)) {
						return;
					}
				}
				for (BlockPos n : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
					if (piece.contains(n) || !ready(level, n) || !level.getBlockState(n).is(BlockTags.LOGS)) {
						continue;
					}
					BlockPos q = n.immutable();
					piece.add(q);
					open.add(q);
				}
			}
		} finally {
			seen.addAll(piece);
		}
		fall(level, logs);
	}

	/** A log a player placed, or a stripped one: part of a build, never brought down. */
	private static boolean built(ServerLevel level, BlockPos log) {
		return Placed.any(level, log) || BuiltInRegistries.BLOCK.getKey(level.getBlockState(log).getBlock()).getPath().startsWith("stripped_");
	}

	/**
	 * Whether {@code state} at {@code pos} holds up the wood beside it: anything with a body (stone, earth, planks, a
	 * chopped log's stump) but wood itself, leaves, and what only hangs on wood (cocoa pods, bee nests).
	 */
	private static boolean holds(ServerLevel level, BlockPos pos, BlockState state) {
		return !state.isAir() && !state.is(BlockTags.LOGS) && !(state.getBlock() instanceof LeavesBlock) && !state.is(BlockTags.BEEHIVES)
			&& !state.is(Blocks.COCOA) && !state.getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * Whether {@code pos}'s chunk is fully there to read now. Level.isLoaded only asks whether the chunk has a ticket, and
	 * is true for one still generating under a player's view: reading a block there made the server wait on it past the
	 * watchdog (2026-11-21 8:10 and 8:16 AM). getChunkNow never schedules or waits; what isn't ready is left for later.
	 */
	private static boolean ready(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())) != null;
	}

	/** The piece of {@code logs} held by nothing comes down: as a tree, or a few bare logs like sand. */
	private static void fall(ServerLevel level, List<BlockPos> logs) {
		logs.sort(Comparator.comparingInt(BlockPos::getY));
		List<BlockPos> leaves = Trees.leavesOf(level, logs);
		List<BlockPos> nests = FallingTree.hives(level, logs, null);
		if (logs.size() <= LOOSE_LOGS && leaves.isEmpty() && nests.isEmpty()) {
			for (BlockPos log : logs) {
				FallingBlockEntity loose = FallingBlockEntity.fall(level, log, level.getBlockState(log));
				loose.setHurtsEntities(HURT_PER_BLOCK, HURT_MOST);
				// (nobody felled it: if it can't land as a block it's gone, not left as a drop, FallingTree#byPlayer)
				loose.dropItem = false;
			}
			return;
		}
		leaves.addAll(nests);
		BlockPos foot = logs.get(0);
		FallingTree.drop(level, foot, logs, leaves, FallingTree.heavier(level, foot, logs, leaves, null), FallingTree.SPLIT_TUMBLE);
	}
}
