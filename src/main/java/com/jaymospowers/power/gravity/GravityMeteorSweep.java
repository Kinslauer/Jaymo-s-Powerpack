package com.jaymospowers.power.gravity;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * After a meteor's crater is torn out: anything it cut loose that is left hanging in the air over the crater (a tree's
 * crown whose trunk went, the lip of an overhang, a lone block the blasts missed) comes down. A clump counts as
 * hanging when it touches nothing solid and doesn't reach the edge of the box looked at ({@code reach} out round the
 * strike, {@code BELOW} under it to {@code ABOVE} over it): anything that does may be held up from outside. What
 * already hung there before the strike (an Aether islet, a cloud, see {@link #floating}) is left be. Clumps bigger than
 * {@code CLUMP_MAX} are left too (more likely a real island than wreckage), and at most {@code BREAK_MAX} blocks go.
 */
final class GravityMeteorSweep {
	/** The box: this far under and over the strike (blocks); round it, the crater rings' reach plus this. */
	static final int BELOW = 8, ABOVE = 40;
	static final double REACH_BEYOND = 6.0;
	/** The biggest clump taken as wreckage, and the most blocks one sweep breaks. */
	static final int CLUMP_MAX = 4000, BREAK_MAX = 12000;
	/** This share of what comes down drops as items; one block in {@code SHOW_ONE_IN} shows its break. */
	static final float DROP_CHANCE = 0.03F;
	static final int SHOW_ONE_IN = 4;
	/** (0.17.200) At most this many blocks come down a tick (all at once was a lag spike), the highest first. */
	static final int BREAK_PER_TICK = 1200;

	/** A sweep's blocks still to come down. */
	private record Pending(ResourceKey<Level> dimension, ArrayDeque<BlockPos> left, LongOpenHashSet going) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private GravityMeteorSweep() {
	}

	/** The positions of every clump hanging in the air round {@code at} now (as {@link BlockPos#asLong}). */
	static Set<Long> floating(ServerLevel level, Vec3 at, double reach) {
		Set<Long> out = new HashSet<>();
		for (List<BlockPos> clump : clumps(level, at, reach)) {
			for (BlockPos pos : clump) {
				out.add(pos.asLong());
			}
		}
		return out;
	}

	/**
	 * Brings down every clump hanging round {@code at} that wasn't there before ({@code before}, from {@link #floating}):
	 * {@code BREAK_PER_TICK} blocks a tick from here on (see {@link #tick}).
	 */
	static void sweep(ServerLevel level, Vec3 at, double reach, Set<Long> before) {
		List<BlockPos> all = new ArrayList<>();
		for (List<BlockPos> clump : clumps(level, at, reach)) {
			if (clump.stream().anyMatch(pos -> before.contains(pos.asLong()))) {
				continue;
			}
			for (BlockPos pos : clump) {
				if (all.size() >= BREAK_MAX) {
					break;
				}
				all.add(pos);
			}
		}
		if (all.isEmpty()) {
			return;
		}
		// the highest first, so nothing loose in it (sand, gravel) is left unpropped to fall
		all.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		LongOpenHashSet going = new LongOpenHashSet(all.size());
		for (BlockPos pos : all) {
			going.add(pos.asLong());
		}
		PENDING.add(new Pending(level.dimension(), new ArrayDeque<>(all), going));
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending pending = it.next();
			ServerLevel level = server.getLevel(pending.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			var random = level.getRandom();
			for (int i = 0; i < BREAK_PER_TICK && !pending.left().isEmpty(); i++) {
				BlockPos pos = pending.left().poll();
				pending.going().remove(pos.asLong());
				if (!level.isLoaded(pos)) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				if (state.isAir()) {
					continue;
				}
				if (random.nextFloat() < DROP_CHANCE || state.hasBlockEntity()) {
					level.destroyBlock(pos, true);
				} else {
					if (random.nextInt(SHOW_ONE_IN) == 0) {
						level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(state));
					}
					// with only air or more of the wreckage round it, it goes quietly; the clump's outside gets full updates
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), inside(level, pos, pending.going()) ? Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
						: Block.UPDATE_ALL);
				}
			}
			if (pending.left().isEmpty()) {
				it.remove();
			}
		}
	}

	static void clear() {
		PENDING.clear();
	}

	/** True if every block beside {@code pos} is air or one of {@code going} (so no neighbour needs telling it went). */
	static boolean inside(ServerLevel level, BlockPos pos, LongOpenHashSet going) {
		BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
		for (Direction direction : Direction.values()) {
			side.setWithOffset(pos, direction);
			if (!going.contains(side.asLong()) && !level.getBlockState(side).isAir()) {
				return false;
			}
		}
		return true;
	}

	/** The clumps of blocks in the box round {@code at} that touch nothing holding them up (see the class comment). */
	private static List<List<BlockPos>> clumps(ServerLevel level, Vec3 at, double reach) {
		int r = Mth.ceil(reach);
		int x0 = Mth.floor(at.x) - r, z0 = Mth.floor(at.z) - r;
		int y0 = Math.max(level.getMinY(), Mth.floor(at.y) - BELOW), y1 = Math.min(level.getMaxY(), Mth.floor(at.y) + ABOVE);
		int sx = 2 * r + 1, sy = y1 - y0 + 1, sz = 2 * r + 1;
		List<List<BlockPos>> out = new ArrayList<>();
		if (sy <= 2) {
			return out;
		}
		// 0 open, 1 a block, 2 a block that holds (unbreakable, or in an unloaded chunk), 3 looked at
		byte[] cells = new byte[sx * sy * sz];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < sx; dx++) {
			for (int dz = 0; dz < sz; dz++) {
				int x = x0 + dx, z = z0 + dz;
				boolean loaded = level.hasChunk(x >> 4, z >> 4);
				for (int dy = 0; dy < sy; dy++) {
					int i = (dx * sz + dz) * sy + dy;
					if (!loaded) {
						cells[i] = 2;
						continue;
					}
					pos.set(x, y0 + dy, z);
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
						continue;
					}
					cells[i] = state.getDestroySpeed(level, pos) < 0.0F ? (byte)2 : (byte)1;
				}
			}
		}
		int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
		for (int dx = 0; dx < sx; dx++) {
			for (int dz = 0; dz < sz; dz++) {
				for (int dy = 0; dy < sy; dy++) {
					int start = (dx * sz + dz) * sy + dy;
					if (cells[start] != 1) {
						continue;
					}
					// (cell indexes, not positions: the ground itself is one huge held clump, never worth a BlockPos a block)
					List<BlockPos> clump = new ArrayList<>();
					boolean held = false;
					cells[start] = 3;
					queue.enqueue(start);
					while (!queue.isEmpty()) {
						int cell = queue.dequeueInt();
						int cy = cell % sy, cz = cell / sy % sz, cx = cell / sy / sz;
						// at the box's edge it may go on outside it, held up there
						if (cx == 0 || cx == sx - 1 || cz == 0 || cz == sz - 1 || cy == 0 || cy == sy - 1) {
							held = true;
						}
						if (held) {
							clump.clear();
						} else {
							clump.add(new BlockPos(x0 + cx, y0 + cy, z0 + cz));
						}
						for (int[] side : sides) {
							int nx = cx + side[0], ny = cy + side[1], nz = cz + side[2];
							if (nx < 0 || nx >= sx || ny < 0 || ny >= sy || nz < 0 || nz >= sz) {
								continue;
							}
							int n = (nx * sz + nz) * sy + ny;
							if (cells[n] == 2) {
								held = true;
							} else if (cells[n] == 1) {
								cells[n] = 3;
								queue.enqueue(n);
							}
						}
					}
					if (!held && clump.size() <= CLUMP_MAX) {
						out.add(clump);
					}
				}
			}
		}
		return out;
	}
}
