package com.jaymospowers.power.xenoform.minds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The blocks of light Lumen's wall, bridge and cage are made of: solid, bright, standing a few seconds, then gone. Cleanup follows
 * {@code power.web.WebWall}: a timed list that waits for a chunk to come back, cleared with the server, and, in case a crash
 * left cells in a save, any cell found in a chunk as it loads that nothing tracks is taken out. Cells go only into empty air and
 * never over a body.
 */
public final class LightBlocks {
	// ---- tuning ----
	/** How long each stands (ticks). */
	public static final int WALL_LIFE = 160, BRIDGE_LIFE = 220, CAGE_LIFE = 160;
	/** The wall: blocks across, high, and distance in front of the feet. */
	public static final int WALL_ACROSS = 5, WALL_HIGH = 3;
	public static final double WALL_FRONT = 3.0;
	/** The bridge: blocks long and wide. */
	public static final int BRIDGE_LONG = 10, BRIDGE_WIDE = 2;
	/** Most cells one use may place. */
	public static final int MAX_CELLS = 120;
	public static final int COLOR = 0x9ADCF4;

	public static Block BLOCK;

	private record Cell(ResourceKey<Level> dimension, BlockPos pos, long until) {
	}

	private static final List<Cell> CELLS = new ArrayList<>();
	/** Chunks that loaded and may hold a leftover cell, to be swept on the next tick. */
	private static final List<LevelChunk> SWEEP = new ArrayList<>();
	private static long clock;

	private LightBlocks() {
	}

	public static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("light_construct"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of()
			.mapColor(MapColor.ICE)
			.strength(0.5F, 6.0F)
			.noLootTable()
			.lightLevel(state -> 12)
			.sound(SoundType.AMETHYST)
			.pushReaction(PushReaction.POPPED)
			.setId(key)));
		ServerTickEvents.END_SERVER_TICK.register(LightBlocks::tick);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, isNew) -> {
			if (chunk.getSections().length > 0 && java.util.Arrays.stream(chunk.getSections()).anyMatch(s -> s.maybeHas(state -> state.is(BLOCK)))) {
				SWEEP.add(chunk);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Cell cell : CELLS) {
				ServerLevel level = server.getLevel(cell.dimension());
				if (level != null && level.isLoaded(cell.pos()) && level.getBlockState(cell.pos()).is(BLOCK)) {
					level.setBlock(cell.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			CELLS.clear();
			SWEEP.clear();
		});
	}

	/** A wall across the way ahead. Returns false if nothing could be placed. */
	public static boolean wall(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 forward = MindsKit.flat(player);
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 middle = player.position().add(forward.scale(WALL_FRONT));
		Set<BlockPos> cells = new LinkedHashSet<>();
		for (int u = -(WALL_ACROSS / 2); u <= WALL_ACROSS / 2; u++) {
			Vec3 spot = middle.add(right.scale(u));
			for (int h = 0; h < WALL_HIGH; h++) {
				add(player, level, BlockPos.containing(spot.x, player.getBlockY() + h, spot.z), cells);
			}
		}
		return raise(level, cells, WALL_LIFE);
	}

	/** A bridge out along the way you face, at the height of the floor you stand on. */
	public static boolean bridge(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 forward = MindsKit.flat(player);
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		Set<BlockPos> cells = new LinkedHashSet<>();
		int y = player.getBlockY() - 1;
		for (int i = 1; i <= BRIDGE_LONG; i++) {
			for (int w = 0; w < BRIDGE_WIDE; w++) {
				Vec3 spot = player.position().add(forward.scale(i)).add(right.scale(w - (BRIDGE_WIDE - 1) * 0.5 + 0.0));
				add(player, level, BlockPos.containing(spot.x, y, spot.z), cells);
			}
		}
		return raise(level, cells, BRIDGE_LIFE);
	}

	/** A cage of light round {@code target}: a floor, a roof and four walls, the creature left standing inside. */
	public static boolean cage(ServerPlayer player, Entity target) {
		ServerLevel level = player.level();
		BlockPos at = target.blockPosition();
		Set<BlockPos> cells = new LinkedHashSet<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					if (dx == 0 && dz == 0 && dy >= 0 && dy <= 1) {
						continue;
					}
					if (Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == -1 || dy == 2) {
						add(player, level, at.offset(dx, dy, dz), cells);
					}
				}
			}
		}
		return raise(level, cells, CAGE_LIFE);
	}

	/** Adds {@code pos} when the player may build there, it is empty air and nothing stands in it. */
	private static void add(ServerPlayer player, ServerLevel level, BlockPos pos, Set<BlockPos> cells) {
		if (cells.size() >= MAX_CELLS || !level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()
			|| !player.mayBuild() || !level.mayInteract(player, pos)) {
			return;
		}
		if (!level.getEntities((Entity)null, new AABB(pos), e -> !e.isSpectator() && !(e instanceof net.minecraft.world.entity.item.ItemEntity)).isEmpty()) {
			return;
		}
		cells.add(pos.immutable());
	}

	private static boolean raise(ServerLevel level, Set<BlockPos> cells, int life) {
		if (cells.isEmpty()) {
			return false;
		}
		var state = BLOCK.defaultBlockState();
		for (BlockPos pos : cells) {
			level.setBlock(pos, state, Block.UPDATE_ALL);
			CELLS.add(new Cell(level.dimension(), pos, clock + life));
			if (CELLS.size() % 4 == 0) {
				level.sendParticles(MagicFx.GLOW, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.0);
			}
		}
		return true;
	}

	private static void tick(MinecraftServer server) {
		clock++;
		for (Iterator<Cell> it = CELLS.iterator(); it.hasNext();) {
			Cell cell = it.next();
			if (clock < cell.until()) {
				continue;
			}
			ServerLevel level = server.getLevel(cell.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			if (!level.isLoaded(cell.pos())) {
				continue; // waits for its chunk to come back
			}
			it.remove();
			if (level.getBlockState(cell.pos()).is(BLOCK)) {
				level.setBlock(cell.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(MagicFx.mote(COLOR, 0.6F), cell.pos().getX() + 0.5, cell.pos().getY() + 0.5, cell.pos().getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.0);
			}
		}
		if (!SWEEP.isEmpty()) {
			sweep();
		}
	}

	/** Takes out cells in freshly loaded chunks that nothing tracks (left by a crash). */
	private static void sweep() {
		List<LevelChunk> chunks = new ArrayList<>(SWEEP);
		SWEEP.clear();
		for (LevelChunk chunk : chunks) {
			if (!(chunk.getLevel() instanceof ServerLevel level)) {
				continue;
			}
			ChunkPos at = chunk.getPos();
			for (int x = at.getMinBlockX(); x <= at.getMaxBlockX(); x++) {
				for (int z = at.getMinBlockZ(); z <= at.getMaxBlockZ(); z++) {
					for (int y = level.getMinY(); y < level.getMaxY(); y++) {
						BlockPos pos = new BlockPos(x, y, z);
						if (chunk.getBlockState(pos).is(BLOCK) && CELLS.stream().noneMatch(c -> c.dimension() == level.dimension() && c.pos().equals(pos))) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
						}
					}
				}
			}
		}
	}
}
