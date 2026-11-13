package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Andy: "add pebbles for them that spawn too, as well as deposits or however you think they should be acquired". The
 * two minerals of true black powder, found where they really are, so a marksman's powder can be made without bone meal:
 * <ul>
 * <li><b>Sulphur</b> comes up with heat. Ore deep in the overworld's stone and deepslate (from the bottom up to y 8, thickest
 * half way), and plenty in the Nether's netherrack, more again in the basalt deltas. Yellow crust lies loose on cave
 * floors beside lava and magma, on the Nether's floors, and round the rims of hot springs.</li>
 * <li><b>Saltpetre</b> (nitre) comes out of dry ground. Ore in the stone and sandstone under deserts, badlands and
 * savanna, and a thinner scatter in any cave's stone; white crust lies loose on dry sand and terracotta, and on the floors
 * of dry caves (where bats roost).</li>
 * </ul>
 * Over a shallow vein of either, the ore tells' pebble is the mineral itself ({@link OreTells}): it blooms at the surface.
 * Both are soft (any pickaxe). Three saltpetre, a sulphur and a charcoal grind into four gunpowder
 * (recipe/gunpowder_from_saltpetre.json); Kingdoms' powderheads use the same mix when they have it. Saltpetre can also
 * be grown the old way, in a nitre bed ({@link NitreBedBlock}).
 * Placement: worldgen/placed_feature/ore_sulphur*.json, ore_saltpetre*.json and *_crust.json.
 */
