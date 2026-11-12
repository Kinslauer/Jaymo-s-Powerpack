package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * Goo wall: the symbiote throws up a shell of itself. Standing, a curved wall ({@link #ARC} blocks across,
 * {@link #HEIGHT} high) {@link #FRONT} blocks in front of the host; sneaking, a half-dome over the host and anyone
 * within {@link #COVER} blocks. It is blocks of {@link #WALL} (so it stops arrows and bodies), put only into empty air
 * and never over a body, and it draws back after {@link #LIFE_TICKS}. Cleanup follows {@link WebBridges}: a timed list that
 * waits for a chunk to come back, cleared with the server; and, in case a crash left cells in a save, any wall cell found in
 * a chunk as it loads that nothing tracks is taken out.
 */
public final class WebWall {
	// ================================================================================================================
	// Tuning
	/** How long it stands and the wait after it (ticks). */
	public static final int LIFE_TICKS = 120, COOLDOWN = 500;
	/** The wall: blocks across, high, and distance in front of the host's feet. */
	static final double ARC = 5.0, FRONT = 3.0;
	static final int HEIGHT = 3;
	/** The dome: radius to the shell's middle, thickness of the shell, and the radius of those it covers. */
	static final double DOME = 3.5, SHELL = 0.55, COVER = 2.5;
	/** Most cells one use may place. */
	static final int MAX_CELLS = 160;
	/** How far up or down from the host's feet the ground may be where the wall stands (blocks). */
	static final int STEP = 2;
	// ================================================================================================================

	public static Block WALL;

	private record Cell(ResourceKey<Level> dimension, BlockPos pos, long until) {
	}

	private static final List<Cell> CELLS = new ArrayList<>();
	/** Chunks that loaded and may hold a leftover cell, to be swept on the next tick. */
	private static final List<LevelChunk> SWEEP = new ArrayList<>();
	private static long clock;

	private WebWall() {
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("web_goo_wall"));
		WALL = Registry.register(BuiltInRegistries.BLOCK, key, new WebGooWallBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(1.5F, 6.0F)
			.noLootTable()
			.noOcclusion()
			.sound(SoundType.HONEY_BLOCK)
			.pushReaction(PushReaction.POPPED)
			.setId(key)));
		ServerTickEvents.END_SERVER_TICK.register(WebWall::tick);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, isNew) -> {
			if (chunk.getSections().length > 0 && java.util.Arrays.stream(chunk.getSections()).anyMatch(s -> s.maybeHas(state -> state.is(WALL)))) {
				SWEEP.add(chunk);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Cell cell : CELLS) {
				ServerLevel level = server.getLevel(cell.dimension());
				if (level != null && level.isLoaded(cell.pos()) && level.getBlockState(cell.pos()).is(WALL)) {
					level.setBlock(cell.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			CELLS.clear();
			SWEEP.clear();
		});
	}

	/** The key: throw up the shell. */
	static void raise(ServerPlayer player) {
		if (WebPuddle.puddled(player) || !WebGoo.ready(player, WebGoo.WALL)) {
			return;
		}
		ServerLevel level = player.level();
		Set<BlockPos> cells = player.isShiftKeyDown() ? dome(level, player) : wall(level, player);
		if (cells.isEmpty() || !WebGoo.pay(player, WebGoo.WALL_COST)) {
			return;
		}
		BlockState goo = WALL.defaultBlockState();
		int color = WebSymbioteStrains.color(WebSymbiote.state(player).kind());
		for (BlockPos pos : cells) {
			level.setBlock(pos, goo, Block.UPDATE_ALL);
			CELLS.add(new Cell(level.dimension(), pos, clock + LIFE_TICKS));
		}
		WebGoo.cool(player, WebGoo.WALL, COOLDOWN);
		Vec3 at = player.position();
		MagicFx.ring(level, at, player.isShiftKeyDown() ? (float)DOME : (float)FRONT, color);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.2F, 0.5F);
		Shakes.send(player, 0.3F);
		WebGoo.remark(player, "goo_wall");
	}

	/** The curved wall in front of the host: an arc of radius {@link #FRONT} about their feet, {@link #ARC} across. */
	private static Set<BlockPos> wall(ServerLevel level, ServerPlayer player) {
		Set<BlockPos> cells = new LinkedHashSet<>();
		Vec3 look = player.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0.0, look.z);
		if (forward.lengthSqr() < 1.0E-4) {
			forward = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		forward = forward.normalize();
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		double half = ARC / 2.0 / FRONT;
		for (double a = -half; a <= half + 1.0E-6; a += 0.05) {
			Vec3 spot = player.position().add(forward.scale(Math.cos(a) * FRONT)).add(right.scale(Math.sin(a) * FRONT));
			column(level, BlockPos.containing(spot.x, player.getBlockY(), spot.z), HEIGHT, cells);
		}
		return cells;
	}

	/** The half-dome over the host: a shell of radius {@link #DOME} about their feet, above the ground. */
	private static Set<BlockPos> dome(ServerLevel level, ServerPlayer player) {
		Set<BlockPos> cells = new LinkedHashSet<>();
		BlockPos feet = player.blockPosition();
		int reach = (int)Math.ceil(DOME + SHELL);
		for (int x = -reach; x <= reach; x++) {
			for (int y = 0; y <= reach; y++) {
				for (int z = -reach; z <= reach; z++) {
					double distance = Math.sqrt(x * x + (y + 0.5) * (y + 0.5) + z * z);
					if (Math.abs(distance - DOME) <= SHELL) {
						place(level, feet.offset(x, y, z), cells);
					}
				}
			}
		}
		return cells;
	}

	/** A column {@code height} high standing on the nearest ground to {@code around} (within {@link #STEP}). */
	private static void column(ServerLevel level, BlockPos around, int height, Set<BlockPos> cells) {
		for (int dy = STEP; dy >= -STEP; dy--) {
			BlockPos base = around.above(dy);
			if (level.getBlockState(base).isAir() && !level.getBlockState(base.below()).isAir() && level.getFluidState(base.below()).isEmpty()) {
				for (int h = 0; h < height; h++) {
					place(level, base.above(h), cells);
				}
				return;
			}
		}
	}

	/** Adds {@code pos} when it is empty air with nothing standing in it. */
	private static void place(ServerLevel level, BlockPos pos, Set<BlockPos> cells) {
		if (cells.size() >= MAX_CELLS || !level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()) {
			return;
		}
		if (!level.getEntities((net.minecraft.world.entity.Entity)null, new net.minecraft.world.phys.AABB(pos), e -> !e.isSpectator()).isEmpty()) {
			return;
		}
		cells.add(pos.immutable());
	}

	private static void tick(MinecraftServer server) {
		clock++;
		for (Iterator<Cell> it = CELLS.iterator(); it.hasNext(); ) {
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
			if (level.getBlockState(cell.pos()).is(WALL)) {
				level.setBlock(cell.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(MagicFx.miasma(WebSymbioteStrains.color(WebSymbioteStrain.BLACK), 0.6F), cell.pos().getX() + 0.5, cell.pos().getY() + 0.5,
					cell.pos().getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.0);
			}
		}
		if (!SWEEP.isEmpty()) {
			sweep();
		}
	}

	/** Takes out wall cells in freshly loaded chunks that nothing tracks (left by a crash). */
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
						if (chunk.getBlockState(pos).is(WALL) && CELLS.stream().noneMatch(c -> c.dimension() == level.dimension() && c.pos().equals(pos))) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
						}
					}
				}
			}
		}
	}
}
