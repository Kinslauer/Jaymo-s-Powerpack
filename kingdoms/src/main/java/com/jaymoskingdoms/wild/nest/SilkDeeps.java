package com.jaymoskingdoms.wild.nest;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * (medieval picks, de-nests) The silk deeps: a cave biome of ours in the Overworld, the caves the spiders have taken over.
 * It sits in the Overworld's biome list beside the lush and dripstone caves ({@link #placing}), in patches under the
 * temperate inland ground, partway down (well under the surface, above the deep dark). Its caves are coated as they
 * generate ({@link Coat}): the bare rock round every open space goes to dark spider stone, the ceilings and walls hung with
 * nest silk, shed skins lie on the floors, cobwebs hang from the roof, and egg sacs ({@link EggSacBlock}) hang like fruit
 * or sit in clusters on the floor. It spawns spiders thick (netcasters and mothers among them, {@code SpiderKinds}) and
 * bats for them to eat ({@link SpiderNests}: what the nest catches feeds it, and a fed nest spreads). At its deepest a
 * brood queen sleeps on her egg ({@link NestStructures}); above, a watcher's hut keeps a map down.
 * <p>
 * Lore: the silk deeps (Lore.md, Andy approved 2026-11-15).
 */
public final class SilkDeeps {
	// ---- tuning ----
	/** Where the biome lies in the climate (each -1 to 1): temperate to warm, middling damp, inland, a narrow band of erosion
	 * and of weirdness (so it comes in patches). */
	static final float TEMP_MIN = -0.15F, TEMP_MAX = 0.55F, HUMID_MIN = -0.1F, HUMID_MAX = 0.15F, INLAND_MIN = 0.1F, INLAND_MAX = 0.75F,
		EROSION_MIN = -0.1F, EROSION_MAX = 0.1F, WEIRD_MIN = 0.35F, WEIRD_MAX = 0.5F;
	/** How deep it reaches (the climate's depth: 0 is the surface, about a block per 128th). */
	static final float DEPTH_MIN = 0.2F, DEPTH_MAX = 0.9F;
	/** Its offset: large, so it only wins deep in its own patch (the surface biome reaching down wins the rest), and the
	 * lush, dripstone, sulfur and ice caves all win over it where they lie. */
	static final float OFFSET = 0.36F;
	/** The coat: the heights it is laid between. */
	static final int COAT_MIN_Y = -60, COAT_MAX_Y = 48;
	/** Of the rock round a cave, the share (in 1) that is nest silk: on the ceiling, on the walls, on the floor (the rest is spider stone). */
	static final float SILK_ROOF = 0.55F, SILK_WALL = 0.3F, SILK_FLOOR = 0.08F;
	/** Per open floor spot: a shed skin, an egg sac; per ceiling spot: a hanging cobweb, an egg sac hung; per wall spot: a cobweb. */
	static final float SKIN_CHANCE = 0.06F, FLOOR_EGG_CHANCE = 0.008F, HANG_WEB_CHANCE = 0.08F, HANG_EGG_CHANCE = 0.012F, WALL_WEB_CHANCE = 0.015F;
	/** A worldgen egg sac first looks for passers-by this many ticks after its chunk ticks. */
	static final int FIRST_LOOK = 40;

	public static final ResourceKey<Biome> SILK_DEEPS = ResourceKey.create(Registries.BIOME, JaymosKingdoms.id("silk_deeps"));

	private static boolean placed;

	private SilkDeeps() {
	}

