package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * (0.19.24) Hot springs: small pools of warm water in mountain, taiga and snowy biomes
 * ({@code worldgen/placed_feature/hot_spring.json}, a few per many chunks). Soaking in one heals slowly ({@link Resting}).
 * <p>
 * A pool is a disc of radius {@link #MIN_RADIUS} to {@link #MAX_RADIUS} cut {@link #WATER_DEPTH} deep into flat ground, its
 * top level with the ground and ringed by gravel, stone and calcite. Under the water is a floor of gravel, and under that a
 * layer of magma: the floor keeps the magma from touching the water (so no bubble columns drag a bather down) and from
 * burning anyone standing on it, while the magma still lies within {@link Resting#SPRING_DEPTH} blocks of a bather's feet.
 * A pool is only dug on flat ground that is solid all round it, so the water can't run out.
 */
public final class HotSprings {
	// ---- tuning ----
	/** (0.19.24) Pool radius (blocks), at least and at most. */
	public static final int MIN_RADIUS = 2, MAX_RADIUS = 4;
	/** (0.19.24) Layers of water, top level with the ground. */
	public static final int WATER_DEPTH = 2;
	/** (0.19.24) Of the rim blocks, this many in 100 are gravel, this many more calcite; the rest stone. */
	public static final int RIM_GRAVEL = 45, RIM_CALCITE = 20;

	private HotSprings() {
	}

	static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("hot_spring"), HotSpring.CODEC);
		var cold = BiomeSelectors.foundInOverworld().and(BiomeSelectors.tag(BiomeTags.IS_MOUNTAIN)
			.or(BiomeSelectors.tag(BiomeTags.IS_TAIGA)).or(BiomeSelectors.tag(ConventionalBiomeTags.IS_SNOWY)));
		BiomeModifications.addFeature(cold, GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
			ResourceKey.<PlacedFeature>create(Registries.PLACED_FEATURE, JaymosPowers.id("hot_spring")));
	}

	/** Ground a pool may be cut into (natural, never a build). */
	private static boolean ground(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK)
			|| state.is(Blocks.SAND) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL);
	}

	/** Air, snow, grass and the like, with no fluid in it. */
	private static boolean free(BlockState state) {
		return (state.isAir() || state.is(BlockTags.REPLACEABLE)) && state.getFluidState().isEmpty();
	}

	public record HotSpring() implements Feature {
		public static final MapCodec<HotSpring> CODEC = MapCodec.unit(HotSpring::new);

		@Override
		public MapCodec<HotSpring> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
			BlockPos top = origin.below();
			int pool = radius * radius + 1, rim = (radius + 1) * (radius + 1) + 1;
			// check the whole site first: flat solid ground under the pool and its rim, clear air over the pool
			for (int dx = -radius - 1; dx <= radius + 1; dx++) {
				for (int dz = -radius - 1; dz <= radius + 1; dz++) {
					int d = dx * dx + dz * dz;
					if (d > rim) {
						continue;
					}
					BlockPos at = top.offset(dx, 0, dz);
					if (!ground(level.getBlockState(at)) || !free(level.getBlockState(at.above()))) {
						return false;
					}
					if (d <= pool && !ground(level.getBlockState(at.below()))) {
						return false;
					}
				}
			}
			for (int dx = -radius - 1; dx <= radius + 1; dx++) {
				for (int dz = -radius - 1; dz <= radius + 1; dz++) {
					int d = dx * dx + dz * dz;
					BlockPos at = top.offset(dx, 0, dz);
					if (d > rim) {
						continue;
					}
					if (d > pool) {
						// the rim: gravel, stone and calcite round the water
						int roll = random.nextInt(100);
						Block block = roll < RIM_GRAVEL ? Blocks.GRAVEL : roll < RIM_GRAVEL + RIM_CALCITE ? Blocks.CALCITE : Blocks.STONE;
						level.setBlock(at, block.defaultBlockState(), Block.UPDATE_CLIENTS);
						if (!level.getBlockState(at.above()).isAir()) {
							level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
						continue;
					}
					// the water, from the ground down
					for (int i = 0; i < WATER_DEPTH; i++) {
						level.setBlock(at.below(i), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
					if (!level.getBlockState(at.above()).isAir()) {
						level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
					// a gravel floor, and magma under it (not under the edge, so the pool's wall stays stone)
					level.setBlock(at.below(WATER_DEPTH), Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_CLIENTS);
					boolean inner = d <= (radius - 1) * (radius - 1) + 1;
					level.setBlock(at.below(WATER_DEPTH + 1), (inner ? Blocks.MAGMA_BLOCK : Blocks.STONE).defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			return true;
		}
	}
}