public final class PowderMinerals {
	public static Item SULPHUR, SALTPETRE;
	/** Scraped off a ripe nitre bed; boiled down (smelted) into saltpetre. */
	public static Item NITRE_EARTH;
	/** The saltpetre bed, the old way of making saltpetre ({@link NitreBedBlock}). */
	public static Block NITRE_BED;
	public static Block SULPHUR_ORE, DEEPSLATE_SULPHUR_ORE, NETHER_SULPHUR_ORE, SALTPETRE_ORE, SANDSTONE_SALTPETRE_ORE;
	/** Loose lumps of the mineral lying on the ground; a right click picks one up. */
	public static Block SULPHUR_PEBBLE, SALTPETRE_PEBBLE;
	public static final TagKey<Block> SULPHUR_ORES = TagKey.create(Registries.BLOCK, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "ores/sulphur"));
	public static final TagKey<Block> SALTPETRE_ORES = TagKey.create(Registries.BLOCK, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "ores/saltpetre"));

	// ---- tuning (ore counts and heights are in the placed features) ----
	/** Columns tried per chunk for loose crust, and pebbles in a cluster (1 to this) and how far they spread. */
	static final int CRUST_TRIES = 4, MAX_PEBBLES = 3, SPREAD = 2;
	/** Deep sulphur: how far a floor may be from lava or magma, and the heights looked at. */
	static final int HEAT_REACH = 3, DEEP_MIN_Y = -58, DEEP_MAX_Y = 16;
	/** Cave nitre: the heights looked at, and how low a cave's roof must be (open sky gets none). */
	static final int CAVE_MIN_Y = 0, CAVE_MAX_Y = 60, CAVE_ROOF = 10;
	/** Nether crust: chance a tried column gets some (in 100), and in the basalt deltas or by lava. */
	static final int NETHER_CHANCE = 30, NETHER_HOT_CHANCE = 80;
	/** Of a hot spring's rim blocks, this many in 100 get a lump of sulphur on them. */
	public static final int SPRING_RIM_SULPHUR = 18;

	private PowderMinerals() {
	}

	public static void init() {
		SULPHUR = item("sulphur");
		SALTPETRE = item("saltpetre");
		NITRE_EARTH = item("nitre_earth");
		ResourceKey<Block> bedKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("nitre_bed"));
		NITRE_BED = Registry.register(BuiltInRegistries.BLOCK, bedKey, new NitreBedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT)
			.strength(0.6F).sound(SoundType.ROOTED_DIRT).setId(bedKey)));
		ResourceKey<Item> bedItem = ResourceKey.create(Registries.ITEM, JaymosPowers.id("nitre_bed"));
		Registry.register(BuiltInRegistries.ITEM, bedItem, new BlockItem(NITRE_BED, new Item.Properties().setId(bedItem).useBlockDescriptionPrefix()));
		SULPHUR_ORE = block("sulphur_ore", ore(MapColor.STONE, 2.0F));
		DEEPSLATE_SULPHUR_ORE = block("deepslate_sulphur_ore", ore(MapColor.DEEPSLATE, 3.5F).sound(SoundType.DEEPSLATE));
		NETHER_SULPHUR_ORE = block("nether_sulphur_ore", ore(MapColor.NETHER, 1.5F).sound(SoundType.NETHER_ORE));
		SALTPETRE_ORE = block("saltpetre_ore", ore(MapColor.STONE, 2.0F));
		SANDSTONE_SALTPETRE_ORE = block("sandstone_saltpetre_ore", ore(MapColor.SAND, 1.2F));
		SULPHUR_PEBBLE = pebble("sulphur_pebble", MapColor.COLOR_YELLOW, () -> SULPHUR);
		SALTPETRE_PEBBLE = pebble("saltpetre_pebble", MapColor.SNOW, () -> SALTPETRE);

		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("cave_crust"), Crust.CAVE_CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("dry_crust"), Crust.DRY_CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("nether_crust"), Crust.NETHER_CODEC);
		var dry = BiomeSelectors.foundInOverworld().and(BiomeSelectors.tag(ConventionalBiomeTags.IS_DRY_OVERWORLD));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, placed("ore_sulphur"));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, placed("ore_saltpetre"));
		BiomeModifications.addFeature(dry, GenerationStep.Decoration.UNDERGROUND_ORES, placed("ore_saltpetre_dry"));
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Decoration.UNDERGROUND_ORES, placed("ore_sulphur_nether"));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.BASALT_DELTAS), GenerationStep.Decoration.UNDERGROUND_ORES,
			placed("ore_sulphur_deltas"));
		// loose crust: after the ores, so a shallow vein's bloom can sit over it
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_DECORATION, placed("cave_crust"));
		BiomeModifications.addFeature(dry, GenerationStep.Decoration.VEGETAL_DECORATION, placed("saltpetre_crust"));
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Decoration.VEGETAL_DECORATION, placed("nether_sulphur_crust"));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			for (Block ore : new Block[] {SULPHUR_ORE, DEEPSLATE_SULPHUR_ORE, NETHER_SULPHUR_ORE, SALTPETRE_ORE, SANDSTONE_SALTPETRE_ORE}) {
				output.accept(ore);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(SULPHUR);
			output.accept(SALTPETRE);
			output.accept(NITRE_EARTH);
			output.accept(NITRE_BED);
		});
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(name));
	}

	private static BlockBehaviour.Properties ore(MapColor colour, float hardness) {
		return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
			.strength(hardness, 3.0F);
	}

	private static Item item(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	private static Block block(String name, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	/** A loose lump, no item of its own (it picks up as the mineral). */
	private static Block pebble(String name, MapColor colour, java.util.function.Supplier<Item> gives) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, new OrePebbleBlock(BlockBehaviour.Properties.of().mapColor(colour).instabreak()
			.noCollision().noOcclusion().sound(SoundType.TUFF).pushReaction(PushReaction.POPPED).offsetType(BlockBehaviour.OffsetType.XZ)
			.setId(key), gives));
	}

	/** The mineral pebble a shallow vein of {@code ore} blooms into at the surface, if it's one of ours. */
	public static Block bloomOf(BlockState ore) {
		return ore.is(SULPHUR_ORES) ? SULPHUR_PEBBLE : ore.is(SALTPETRE_ORES) ? SALTPETRE_PEBBLE : null;
	}

	/** Lays a lump of sulphur on a hot spring's rim block, now and then. */
	public static void springRim(WorldGenLevel level, BlockPos rim, RandomSource random) {
		BlockPos on = rim.above();
		if (SULPHUR_PEBBLE != null && random.nextInt(100) < SPRING_RIM_SULPHUR && level.getBlockState(on).isAir()) {
			level.setBlock(on, SULPHUR_PEBBLE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Which crust a placed feature lays. */
	public enum Kind {
		/** Sulphur on cave floors near lava or magma, and white nitre on dry cave floors (overworld, underground). */
		CAVE,
		/** Saltpetre crust on dry sand, red sand, terracotta and sandstone (deserts, badlands, savanna). */
		DRY,
		/** Sulphur on the Nether's floors, most of it in the basalt deltas and by lava. */
		NETHER
	}

	public record Crust(Kind kind) implements Feature {
		public static final MapCodec<Crust> CAVE_CODEC = MapCodec.unit(() -> new Crust(Kind.CAVE));
		public static final MapCodec<Crust> DRY_CODEC = MapCodec.unit(() -> new Crust(Kind.DRY));
		public static final MapCodec<Crust> NETHER_CODEC = MapCodec.unit(() -> new Crust(Kind.NETHER));

		@Override
		public MapCodec<Crust> codec() {
			return switch (this.kind) {
				case CAVE -> CAVE_CODEC;
				case DRY -> DRY_CODEC;
				case NETHER -> NETHER_CODEC;
			};
		}

		@Override
		public boolean place(final WorldGenLevel level, final ChunkGenerator generator, final RandomSource random, final BlockPos origin) {
			int cx = origin.getX() & ~15, cz = origin.getZ() & ~15;
			boolean placed = false;
			for (int i = 0; i < CRUST_TRIES; i++) {
				int x = cx + random.nextInt(16), z = cz + random.nextInt(16);
				placed |= switch (this.kind) {
					case CAVE -> cave(level, random, x, z);
					case DRY -> dry(level, random, x, z);
					case NETHER -> nether(level, random, x, z);
				};
			}
			return placed;
		}

		/** One cave floor in this column: sulphur if heat is near and it's deep, nitre if it's a dry cave higher up. */
		private static boolean cave(WorldGenLevel level, RandomSource random, int x, int z) {
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, DEEP_MIN_Y, z);
			int surface = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 4;
			int start = DEEP_MIN_Y + random.nextInt(Math.max(1, CAVE_MAX_Y - DEEP_MIN_Y));
			for (int y = start; y < Math.min(CAVE_MAX_Y, surface); y++) {
				at.setY(y);
				if (!floor(level, at, BlockTags.BASE_STONE_OVERWORLD)) {
					continue;
				}
				if (y <= DEEP_MAX_Y && hot(level, at)) {
					return cluster(level, random, at.immutable(), SULPHUR_PEBBLE, BlockTags.BASE_STONE_OVERWORLD);
				}
				if (y >= CAVE_MIN_Y && roofed(level, at) && !wet(level, at)) {
					return cluster(level, random, at.immutable(), SALTPETRE_PEBBLE, BlockTags.BASE_STONE_OVERWORLD);
				}
				return false;
			}
			return false;
		}

		private static boolean dry(WorldGenLevel level, RandomSource random, int x, int z) {
			BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
			BlockState ground = level.getBlockState(at.below());
			if (!level.getBlockState(at).isAir() || !dryGround(ground) || random.nextInt(3) != 0) {
				return false;
			}
			return cluster(level, random, at, SALTPETRE_PEBBLE, null);
		}

		private static boolean nether(WorldGenLevel level, RandomSource random, int x, int z) {
			java.util.List<BlockPos> floors = new java.util.ArrayList<>();
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, NetherRocksFeature.MIN_Y, z);
			for (int y = NetherRocksFeature.MIN_Y; y <= NetherRocksFeature.MAX_Y; y++) {
				at.setY(y);
				if (floor(level, at, BlockTags.BASE_STONE_NETHER)) {
					floors.add(at.immutable());
				}
			}
			if (floors.isEmpty()) {
				return false;
			}
			BlockPos spot = floors.get(random.nextInt(floors.size()));
			boolean hot = level.getBiome(spot).is(Biomes.BASALT_DELTAS) || hot(level, spot);
			if (random.nextInt(100) >= (hot ? NETHER_HOT_CHANCE : NETHER_CHANCE)) {
				return false;
			}
			return cluster(level, random, spot, SULPHUR_PEBBLE, BlockTags.BASE_STONE_NETHER);
		}

		/** 1 to {@link #MAX_PEBBLES} lumps round {@code first}, each on a free floor of the same kind. */
		private static boolean cluster(WorldGenLevel level, RandomSource random, BlockPos first, Block pebble, TagKey<Block> stone) {
			level.setBlock(first, pebble.defaultBlockState(), Block.UPDATE_CLIENTS);
			int more = random.nextInt(MAX_PEBBLES);
			for (int i = 0; i < more; i++) {
				BlockPos near = first.offset(random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD);
				for (int dy = 1; dy >= -1; dy--) {
					BlockPos p = near.above(dy);
					if (stone == null ? level.getBlockState(p).isAir() && dryGround(level.getBlockState(p.below()))
						&& Block.canSupportCenter(level, p.below(), Direction.UP) : floor(level, p, stone)) {
						level.setBlock(p, pebble.defaultBlockState(), Block.UPDATE_CLIENTS);
						break;
					}
				}
			}
			return true;
		}

		/** Air with no fluid, over solid natural stone of the kind given. */
		private static boolean floor(WorldGenLevel level, BlockPos pos, TagKey<Block> stone) {
			if (!level.getBlockState(pos).isAir()) {
				return false;
			}
			BlockState ground = level.getBlockState(pos.below());
			boolean natural = ground.is(stone) || stone == BlockTags.BASE_STONE_NETHER && (ground.is(BlockTags.NYLIUM) || ground.is(Blocks.SOUL_SOIL));
			return natural && Block.canSupportCenter(level, pos.below(), Direction.UP);
		}

		/** Lava or magma within {@link #HEAT_REACH} along the floor or just under it. */
		private static boolean hot(WorldGenLevel level, BlockPos pos) {
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-HEAT_REACH, -2, -HEAT_REACH), pos.offset(HEAT_REACH, 1, HEAT_REACH))) {
				BlockState s = level.getBlockState(p);
				if (s.is(Blocks.MAGMA_BLOCK) || s.getFluidState().is(Fluids.LAVA)) {
					return true;
				}
			}
			return false;
		}

		/** Stone overhead within {@link #CAVE_ROOF}: a cave, not a ravine floor open to the rain. */
		private static boolean roofed(WorldGenLevel level, BlockPos pos) {
			for (int dy = 1; dy <= CAVE_ROOF; dy++) {
				if (!level.getBlockState(pos.above(dy)).isAir()) {
					return level.getBlockState(pos.above(dy)).is(BlockTags.BASE_STONE_OVERWORLD);
				}
			}
			return false;
		}

		/** Water dripping or standing close by washes nitre away. */
		private static boolean wet(WorldGenLevel level, BlockPos pos) {
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 3, 2))) {
				if (level.getFluidState(p).is(Fluids.WATER) || level.getBlockState(p).is(Blocks.POINTED_DRIPSTONE)) {
					return true;
				}
			}
			return false;
		}

		private static boolean dryGround(BlockState ground) {
			return ground.is(BlockTags.SAND) || ground.is(BlockTags.TERRACOTTA) || ground.is(Blocks.SANDSTONE) || ground.is(Blocks.RED_SANDSTONE)
				|| ground.is(Blocks.COARSE_DIRT);
		}
	}
}
