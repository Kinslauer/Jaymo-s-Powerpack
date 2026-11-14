package com.jaymospowers.aether.world;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.cloudsea.CloudSea;
import com.jaymospowers.compat.Stacked;
import com.jaymospowers.power.wind.aether.WindAether;
import java.util.ArrayDeque;
import java.util.HashSet;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

/**
 * No skyfalls before Dread (Andy: "i need them all removed until at least the world teir"), and since the Cloud Sea: "what if
 * they stopped in the cloud sea until later?" In a One Dimension world water can't flow down out of the floor of the lowest
 * sky band (the Cloud Sea's, or the Aether's in a world made without one) until someone online has reached Dread
 * ({@link #blocks}, from {@code FlowingFluidMixin}): a fall from the Aether ends in the open air of the Cloud Sea, and doesn't
 * spread out into a sheet there either. A fall already pouring into the Overworld's sky is cut at that floor when its chunk
 * loads, and the chunks round every player are checked again every {@link #SWEEP_TICKS} ticks, so the water under the cut
 * drains away by itself. After Dread the heavenfall chunks ({@link SkySpringFeature#heavenfall}) may pour again.
 */
public final class SkyfallCleanup {
	// ---- tuning ----
	/** Chunks checked per server tick. */
	static final int PER_TICK = 8;
	/** How often (ticks) the chunks round each player are queued again, and the floor read again. */
	static final int SWEEP_TICKS = 200;
	/** Chunks out from each player that the sweep queues. */
	static final int SWEEP_RADIUS = 10;

	/** The floor water may not fall through (Integer.MIN_VALUE: none, not a stacked world). Read by fluid ticks. */
	private static volatile int floor = Integer.MIN_VALUE;
	private static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>();
	private static final Set<ChunkPos> QUEUED = new HashSet<>();
	/** Whether this server's start was logged. */
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
			floor = Integer.MIN_VALUE;
			announced = false;
		});
	}

	/**
	 * Whether a fluid may not spread from its block into {@code target} (in {@code direction}): down through the sky floor,
	 * or out sideways in the air just above it, while nobody has reached Dread.
	 */
	public static boolean blocks(LevelAccessor level, BlockPos target, Direction direction) {
		int at = floor;
		if (at == Integer.MIN_VALUE || target.getY() != at - 1 && target.getY() != at) {
			return false;
		}
		if (!(level instanceof ServerLevel server) || server.dimension() != Level.OVERWORLD) {
			return false;
		}
		boolean through = direction == Direction.DOWN && target.getY() == at - 1;
		boolean sheet = direction.getAxis().isHorizontal() && target.getY() == at && server.getBlockState(target.below()).isAir();
		return (through || sheet) && !open(server, target);
	}

	/** Whether falls may pour here: Dread reached, and a heavenfall chunk. */
	private static boolean open(ServerLevel level, BlockPos pos) {
		return SkySpringFeature.dread() && SkySpringFeature.heavenfall(level, pos);
	}

	private static void onLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (level.dimension() == Level.OVERWORLD && floor != Integer.MIN_VALUE && QUEUED.add(chunk.getPos())) {
			QUEUE.add(chunk.getPos());
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!announced || server.getTickCount() % SWEEP_TICKS == 0) {
			floor = Stacked.joined(server, CloudSea.REALM) ? Stacked.minY(server, CloudSea.REALM)
				: Stacked.joined(server, WindAether.AETHER) ? Stacked.minY(server, WindAether.AETHER) : Integer.MIN_VALUE;
			if (!announced) {
				announced = true;
				JaymosPowers.LOGGER.info(floor == Integer.MIN_VALUE ? "Skyfall stop off: no sky band in this world"
					: "Skyfall stop on: no water falls below Y " + floor + " before Dread");
			}
			if (floor != Integer.MIN_VALUE) {
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
		}
		int at = floor;
		for (int i = 0; i < PER_TICK && !QUEUE.isEmpty(); i++) {
			ChunkPos pos = QUEUE.poll();
			QUEUED.remove(pos);
			if (at != Integer.MIN_VALUE && level.hasChunk(pos.x(), pos.z())) {
				cut(level, pos, at);
			}
		}
	}

	/** Cuts every fall in this chunk just under the floor, so the water below drains away. */
	private static void cut(ServerLevel level, ChunkPos chunk, int at) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int cut = 0;
		for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
			for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
				pos.set(x, at - 1, z);
				if (water(level.getFluidState(pos)) && water(level.getFluidState(pos.above())) && !open(level, pos)) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					cut++;
				}
			}
		}
		if (cut > 0) {
			JaymosPowers.LOGGER.info("Cut {} skyfall column(s) at Y {} in chunk {}", cut, at - 1, chunk);
		}
	}

	private static boolean water(FluidState fluid) {
		return !fluid.isEmpty() && fluid.is(FluidTags.WATER);
	}
}
