package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import java.util.function.Supplier;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.20.89) Andy (seven-mods plan, patch 4): ore tells, so a sharp eye can spot where to dig (Caverns &amp; Chasms' idea,
 * our own blocks). Stone touching an ore vein is often <b>seamed</b> (cracked, rusty-veined stone; seamed deepslate in
 * the deep), and where a vein lies just under the ground the dirt over it turns <b>rocky</b>. They mine like what they
 * are and drop cobblestone, cobbled deepslate and dirt. Placement: worldgen/placed_feature/ore_tells.json.
 * (0.20.102) Andy: "right now it isnt a scrying eye that could tell theres something there, ANYONE could tell, it should
 * instead be a chance that a pebble spawns where these blocks would have, and it is either colored copper or brown like
 * iron, or will drop you a nugget from that ore type". The rocky dirt is gone: over a shallow copper or iron vein there's
 * now sometimes a lone pebble on the ground, either stained the ore's colour or a plain-looking rock that's really a
 * nugget ({@link OrePebbleBlock}). Rocky dirt already in the world turns back into plain dirt on its own.
 */
public final class OreTells {
	public static Block SEAMED_STONE, SEAMED_DEEPSLATE, ROCKY_DIRT;
	/** (0.20.102) The pebbles over shallow veins: stained (gives a plain rock) and hidden (looks plain, gives a nugget). */
	public static Block COPPER_PEBBLE, IRON_PEBBLE, COPPER_NUGGET_PEBBLE, IRON_NUGGET_PEBBLE;
	/** Ore looked for underground in each chunk; seams round each found; columns looked down for a shallow vein. */
	static final int TRIES = 28, SURFACE_TRIES = 8;
	static final float SEAM_CHANCE = 0.55F, FAR_SEAM_CHANCE = 0.25F;
	static final int SHALLOW_MIN = 2, SHALLOW_MOST = 12;
	/** How often a shallow copper or iron vein shows a pebble at all, and how many of those are hidden nuggets. */
	static final float PEBBLE_CHANCE = 0.4F, NUGGET_SHARE = 0.5F;

	private OreTells() {
	}

	public static void init() {
		SEAMED_STONE = block("seamed_stone", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
		SEAMED_DEEPSLATE = block("seamed_deepslate", BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops()
			.strength(3.0F, 6.0F).sound(SoundType.DEEPSLATE));
		// (0.20.102) no longer laid down; what's in the world already slowly turns back into dirt
		ROCKY_DIRT = block("rocky_dirt", BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.6F).sound(SoundType.GRAVEL).randomTicks(),
			FadingDirt::new);
		Supplier<Item> rock = () -> GroundRocks.ROCKS.get(GroundRocks.Variant.STONE).asItem();
		COPPER_PEBBLE = pebble("copper_pebble", rock);
		IRON_PEBBLE = pebble("iron_pebble", rock);
		COPPER_NUGGET_PEBBLE = pebble("copper_nugget_pebble", () -> Items.COPPER_NUGGET);
		IRON_NUGGET_PEBBLE = pebble("iron_nugget_pebble", () -> Items.IRON_NUGGET);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("ore_tells"), Tells.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_DECORATION,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id("ore_tells")));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.accept(SEAMED_STONE);
			output.accept(SEAMED_DEEPSLATE);
		});
	}

	private static Block block(String name, BlockBehaviour.Properties properties) {
		return block(name, properties, Block::new);
	}

	private static Block block(String name, BlockBehaviour.Properties properties, Function<BlockBehaviour.Properties, Block> make) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	/** A pebble block, no item of its own (it picks up as a rock or a nugget). */
	private static Block pebble(String name, Supplier<Item> gives) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, new OrePebbleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instabreak()
			.noCollision().noOcclusion().sound(SoundType.STONE).pushReaction(PushReaction.POPPED).offsetType(BlockBehaviour.OffsetType.XZ)
			.setId(key), gives));
	}

	/** (0.20.102) Rocky dirt left over from before: a random tick turns it back into dirt (grass spreads back over it). */
	static final class FadingDirt extends Block {
		FadingDirt(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
		}
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
				BlockPos above = p.above();
				if (!level.getBlockState(above).isAir() || !Block.canSupportCenter(level, p, Direction.UP)) {
					continue;
				}
				for (int dy = SHALLOW_MIN; dy <= SHALLOW_MOST; dy++) {
					BlockState ore = level.getBlockState(p.below(dy));
					boolean copper = ore.is(ConventionalBlockTags.COPPER_ORES), iron = ore.is(ConventionalBlockTags.IRON_ORES);
					if (copper || iron) {
						if (random.nextFloat() < PEBBLE_CHANCE) {
							boolean nugget = random.nextFloat() < NUGGET_SHARE;
							Block pebble = copper ? (nugget ? COPPER_NUGGET_PEBBLE : COPPER_PEBBLE) : (nugget ? IRON_NUGGET_PEBBLE : IRON_PEBBLE);
							level.setBlock(above, pebble.defaultBlockState(), Block.UPDATE_CLIENTS);
							placed = true;
						}
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
