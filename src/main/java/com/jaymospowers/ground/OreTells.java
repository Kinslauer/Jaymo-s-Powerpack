package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

/**
 * (0.20.88) Andy (seven-mods plan, patch 4): ore tells, so a sharp eye can spot where to dig (Caverns &amp; Chasms' idea,
 * our own blocks). Stone touching an ore vein is often <b>seamed</b> (cracked, rusty-veined stone; seamed deepslate in
 * the deep), and where a vein lies just under the ground the dirt over it turns <b>rocky</b>. They mine like what they
 * are and drop cobblestone, cobbled deepslate and dirt. Placement: worldgen/placed_feature/ore_tells.json.
 */
public final class OreTells {
	public static Block SEAMED_STONE, SEAMED_DEEPSLATE, ROCKY_DIRT;
	/** Ore looked for underground in each chunk; seams round each found; columns looked down for a shallow vein. */
	static final int TRIES = 28, SURFACE_TRIES = 8;
	static final float SEAM_CHANCE = 0.55F, FAR_SEAM_CHANCE = 0.25F;
	static final int SHALLOW_MIN = 2, SHALLOW_MOST = 12;

	private OreTells() {
	}

	public static void init() {
		SEAMED_STONE = block("seamed_stone", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
		SEAMED_DEEPSLATE = block("seamed_deepslate", BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops()
			.strength(3.0F, 6.0F).sound(SoundType.DEEPSLATE));
		ROCKY_DIRT = block("rocky_dirt", BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.6F).sound(SoundType.GRAVEL));
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("ore_tells"), Tells.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_DECORATION,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id("ore_tells")));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.accept(SEAMED_STONE);
			output.accept(SEAMED_DEEPSLATE);
			output.accept(ROCKY_DIRT);
		});
	}

	private static Block block(String name, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	/** The feature: once a chunk. */
	public record Tells() implements Feature {
		public static final MapCodec<Tells> CODEC = MapCodec.unit(Tells::new);

		@Override
		public MapCodec<Tells> codec() {
			return CODEC;
		}

		@Override
		public boolean place(final WorldGenLevel level, final ChunkGenerator generator, final RandomSource random, final BlockPos origin) {
			int cx = origin.getX() & ~15, cz = origin.getZ() & ~15;
			boolean placed = false;
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int i = 0; i < TRIES; i++) {
				int x = cx + random.nextInt(16), z = cz + random.nextInt(16);
				int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
				int bottom = level.getMinY() + 4;
				if (top <= bottom) {
					continue;
				}
				p.set(x, bottom + random.nextInt(top - bottom), z);
				if (!level.getBlockState(p).is(ConventionalBlockTags.ORES)) {
					continue;
				}
				for (Direction d : Direction.values()) {
					BlockPos n = p.relative(d);
					if (random.nextFloat() < SEAM_CHANCE && seam(level, n)) {
						placed = true;
						if (random.nextFloat() < FAR_SEAM_CHANCE) {
							seam(level, n.relative(d));
						}
					}
				}
			}
			for (int i = 0; i < SURFACE_TRIES; i++) {
				int x = cx + random.nextInt(16), z = cz + random.nextInt(16);
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
				p.set(x, top, z);
				BlockState ground = level.getBlockState(p);
				if (!(ground.is(Blocks.DIRT) || ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.PODZOL))) {
					continue;
				}
				for (int dy = SHALLOW_MIN; dy <= SHALLOW_MOST; dy++) {
					if (level.getBlockState(p.below(dy)).is(ConventionalBlockTags.ORES)) {
						level.setBlock(p, ROCKY_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
						placed = true;
						break;
					}
				}
			}
			return placed;
		}

		/** Seams the stone at {@code pos}, if it's plain stone of the overworld. */
		private static boolean seam(WorldGenLevel level, BlockPos pos) {
			BlockState s = level.getBlockState(pos);
			if (s.is(Blocks.DEEPSLATE) || s.is(Blocks.TUFF)) {
				level.setBlock(pos, SEAMED_DEEPSLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
				return true;
			}
			if (s.is(Blocks.STONE) || s.is(Blocks.ANDESITE) || s.is(Blocks.GRANITE) || s.is(Blocks.DIORITE)) {
				level.setBlock(pos, SEAMED_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				return true;
			}
			return false;
		}
	}
}