	static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("silk_deeps_coat"), Coat.CODEC);
	}

	/**
	 * From here on the Overworld's biome list holds the silk deeps. Called from the server and client entrypoints, after
	 * every mod's main entrypoint (as Jaymo's Powers places its ice caves): a mod that builds a vanilla-only world lookup in
	 * its main entrypoint would crash on a list that asks for our biome.
	 */
	public static void place() {
		placed = true;
	}

	/** Wraps the Overworld biome list as it's built: the silk deeps go in beside the dripstone caves (the first cave biome). */
	public static Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> placing(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out) {
		if (!placed) {
			return out;
		}
		return pair -> {
			out.accept(pair);
			if (pair.getSecond() == Biomes.DRIPSTONE_CAVES) {
				out.accept(Pair.of(Climate.parameters(Climate.Parameter.span(TEMP_MIN, TEMP_MAX), Climate.Parameter.span(HUMID_MIN, HUMID_MAX),
					Climate.Parameter.span(INLAND_MIN, INLAND_MAX), Climate.Parameter.span(EROSION_MIN, EROSION_MAX),
					Climate.Parameter.span(DEPTH_MIN, DEPTH_MAX), Climate.Parameter.span(WEIRD_MIN, WEIRD_MAX), OFFSET), SILK_DEEPS));
			}
		};
	}

	/**
	 * The silk deeps' coat, laid once over each chunk's caves (where they are in the biome): the rock round every open space
	 * turned to spider stone or nest silk, then skins, cobwebs and egg sacs in the open spaces. Only the land's own rock
	 * ({@code base_stone_overworld}) is turned, so ores, dirt and anything built stay as they are.
	 */
	public record Coat() implements Feature {
		public static final MapCodec<Coat> CODEC = MapCodec.unit(Coat::new);

		@Override
		public MapCodec<Coat> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			ChunkPos chunk = ChunkPos.containing(origin);
			int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ();
			int minY = Math.max(level.getMinY() + 2, COAT_MIN_Y), maxY = Math.min(level.getMaxY() - 2, COAT_MAX_Y);
			boolean any = false;
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			// a quart (4x4x4) at a time: the biome is kept per quart
			for (int qx = 0; qx < 4; qx++) {
				for (int qz = 0; qz < 4; qz++) {
					for (int qy = minY >> 2; qy <= maxY >> 2; qy++) {
						if (!level.getBiome(new BlockPos(x0 + qx * 4 + 2, qy * 4 + 2, z0 + qz * 4 + 2)).is(SILK_DEEPS)) {
							continue;
						}
						for (int dx = 0; dx < 4; dx++) {
							for (int dz = 0; dz < 4; dz++) {
								for (int dy = 0; dy < 4; dy++) {
									p.set(x0 + qx * 4 + dx, qy * 4 + dy, z0 + qz * 4 + dz);
									if (p.getY() >= minY && p.getY() <= maxY && level.getBlockState(p).isAir()) {
										any |= coat(level, random, p.immutable(), x0, z0);
									}
								}
							}
						}
					}
				}
			}
			return any;
		}

		/** The rock round one open spot, and what grows in it. */
		private static boolean coat(WorldGenLevel level, RandomSource random, BlockPos at, int x0, int z0) {
			boolean any = false;
			for (Direction d : Direction.values()) {
				BlockPos side = at.relative(d);
				if (side.getX() < x0 || side.getX() > x0 + 15 || side.getZ() < z0 || side.getZ() > z0 + 15) {
					continue;
				}
				BlockState rock = level.getBlockState(side);
				if (!rock.is(BlockTags.BASE_STONE_OVERWORLD)) {
					continue;
				}
				float silk = d == Direction.UP ? SILK_ROOF : d == Direction.DOWN ? SILK_FLOOR : SILK_WALL;
				level.setBlock(side, (random.nextFloat() < silk ? SpiderNests.NEST_SILK : SpiderNests.SPIDER_STONE).defaultBlockState(), Block.UPDATE_CLIENTS);
				any = true;
			}
			BlockState below = level.getBlockState(at.below()), above = level.getBlockState(at.above());
			boolean floor = below.isFaceSturdy(level, at.below(), Direction.UP), roof = above.isFaceSturdy(level, at.above(), Direction.DOWN);
			float roll = random.nextFloat();
			if (floor && level.getBlockState(at.above()).isAir()) {
				if (roll < FLOOR_EGG_CHANCE) {
					EggSacBlock.lay(level, at, false, FIRST_LOOK + random.nextInt(FIRST_LOOK));
				} else if (roll < FLOOR_EGG_CHANCE + SKIN_CHANCE) {
					level.setBlock(at, SpiderNests.SHED_SKIN.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			} else if (roof) {
				if (roll < HANG_EGG_CHANCE) {
					EggSacBlock.lay(level, at, true, FIRST_LOOK + random.nextInt(FIRST_LOOK));
				} else if (roll < HANG_EGG_CHANCE + HANG_WEB_CHANCE) {
					level.setBlock(at, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
					if (random.nextBoolean() && level.getBlockState(at.below()).isAir() && level.getBlockState(at.below(2)).isAir()) {
						level.setBlock(at.below(), Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			} else if (!floor && roll < WALL_WEB_CHANCE) {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (level.getBlockState(at.relative(d)).isSolid()) {
						level.setBlock(at, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
						break;
					}
				}
			}
			return any;
		}
	}
}
