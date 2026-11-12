package com.jaymospowers.water;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Floods (Andy's TFC Hardrock picks, the Dynamic Waters row; our own code, "held to small areas so the Steam Deck server
 * keeps up"). A pond or pool is a finite amount of water, not a spring: break the wall that holds it back and it runs
 * out through the gap and downhill, the surface sinking as it goes, until what stood above the gap has drained away.
 * Only small bodies count ({@link #MOST_CELLS} blocks of still water or fewer, joined edge to edge): a lake, a river or the
 * sea is too big to empty and carries on as ever. At most {@link #MOST_FLOODS} run in a level at once, each taking
 * {@link #PER_STEP} blocks off the top of the pond every {@link #STEP_TICKS}, and none is looked at until the water
 * has been seen running out through the gap (flowing water by the breach), so a gap that leads nowhere drains nothing.
 * Started by a player breaking a block with a pond's water against it.
 */
public final class Floods {
	/** The biggest body of water that can flood, in blocks. */
	static final int MOST_CELLS = 360;
	/** Floods running in one level, blocks off the top each step, ticks between steps, and steps before it gives up. */
	static final int MOST_FLOODS = 3, PER_STEP = 2, STEP_TICKS = 10, MOST_STEPS = 400;
	/** Ticks the water is given to start running before the first step. */
	static final int RUN_UP = 12;

	private record Flood(BlockPos breach, long since, int[] steps) {
	}

	private static final Map<ResourceKey<Level>, List<Flood>> FLOODS = new HashMap<>();

	private Floods() {
	}

	public static void init() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel server && !state.getFluidState().is(FluidTags.WATER)) {
				breach(server, pos);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Floods::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FLOODS.clear());
	}

	/** {@code gap} was just opened: a flood starts if a small pond has water against it. */
	static void breach(ServerLevel level, BlockPos gap) {
		List<Flood> floods = FLOODS.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
		if (floods.size() >= MOST_FLOODS) {
			return;
		}
		for (Flood f : floods) {
			if (f.breach.distSqr(gap) < 25) {
				return;
			}
		}
		for (Direction d : Direction.values()) {
			if (d == Direction.DOWN) {
				continue;
			}
			BlockPos next = gap.relative(d);
			BlockState there = level.getBlockState(next);
			if (there.is(Blocks.WATER) && there.getFluidState().isSource() && body(level, next, gap.getY()) != null) {
				floods.add(new Flood(gap.immutable(), level.getGameTime(), new int[1]));
				level.playSound(null, gap, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.8F, 0.6F);
				return;
			}
		}
	}

	/**
	 * The still water joined to {@code start} that stands at or above {@code floor}, or null if the body is too big to
	 * empty (more than {@link #MOST_CELLS} blocks, or an unloaded edge).
	 */
	private static List<BlockPos> body(ServerLevel level, BlockPos start, int floor) {
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		List<BlockPos> above = new ArrayList<>();
		seen.add(start.asLong());
		queue.enqueue(start.asLong());
		while (!queue.isEmpty()) {
			BlockPos at = BlockPos.of(queue.dequeueLong());
			if (seen.size() > MOST_CELLS || !level.hasChunkAt(at)) {
				return null;
			}
			if (at.getY() >= floor) {
				above.add(at);
			}
			for (Direction d : Direction.values()) {
				BlockPos next = at.relative(d);
				if (seen.contains(next.asLong())) {
					continue;
				}
				BlockState state = level.getBlockState(next);
				if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
					seen.add(next.asLong());
					queue.enqueue(next.asLong());
				}
			}
		}
		return above;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % STEP_TICKS != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			List<Flood> floods = FLOODS.get(level.dimension());
			if (floods == null || floods.isEmpty()) {
				continue;
			}
			floods.removeIf(flood -> !step(level, flood));
		}
	}

	/** One step of {@code flood}: false when it's over. */
	private static boolean step(ServerLevel level, Flood flood) {
		if (level.getGameTime() - flood.since < RUN_UP) {
			return true;
		}
		if (!level.hasChunkAt(flood.breach) || ++flood.steps[0] > MOST_STEPS) {
			return false;
		}
		// the water must be seen running out through the gap, or there is nowhere for it to go
		boolean running = false;
		for (BlockPos p : BlockPos.betweenClosed(flood.breach.offset(-2, -3, -2), flood.breach.offset(2, 1, 2))) {
			BlockState s = level.getBlockState(p);
			if (s.is(Blocks.WATER) && !s.getFluidState().isSource()) {
				running = true;
				break;
			}
		}
		if (!running) {
			return false;
		}
		List<BlockPos> cells = new ArrayList<>();
		for (Direction d : Direction.values()) {
			BlockPos next = flood.breach.relative(d);
			BlockState there = level.getBlockState(next);
			if (d != Direction.DOWN && there.is(Blocks.WATER) && there.getFluidState().isSource()) {
				List<BlockPos> body = body(level, next, flood.breach.getY());
				if (body == null) {
					return false;
				}
				for (BlockPos p : body) {
					if (!cells.contains(p)) {
						cells.add(p);
					}
				}
			}
		}
		if (cells.isEmpty()) {
			return false;
		}
		// the surface sinks: the highest blocks go first, the farthest from the gap among them
		cells.sort(Comparator.comparingInt((BlockPos p) -> -p.getY()).thenComparingDouble(p -> -p.distSqr(flood.breach)));
		for (int i = 0; i < Math.min(PER_STEP, cells.size()); i++) {
			BlockPos p = cells.get(i);
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			if (level.getRandom().nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.BUBBLE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.02);
			}
		}
		if (flood.steps[0] % 6 == 1) {
			level.playSound(null, flood.breach, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 1.0F, 0.7F);
		}
		return true;
	}
}
