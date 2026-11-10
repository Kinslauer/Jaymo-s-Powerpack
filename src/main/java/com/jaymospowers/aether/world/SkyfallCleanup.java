package com.jaymospowers.aether.world;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.Stacked;
import com.jaymospowers.power.wind.aether.WindAether;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

/**
 * (2026-11-08, Andy: "I saw another skyfall earlier today, i need them all removed until at least the world teir") Since 0.21.4
 * a new chunk gets no sky waterfall before the Dread tier ({@link SkySpringFeature}), but chunks made before that kept
 * theirs: water poured on from an Aether spring, down through the Overworld's sky. Now, in a One Dimension world, every
 * chunk that loads is checked at the Aether's floor: water running on down out of it is traced back up through the Aether to the source blocks
 * feeding it, and each source is sealed with the stone beside it (or air), so the fall drains away by itself. A heavenfall
 * chunk's own fall is left alone once someone online has reached Dread, as for new chunks. Andy still saw them on
 * 2026-11-10: a chunk can load before its spring's water has run, so the chunks round every player are checked again every
 * {@link #SWEEP_TICKS} ticks as well.
 * <p>
 * Only water that leaves the bottom of the Aether is touched, so pools and springs that land on the islands stay. That
 * includes a waterfall a player poured off an edge on purpose before Dread.
 */
public final class SkyfallCleanup {
	// ---- tuning ----
	/** Chunks checked per server tick. */
	static final int PER_TICK = 8;
	/**
	 * How often (ticks) the chunks round each player are queued again. A spring in a chunk that loaded before its water had
	 * run (made at the edge of sight, ticked later) only pours after the load check, so falls are looked for again here.
	 */
	static final int SWEEP_TICKS = 200;
	/** Chunks out from each player that the sweep queues. */
	static final int SWEEP_RADIUS = 10;
	/** Water blocks a trace may visit before it gives up (a whole leaking lake is left as it is). */
	static final int BUDGET = 8000;

	private static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>();
	private static final Set<ChunkPos> QUEUED = new HashSet<>();
	/** Whether this server's start was logged (on or off, and the Aether band's heights). */
	private static boolean announced;

	private SkyfallCleanup() {
	}

	/** Called from {@code AetherFeatures.init}. */
	public static void init() {
		ServerChunkEvents.CHUNK_LOAD.register(SkyfallCleanup::onLoad);
		ServerTickEvents.END_SERVER_TICK.register(SkyfallCleanup::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			QUEUE.clear();
			QUEUED.clear();
			NOTED.clear();
			announced = false;
		});
	}

	private static void onLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (level.dimension() == Level.OVERWORLD && Stacked.joined(level.getServer(), WindAether.AETHER) && QUEUED.add(chunk.getPos())) {
			QUEUE.add(chunk.getPos());
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!announced) {
			announced = true;
			JaymosPowers.LOGGER.info(Stacked.joined(server, WindAether.AETHER)
				? "Skyfall cleanup on: Aether band from Y " + Stacked.minY(server, WindAether.AETHER) + " to " + Stacked.maxY(server, WindAether.AETHER)
				: "Skyfall cleanup off: the Aether isn't a band of this world");
		}
		if (server.getTickCount() % SWEEP_TICKS == 0 && Stacked.joined(server, WindAether.AETHER)) {
			for (ServerPlayer player : level.players()) {
				ChunkPos at = player.chunkPosition();
				for (int dx = -SWEEP_RADIUS; dx <= SWEEP_RADIUS; dx++) {
					for (int dz = -SWEEP_RADIUS; dz <= SWEEP_RADIUS; dz++) {
						ChunkPos pos = new ChunkPos(at.x() + dx, at.z() + dz);
						if (QUEUED.add(pos)) {
							QUEUE.add(pos);
						}
					}
				}
			}
		}
		if (QUEUE.isEmpty()) {
			return;
		}
		int floor = Stacked.minY(server, WindAether.AETHER), top = Stacked.maxY(server, WindAether.AETHER);
		for (int i = 0; i < PER_TICK && !QUEUE.isEmpty(); i++) {
			ChunkPos pos = QUEUE.poll();
			QUEUED.remove(pos);
			if (level.hasChunk(pos.x(), pos.z())) {
				check(level, pos, floor, top);
			}
		}
	}

	/** Seals the sources of any water crossing the Aether's floor in this chunk. */
	private static void check(ServerLevel level, ChunkPos chunk, int floor, int top) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
			for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
				at.set(x, floor, z);
				if (water(level.getFluidState(at)) && water(level.getFluidState(at.below()))) {
					seal(level, at.immutable(), floor, top);
				}
			}
		}
	}

	/** Traces the water at {@code start} up through the Aether and seals every source block that feeds it. */
	private static void seal(ServerLevel level, BlockPos start, int floor, int top) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayList sources = new LongArrayList();
		queue.add(start);
		seen.add(start.asLong());
		int budget = BUDGET;
		while (!queue.isEmpty()) {
			if (--budget < 0) {
				note(start, "more than " + BUDGET + " water blocks above it; left alone");
				return;
			}
			BlockPos pos = queue.poll();
			if (level.getFluidState(pos).isSource()) {
				sources.add(pos.asLong());
			}
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				if (next.getY() < floor || next.getY() > top || !level.hasChunk(next.getX() >> 4, next.getZ() >> 4)) {
					continue;
				}
				if (seen.add(next.asLong()) && water(level.getFluidState(next))) {
					queue.add(next);
				}
			}
		}
		int sealed = 0;
		for (long packed : sources) {
			BlockPos source = BlockPos.of(packed);
			if (SkySpringFeature.heavenfall(level, source)) {
				continue;
			}
			level.setBlock(source, stoneBeside(level, source), 3);
			sealed++;
		}
		if (sealed == 0) {
			note(start, sources.isEmpty() ? "no source found in the Aether above it" : "a heavenfall, kept (Dread reached)");
		}
		if (sealed > 0) {
			JaymosPowers.LOGGER.info("Sealed {} Aether water source(s) pouring into the Overworld's sky at {}", sealed, start);
		}
	}

	/** Columns already reported as left pouring, so a sweep doesn't log them again and again. */
	private static final Set<BlockPos> NOTED = new HashSet<>();

	/** Logs, once per column, a fall this couldn't stop and why (for Andy's logs/latest.log). */
	private static void note(BlockPos start, String why) {
		if (NOTED.size() < 256 && NOTED.add(start)) {
			JaymosPowers.LOGGER.info("Skyfall at {} still pouring: {}", start, why);
		}
	}

	private static boolean water(FluidState fluid) {
		return !fluid.isEmpty() && fluid.is(FluidTags.WATER);
	}

	/** The commonest solid block next to {@code pos} (so a spring hole closes in its own stone), else air. */
	private static BlockState stoneBeside(ServerLevel level, BlockPos pos) {
		Map<BlockState, Integer> counts = new HashMap<>();
		for (Direction direction : Direction.values()) {
			BlockState state = level.getBlockState(pos.relative(direction));
			if (state.isSolid() && state.getFluidState().isEmpty() && !state.hasBlockEntity()) {
				counts.merge(state.getBlock().defaultBlockState(), 1, Integer::sum);
			}
		}
		return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Blocks.AIR.defaultBlockState());
	}
}
