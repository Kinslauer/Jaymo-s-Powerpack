package com.jaymoskingdoms;

import com.jaymoskingdoms.disasters.Disasters;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Four pieces of land from Reference Mods patch 11, each a placed feature (data/jaymoskingdoms/worldgen) over a feature written
 * here. They read the ground the game made (our continents' shape and the game's own caves). Tectonic, which that patch tried
 * in the pack, left it again (Andy, 2026-11-25: it broke the caves and the climate map); these stayed, as none needed it.
 * <ul>
 * <li><b>Smugglers' lanterns</b> ({@link SmugglersCache}): under a roof, over still deep water (a flooded cave or an
 * underground lake), a few chained lanterns and a barrel of goods on the bank. The back rooms'
 * runners (the speakeasies' fences) bring their goods by the water the watch doesn't walk.</li>
 * <li><b>Pillar crowns</b> ({@link PillarCrown}): in jungles, on a pillar standing clear of the ground round it (one the land made,
 * or one raised here where there is none), a flat top with a nest, a hoard and broken walls. Left by the forgotten hands that raised
 * the jungle temples; who they were is left unnamed.</li>
 * <li><b>Volcanic islands</b> ({@link VolcanicIsland}): in warm, shallow seas, a cone of basalt with a crater of lava, black sand
 * and ash beaches. They are the mountains that wake ({@code disasters/Volcanoes}) standing in the sea.</li>
 * <li><b>Lava tunnels</b> ({@link LavaTunnel}): at the bottom of the world, where a cave has a lava floor, shroomlight on its roof, lava
 * seeping through the roof, magma on the banks and a deep ore in the wall.</li>
 * </ul>
 */
public final class TerrainFeatures {
	public static Block BLACK_SAND;
	public static Item BLACK_SAND_ITEM;

	/** The barrel on a smugglers' bank, and the hoard in a pillar's nest. */
	static final ResourceKey<LootTable> CACHE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, JaymosKingdoms.id("chests/smugglers_cache"));
	static final ResourceKey<LootTable> NEST_LOOT = ResourceKey.create(Registries.LOOT_TABLE, JaymosKingdoms.id("chests/pillar_nest"));

	// smugglers: how far down the water is looked for, how far up the roof, the roof must be this far under the surface
	static final int WATER_REACH = 24, ROOF_REACH = 14, ROOF_COVER = 4, LANTERN_TRIES = 3, LANTERN_SPREAD = 5;
	// pillars: the drop all round that makes a ground pillar, the ring it is measured at, our own shaft's height and radius, the crown's radius
	static final int PILLAR_DROP = 10, PILLAR_RING = 8, SHAFT_MIN = 22, SHAFT_EXTRA = 14, SHAFT_RADIUS = 3, CROWN_RADIUS = 4, CROWN_AIR = 7;
	// islands: its radius, the peak above the sea, the crater's radius and depth, the sea depth it may stand in, the beach's height above the sea
	static final int ISLAND_RADIUS = 13, PEAK = 12, CRATER_RADIUS = 3, CRATER_DEPTH = 3, MIN_DEPTH = 4, MAX_DEPTH = 22, BEACH = 2;
	static final double SLOPE = 1.5;
	// lava tunnels: how far down the lava is looked for, how far up the roof, and the chances (1 in n) of each hazard
	static final int LAVA_REACH = 14, LAVA_ROOF = 12, SEEP_ONE_IN = 3, MAGMA_ONE_IN = 2, ORE_ONE_IN = 6;

	private TerrainFeatures() {
	}

	public static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("black_sand"));
		BLACK_SAND = Registry.register(BuiltInRegistries.BLOCK, key, new ColoredFallingBlock(new ColorRGBA(0x2A2A2E),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.SAND).setId(key)));
		ResourceKey<Item> item = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("black_sand"));
		BLACK_SAND_ITEM = Registry.register(BuiltInRegistries.ITEM, item, new BlockItem(BLACK_SAND, new Item.Properties().setId(item).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> output.insertAfter(Items.RED_SAND, BLACK_SAND_ITEM));

		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("smugglers_cache"), SmugglersCache.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("pillar_crown"), PillarCrown.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("volcanic_island"), VolcanicIsland.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("lava_tunnel"), LavaTunnel.CODEC);

		// under any land, in a flooded cave; in the jungles; in the warm shallow seas; at the bottom of the world
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_DECORATION, placed("smugglers_cache"));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE),
			GenerationStep.Decoration.TOP_LAYER_MODIFICATION, placed("pillar_crown"));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN),
			GenerationStep.Decoration.TOP_LAYER_MODIFICATION, placed("volcanic_island"));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_DECORATION, placed("lava_tunnel"));
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosKingdoms.id(name));
	}

	private static boolean air(WorldGenLevel level, BlockPos pos) {
		return level.getBlockState(pos).isAir();
	}

	private static boolean solid(WorldGenLevel level, BlockPos pos, Direction face) {
		return level.getBlockState(pos).isFaceSturdy(level, pos, face);
	}

	/** Sets a block, leaving bedrock and anything with a block entity alone. */
	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		BlockState old = level.getBlockState(pos);
		if (!old.is(Blocks.BEDROCK) && !old.hasBlockEntity()) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	// ------------------------------------------------------------------------------------------------ smugglers

	/**
	 * Where: any underground, over deep still water with a roof over it, one chunk in a few. Why: the speakeasies' smugglers run goods by the
	 * water the watch doesn't walk, and leave a light to steer by.
	 */
	public record SmugglersCache() implements Feature {
		public static final MapCodec<SmugglersCache> CODEC = MapCodec.unit(SmugglersCache::new);

		@Override
		public MapCodec<SmugglersCache> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!air(level, origin)) {
				return false;
			}
			BlockPos.MutableBlockPos p = origin.mutable();
			BlockPos water = null;
			for (int i = 0; i < WATER_REACH; i++) {
				if (level.getFluidState(p.below()).is(FluidTags.WATER)) {
					water = p.immutable();
					break;
				}
				if (!air(level, p.below())) {
					return false;
				}
				p.move(Direction.DOWN);
			}
			if (water == null || !level.getFluidState(water.below(2)).is(FluidTags.WATER)) {
				return false;
			}
			// a river, not a puddle: water three blocks out on most sides
			int wet = 0;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (level.getFluidState(water.relative(d, 3).below()).is(FluidTags.WATER)) {
					wet++;
				}
			}
			if (wet < 3 || RemainsFeature.village(level, water)) {
				return false;
			}
			for (BlockPos near : BlockPos.betweenClosed(water.offset(-6, 1, -6), water.offset(6, ROOF_REACH, 6))) {
				if (level.getBlockState(near).is(Blocks.LANTERN) || level.getBlockState(near).is(Blocks.SOUL_LANTERN)) {
					return false;
				}
			}
			int hung = 0;
			for (int i = 0; i < LANTERN_TRIES; i++) {
				hung += hang(level, random, water.getX() + random.nextInt(2 * LANTERN_SPREAD + 1) - LANTERN_SPREAD, water.getY(),
					water.getZ() + random.nextInt(2 * LANTERN_SPREAD + 1) - LANTERN_SPREAD) ? 1 : 0;
			}
			if (hung == 0) {
				return false;
			}
			bank(level, random, water);
			return true;
		}

		/** A lantern on a chain from the roof over (x, z), if there is a roof, air to hang in, and the ground above is thick. */
		private static boolean hang(WorldGenLevel level, RandomSource random, int x, int waterY, int z) {
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, waterY + 1, z);
			int up = 0;
			while (up < ROOF_REACH && air(level, p)) {
				p.move(Direction.UP);
				up++;
			}
			if (up < 4 || up >= ROOF_REACH || !solid(level, p, Direction.DOWN) || level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) < p.getY() + ROOF_COVER) {
				return false;
			}
			int roof = p.getY();
			int chain = 1 + random.nextInt(Math.max(1, up - 3));
			for (int i = 1; i <= chain + 1; i++) {
				if (!air(level, new BlockPos(x, roof - i, z))) {
					return false;
				}
			}
			for (int i = 1; i <= chain; i++) {
				set(level, new BlockPos(x, roof - i, z), Blocks.IRON_CHAIN.defaultBlockState());
			}
			Block lantern = random.nextInt(3) == 0 ? Blocks.SOUL_LANTERN : Blocks.LANTERN;
			set(level, new BlockPos(x, roof - chain - 1, z), lantern.defaultBlockState().setValue(LanternBlock.HANGING, true));
			return true;
		}

		/** A barrel of the smugglers' goods on the nearest dry bank at the water's level. */
		private static void bank(WorldGenLevel level, RandomSource random, BlockPos water) {
			for (int i = 0; i < 24; i++) {
				BlockPos at = water.offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5);
				if (!air(level, at) || !solid(level, at.below(), Direction.UP) || !level.getFluidState(at.below()).isEmpty()) {
					continue;
				}
				boolean shore = false;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					shore |= level.getFluidState(at.relative(d).below()).is(FluidTags.WATER);
				}
				if (shore) {
					set(level, at, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
					RandomizableContainer.setBlockEntityLootTable(level, random, at, CACHE_LOOT);
					return;
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ pillars

	/**
	 * Where: jungles, one chunk in a few dozen, on a pillar of stone standing {@link #PILLAR_DROP} blocks over the ground on every side (one
	 * the land made), or on one raised here where there is none. Why: the jungle temples' builders left their marks on the heights.
	 */
	public record PillarCrown() implements Feature {
		public static final MapCodec<PillarCrown> CODEC = MapCodec.unit(PillarCrown::new);

		@Override
		public MapCodec<PillarCrown> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			int cx = (origin.getX() & ~15) + 8, cz = (origin.getZ() & ~15) + 8;
			int centre = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
			if (RemainsFeature.village(level, new BlockPos(cx, centre, cz)) || level.getFluidState(new BlockPos(cx, centre - 1, cz)).is(FluidTags.WATER)) {
				return false;
			}
			int drop = Integer.MAX_VALUE;
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx + (int)Math.round(Math.cos(a) * PILLAR_RING), cz + (int)Math.round(Math.sin(a) * PILLAR_RING));
				drop = Math.min(drop, centre - h);
			}
			int top;
			if (drop >= PILLAR_DROP) {
				top = centre - 1;
			} else {
				top = centre - 1 + SHAFT_MIN + random.nextInt(SHAFT_EXTRA);
				if (top + CROWN_AIR >= level.getMaxY()) {
					return false;
				}
				shaft(level, random, cx, cz, centre - 3, top);
			}
			crown(level, random, cx, cz, top);
			return true;
		}

		private static void shaft(WorldGenLevel level, RandomSource random, int cx, int cz, int from, int top) {
			for (int y = from; y <= top; y++) {
				for (int dx = -SHAFT_RADIUS - 1; dx <= SHAFT_RADIUS + 1; dx++) {
					for (int dz = -SHAFT_RADIUS - 1; dz <= SHAFT_RADIUS + 1; dz++) {
						if (dx * dx + dz * dz > SHAFT_RADIUS * SHAFT_RADIUS + random.nextInt(3) - 1) {
							continue;
						}
						BlockPos at = new BlockPos(cx + dx, y, cz + dz);
						int roll = random.nextInt(10);
						set(level, at, (roll < 4 ? Blocks.STONE : roll < 6 ? Blocks.ANDESITE : roll < 9 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
						if (random.nextInt(14) == 0) {
							Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
							BlockPos vine = at.relative(d);
							if (air(level, vine)) {
								set(level, vine, Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d.getOpposite()), true));
							}
						}
					}
				}
			}
		}

		private static void crown(WorldGenLevel level, RandomSource random, int cx, int cz, int top) {
			for (int dx = -CROWN_RADIUS; dx <= CROWN_RADIUS; dx++) {
				for (int dz = -CROWN_RADIUS; dz <= CROWN_RADIUS; dz++) {
					int d2 = dx * dx + dz * dz;
					if (d2 > CROWN_RADIUS * CROWN_RADIUS) {
						continue;
					}
					for (int y = top + CROWN_AIR; y > top; y--) {
						BlockPos up = new BlockPos(cx + dx, y, cz + dz);
						if (!air(level, up)) {
							set(level, up, Blocks.AIR.defaultBlockState());
						}
					}
					for (int y = top - 1; y >= top - 5 && air(level, new BlockPos(cx + dx, y, cz + dz)); y--) {
						set(level, new BlockPos(cx + dx, y, cz + dz), Blocks.COBBLESTONE.defaultBlockState());
					}
					Block floor = random.nextInt(4) == 0 ? Blocks.CRACKED_STONE_BRICKS : random.nextInt(3) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS;
					set(level, new BlockPos(cx + dx, top, cz + dz), floor.defaultBlockState());
					// the nest: a ring of hay and moss round the hoard
					if (d2 >= 1 && d2 <= 5) {
						set(level, new BlockPos(cx + dx, top + 1, cz + dz), (random.nextInt(3) == 0 ? Blocks.MOSS_BLOCK : Blocks.HAY_BLOCK).defaultBlockState());
					} else if (d2 >= 10 && random.nextInt(5) == 0) {
						// broken walls round the rim
						Block wall = random.nextBoolean() ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS;
						int height = 1 + random.nextInt(3);
						for (int h = 1; h <= height; h++) {
							set(level, new BlockPos(cx + dx, top + h, cz + dz), wall.defaultBlockState());
						}
					}
				}
			}
			BlockPos chest = new BlockPos(cx, top + 1, cz);
			set(level, chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
			RandomizableContainer.setBlockEntityLootTable(level, random, chest, NEST_LOOT);
		}
	}

	// ------------------------------------------------------------------------------------------------ islands

	/**
	 * Where: warm, lukewarm and deep lukewarm seas where the floor is {@link #MIN_DEPTH} to {@link #MAX_DEPTH} under the sea and open
	 * water all round, one chunk in a few dozen. Why: the hot lands' mountains wake as volcanoes, and some stand in the sea.
	 */
	public record VolcanicIsland() implements Feature {
		public static final MapCodec<VolcanicIsland> CODEC = MapCodec.unit(VolcanicIsland::new);

		@Override
		public MapCodec<VolcanicIsland> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			int cx = (origin.getX() & ~15) + 8, cz = (origin.getZ() & ~15) + 8;
			int sea = level.getSeaLevel();
			int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, cx, cz);
			int depth = sea - floor;
			if (depth < MIN_DEPTH || depth > MAX_DEPTH) {
				return false;
			}
			// open water all round, no shore, no other island
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				int x = cx + (int)Math.round(Math.cos(a) * (ISLAND_RADIUS + 2)), z = cz + (int)Math.round(Math.sin(a) * (ISLAND_RADIUS + 2));
				if (level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) < 2) {
					return false;
				}
			}
			double phaseA = random.nextDouble() * Math.TAU, phaseB = random.nextDouble() * Math.TAU;
			int topAtRim = sea + PEAK - (int)Math.round((PEAK + depth) * Math.pow(1 - (double)CRATER_RADIUS / ISLAND_RADIUS, SLOPE));
			for (int dx = -ISLAND_RADIUS - 2; dx <= ISLAND_RADIUS + 2; dx++) {
				for (int dz = -ISLAND_RADIUS - 2; dz <= ISLAND_RADIUS + 2; dz++) {
					double angle = Math.atan2(dz, dx);
					double d = Math.sqrt(dx * dx + dz * dz) * (1 + 0.16 * Math.sin(angle * 3 + phaseA) + 0.1 * Math.sin(angle * 5 + phaseB));
					if (d >= ISLAND_RADIUS) {
						continue;
					}
					int x = cx + dx, z = cz + dz;
					int colFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
					double t = 1 - d / ISLAND_RADIUS;
					int top = floor + (int)Math.round((sea + PEAK - floor) * Math.pow(t, SLOPE));
					boolean crater = d < CRATER_RADIUS;
					if (crater) {
						top = Math.max(topAtRim - CRATER_DEPTH, top - (int)Math.round(CRATER_DEPTH * (1 - d / CRATER_RADIUS)) - 1);
					}
					for (int y = colFloor; y <= top; y++) {
						set(level, new BlockPos(x, y, z), body(random, y, top, sea, d));
					}
					if (crater) {
						for (int y = top + 1; y < topAtRim; y++) {
							set(level, new BlockPos(x, y, z), Blocks.LAVA.defaultBlockState());
						}
					} else if (top > sea && top <= sea + BEACH + 2) {
						// the ash beach: layers of ash over the black sand
						set(level, new BlockPos(x, top + 1, z), Disasters.ASH_LAYER.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(3)));
					}
				}
			}
			return true;
		}

		private static BlockState body(RandomSource random, int y, int top, int sea, double d) {
			if (top <= sea + BEACH && y >= top - 2 && y >= sea - 4) {
				return BLACK_SAND.defaultBlockState();
			}
			int roll = random.nextInt(20);
			if (y == top && d < CRATER_RADIUS + 2 && roll < 5) {
				return Blocks.MAGMA_BLOCK.defaultBlockState();
			}
			return (roll < 7 ? Blocks.BASALT : roll < 13 ? Blocks.BLACKSTONE : roll < 18 ? Blocks.SMOOTH_BASALT : Blocks.TUFF).defaultBlockState();
		}
	}

	// ------------------------------------------------------------------------------------------------ lava tunnels

	/**
	 * Where: the bottom of the world (the placed feature keeps to the lowest thirty blocks), in any cave with lava for a floor, which is
	 * what the game's own deep lava lakes and lava-floored caves are. Why: the deep ores lie there; a miner finds them by the glow and
	 * pays for them in burns.
	 */
	public record LavaTunnel() implements Feature {
		public static final MapCodec<LavaTunnel> CODEC = MapCodec.unit(LavaTunnel::new);

		@Override
		public MapCodec<LavaTunnel> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!air(level, origin)) {
				return false;
			}
			BlockPos.MutableBlockPos p = origin.mutable();
			BlockPos surface = null;
			for (int i = 0; i < LAVA_REACH; i++) {
				if (level.getFluidState(p.below()).is(FluidTags.LAVA)) {
					surface = p.immutable();
					break;
				}
				if (!air(level, p.below())) {
					return false;
				}
				p.move(Direction.DOWN);
			}
			if (surface == null) {
				return false;
			}
			p.set(surface);
			for (int i = 0; i < LAVA_ROOF && air(level, p); i++) {
				p.move(Direction.UP);
			}
			if (!solid(level, p, Direction.DOWN) || level.getBlockState(p).is(Blocks.BEDROCK)) {
				return false;
			}
			BlockPos roof = p.immutable();
			// lit: shroomlight in the roof
			set(level, roof, Blocks.SHROOMLIGHT.defaultBlockState());
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = roof.relative(d);
				if (random.nextInt(3) == 0 && solid(level, side, Direction.DOWN) && !level.getBlockState(side).is(Blocks.BEDROCK)) {
					set(level, side, Blocks.SHROOMLIGHT.defaultBlockState());
				}
			}
			// dangerous: lava seeping through the roof elsewhere, magma on the banks
			if (random.nextInt(SEEP_ONE_IN) == 0) {
				BlockPos seep = roof.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
				if (solid(level, seep, Direction.DOWN) && !level.getBlockState(seep).is(Blocks.BEDROCK) && air(level, seep.below())) {
					set(level, seep, Blocks.LAVA.defaultBlockState());
					level.scheduleTick(seep, Fluids.LAVA, 20);
				}
			}
			if (random.nextInt(MAGMA_ONE_IN) == 0) {
				for (int i = 0; i < 6; i++) {
					BlockPos at = surface.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
					boolean bank = false;
					for (Direction d : Direction.Plane.HORIZONTAL) {
						bank |= level.getFluidState(at.relative(d).below()).is(FluidTags.LAVA);
					}
					if (bank && air(level, at) && solid(level, at.below(), Direction.UP)) {
						set(level, at.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
					}
				}
			}
			// the deep ore: one in the wall, in the light
			if (random.nextInt(ORE_ONE_IN) == 0) {
				for (int i = 0; i < 12; i++) {
					BlockPos at = surface.offset(random.nextInt(9) - 4, random.nextInt(4), random.nextInt(9) - 4);
					BlockState state = level.getBlockState(at);
					if (state.is(Blocks.DEEPSLATE) && (air(level, at.above()) || air(level, at.north()) || air(level, at.south()) || air(level, at.east()) || air(level, at.west()))) {
						set(level, at, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState());
						break;
					}
				}
			}
			return true;
		}
	}
}
