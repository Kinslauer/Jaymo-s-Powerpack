package com.jaymospowers.aether;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Aether's own worldgen features (docs/aether.md, "The dimension"): golden oak, crystal islands, aerclouds and
 * quicksoil shelves. Each is a {@link Feature} with its configured feature in {@code data/jaymospowers/worldgen/feature}
 * and its placement in {@code worldgen/placed_feature}; the biomes list the placed features. Everything else the
 * dimension grows (skyroot trees, ores, lakes, springs, plants) is vanilla feature types in that data.
 *
 * <p>This class registers the types and holds the small helpers the features share. Every write goes through
 * {@link #put}, which keeps to the region the generator may touch.
 */
public final class AetherFeatures {
	/** Block update flags for worldgen writes (clients only; nothing needs to react to a generating block). */
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	/** Leaves farther than this from a log would wither (the leaves block's own limit). */
	private static final int LEAF_REACH = 7;

	private AetherFeatures() {
	}

	static void init() {
		register("golden_oak", GoldenOakFeature.CODEC);
		register("crystal_island", CrystalIslandFeature.CODEC);
		register("aercloud", AercloudFeature.CODEC);
		register("quicksoil_shelf", QuicksoilShelfFeature.CODEC);
	}

	private static void register(String id, MapCodec<? extends Feature> codec) {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id(id), codec);
	}

	/** Sets a block, unless {@code pos} lies outside what this generation step may write. */
	static boolean put(WorldGenLevel level, BlockPos pos, BlockState state) {
		return level.ensureCanWrite(pos) && level.setBlock(pos, state, FLAGS);
	}

	/** Open air that may be written to. */
	static boolean isAir(WorldGenLevel level, BlockPos pos) {
		return level.ensureCanWrite(pos) && level.getBlockState(pos).isAir();
	}

	/** Room for a tree: air, or what trees grow through (plants, leaves, snow). */
	static boolean isFree(WorldGenLevel level, BlockPos pos) {
		if (!level.ensureCanWrite(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES);
	}

	/** A block a tree can stand on: aether dirt or either kind of aether grass. */
	static boolean isSoil(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(AetherBlocks.AETHER_GRASS_BLOCK) || state.is(AetherBlocks.ENCHANTED_AETHER_GRASS_BLOCK);
	}

	/**
	 * Lays a tree's leaves (state by position), each with the right distance from the logs so none of them wither, and
	 * only where there is room. {@code logs} are the tree's log positions.
	 */
	static void placeLeaves(WorldGenLevel level, Set<BlockPos> logs, Map<BlockPos, BlockState> leaves) {
		// breadth-first out from the logs through the leaves
		Map<BlockPos, Integer> distance = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos log : logs) {
			queue.add(log);
			distance.put(log, 0);
		}
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			int next = distance.get(at) + 1;
			if (next > LEAF_REACH) {
				continue;
			}
			for (Direction direction : Direction.values()) {
				BlockPos to = at.relative(direction);
				if (leaves.containsKey(to) && !distance.containsKey(to)) {
					distance.put(to, next);
					queue.add(to);
				}
			}
		}
		for (Map.Entry<BlockPos, BlockState> leaf : leaves.entrySet()) {
			BlockPos pos = leaf.getKey();
			if (logs.contains(pos) || !isFree(level, pos)) {
				continue;
			}
			int d = Math.min(distance.getOrDefault(pos, LEAF_REACH), LEAF_REACH);
			put(level, pos, leaf.getValue().setValue(LeavesBlock.DISTANCE, d).setValue(LeavesBlock.PERSISTENT, false));
		}
	}
}
