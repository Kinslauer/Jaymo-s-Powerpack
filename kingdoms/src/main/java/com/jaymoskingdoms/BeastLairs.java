package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Small lairs for the great creatures that turn up in the open. Where a gorgon comes into the world by nature, the
 * place round it is dressed as the old ruin it keeps: a broken ring of mossy columns. Where a grizzly does, its den
 * ground: a scraped patch of bare earth, the tree it rubs its back on (bark worn off, as a real bear's rub tree is),
 * and the bones of what it ate. Nothing is built under a roof, over water, or on ground a builder laid; a creature
 * from an egg, a spawner or a command gets none. The dressing is done on the next tick, on the loaded world, so a
 * creature made while its chunk is still being generated doesn't write into its neighbours.
 */
public final class BeastLairs {
	/** The gorgon's ruin: how many columns, how far out they stand, the tallest. */
	static final int COLUMNS = 7, COLUMN_RING = 5, COLUMN_MOST = 3;
	/** One column in this many is missing (the ring is broken). */
	static final int COLUMN_GAP_ONE_IN = 4;
	/** The grizzly's den: how wide the scraped ground is, how tall the rub tree's trunk, how many bones. */
	static final int SCRAPE = 2, RUB_HEIGHT = 3, BONES = 2;
	/** Ground this far above or below the creature isn't its own (a cave, a canopy): nothing is placed there. */
	static final int GROUND_REACH = 3;

	private record Job(ServerLevel level, BlockPos at, boolean gorgon) {
	}

	private static final List<Job> QUEUE = new ArrayList<>();

	private BeastLairs() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (QUEUE.isEmpty()) {
				return;
			}
			List<Job> jobs = List.copyOf(QUEUE);
			QUEUE.clear();
			for (Job job : jobs) {
				if (!job.level().hasChunksAt(job.at().offset(-COLUMN_RING, 0, -COLUMN_RING), job.at().offset(COLUMN_RING, 0, COLUMN_RING))) {
					continue;
				}
				if (job.gorgon()) {
					ruin(job.level(), job.at());
				} else {
					den(job.level(), job.at());
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> QUEUE.clear());
	}

	/** A gorgon came into the world at {@code at}. */
	public static void gorgon(ServerLevelAccessor level, BlockPos at, EntitySpawnReason reason) {
		queue(level, at, reason, true);
	}

	/** A grizzly came into the world at {@code at}. */
	public static void grizzly(ServerLevelAccessor level, BlockPos at, EntitySpawnReason reason) {
		queue(level, at, reason, false);
	}

	private static void queue(ServerLevelAccessor level, BlockPos at, EntitySpawnReason reason, boolean gorgon) {
		if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) {
			QUEUE.add(new Job(level.getLevel(), at.immutable(), gorgon));
		}
	}

	/** The natural ground block in column ({@code x}, {@code z}) near {@code nearY}, or null (water, a roof, laid blocks, too far up or down). */
	private static @Nullable BlockPos ground(ServerLevel level, int x, int z, int nearY) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, nearY, z)).below();
		BlockState state = level.getBlockState(top);
		if (Math.abs(top.getY() + 1 - nearY) > GROUND_REACH || !state.getFluidState().isEmpty()
			|| !(state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.MUD)
				|| state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK))) {
			return null;
		}
		BlockState over = level.getBlockState(top.above());
		return over.getFluidState().isEmpty() && !over.isSolid() ? top : null;
	}

	private static void ruin(ServerLevel level, BlockPos at) {
		RandomSource random = level.getRandom();
		double turn = random.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < COLUMNS; i++) {
			if (random.nextInt(COLUMN_GAP_ONE_IN) == 0) {
				continue;
			}
			double angle = turn + Math.PI * 2.0 * i / COLUMNS;
			BlockPos foot = ground(level, at.getX() + (int) Math.round(Math.cos(angle) * COLUMN_RING), at.getZ() + (int) Math.round(Math.sin(angle) * COLUMN_RING),
				at.getY());
			if (foot == null) {
				continue;
			}
			int tall = 1 + random.nextInt(COLUMN_MOST);
			for (int h = 1; h <= tall; h++) {
				BlockState state = switch (random.nextInt(3)) {
					case 0 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
					case 1 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
					default -> Blocks.STONE_BRICKS.defaultBlockState();
				};
				level.setBlock(foot.above(h), state, Block.UPDATE_CLIENTS);
			}
			if (random.nextBoolean()) {
				level.setBlock(foot.above(tall + 1), Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	private static void den(ServerLevel level, BlockPos at) {
		RandomSource random = level.getRandom();
		for (int dx = -SCRAPE; dx <= SCRAPE; dx++) {
			for (int dz = -SCRAPE; dz <= SCRAPE; dz++) {
				if (dx * dx + dz * dz > SCRAPE * SCRAPE + 1 || random.nextInt(4) == 0) {
					continue;
				}
				BlockPos foot = ground(level, at.getX() + dx, at.getZ() + dz, at.getY());
				if (foot != null && level.getBlockState(foot).is(BlockTags.DIRT)) {
					level.setBlock(foot, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		double angle = random.nextDouble() * Math.PI * 2.0;
		BlockPos tree = ground(level, at.getX() + (int) Math.round(Math.cos(angle) * (SCRAPE + 1)), at.getZ() + (int) Math.round(Math.sin(angle) * (SCRAPE + 1)),
			at.getY());
		if (tree != null) {
			for (int h = 1; h <= RUB_HEIGHT; h++) {
				level.setBlock(tree.above(h), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		for (int i = 0; i < BONES; i++) {
			BlockPos foot = ground(level, at.getX() + random.nextInt(SCRAPE * 2 + 1) - SCRAPE, at.getZ() + random.nextInt(SCRAPE * 2 + 1) - SCRAPE, at.getY());
			if (foot != null && !foot.above().equals(at) && level.getBlockState(foot.above()).isAir()) {
				level.setBlock(foot.above(), Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
					random.nextBoolean() ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z), Block.UPDATE_CLIENTS);
			}
		}
	}
}
