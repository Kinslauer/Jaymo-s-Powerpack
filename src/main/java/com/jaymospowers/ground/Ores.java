package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.ore.OreWashing;
import com.jaymospowers.tinkering.smeltery.Melting;
import com.jaymospowers.tinkering.smeltery.Molten;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * Andy's GT New Horizons picks, patch 7 (docs/GTNH-Plan.md, "Ores"; new world: only chunks made after it get the new ore):
 * <ul>
 * <li><b>Mixed veins</b> (or-mixed): every great vein ({@link BigVeinFeature}) carries streaks of a partner ore, and washing
 * its crushed ore ({@code tinkering/ore/OreWashing}) now and then gives up a <b>tiny dust</b> of that partner. Nine tiny
 * dusts make a dust (the mortar's metal dusts); one melts as a nugget's worth.</li>
 * <li><b>Small ores</b> (or-small): lone ore stones in the stone just under the ground ({@link #SHALLOW_MIN} to
 * {@link #SHALLOW_MOST} blocks down), one {@link Metal} each, stone and deepslate. Mined (with the tool their full ore
 * wants) they give one crushed ore, not raw ore. A few are scattered anywhere ({@link Scatter}, by {@link Metal#scatter});
 * many more lie over each great vein, mostly its own metal and some its partner's ({@link BigVeinFeature#HINT_TRIES}), so
 * the small ores a country shows lean toward the veins under it.</li>
 * <li><b>The dowsing rod</b> (or-dowse): {@link DowsingRodItem}.</li>
 * </ul>
 * Assets, data and lang: tools/gen_ores.py.
 */
public final class Ores {
	/** Lone small ores tried in each chunk, wherever it is (the veins' own come on top). */
	static final int SCATTER_TRIES = 1;
	/** How far under the top of the ground a lone small ore may lie (blocks). */
	static final int SHALLOW_MIN = 3, SHALLOW_MOST = 20;

	/** The metals that have small ores, their dowsing sample's tag, their crushed ore and how often the scatter picks them. */
	public enum Metal {
		IRON("iron", ConventionalBlockTags.IRON_ORES, OreWashing.Ore.IRON, 4),
		COPPER("copper", ConventionalBlockTags.COPPER_ORES, OreWashing.Ore.COPPER, 4),
		TIN("tin", tag("tin"), OreWashing.Ore.TIN, 1),
		GOLD("gold", ConventionalBlockTags.GOLD_ORES, OreWashing.Ore.GOLD, 1),
		SILVER("silver", tag("silver"), OreWashing.Ore.SILVER, 1);

		public final String id;
		/** Every ore block of this metal (its great veins' and its small ores), for the dowsing rod. */
		public final TagKey<Block> ores;
		public final OreWashing.Ore ore;
		final int scatter;
		public Block small, smallDeep;

		Metal(String id, TagKey<Block> ores, OreWashing.Ore ore, int scatter) {
			this.id = id;
			this.ores = ores;
			this.ore = ore;
			this.scatter = scatter;
		}

		private static TagKey<Block> tag(String metal) {
			return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores/" + metal));
		}

		/** Whether {@code stack} is a sample of this metal: its raw ore, its crushed ore or one of its small ores. */
		public boolean sample(ItemStack stack) {
			return stack.is(ore.raw()) || ore.crushed != null && stack.is(ore.crushed) || small != null && (stack.is(small.asItem())
				|| stack.is(smallDeep.asItem()));
		}

		/** The metal {@code stack} is a sample of, or null. */
		public static @Nullable Metal of(ItemStack stack) {
			for (Metal metal : values()) {
				if (metal.sample(stack)) {
					return metal;
				}
			}
			return null;
		}
	}

	public static Item TINY_IRON_DUST, TINY_COPPER_DUST, TINY_TIN_DUST, DOWSING_ROD;
	/** The dowsing rod's sample: the id of the item put in it. */
	public static DataComponentType<String> DOWSING_SAMPLE;

	private Ores() {
	}

	/** After {@link StoneAge#init} (and so after OreWashing, Tin and XenoFinds' dusts). */
	public static void init() {
		TINY_IRON_DUST = item("tiny_iron_dust", Item::new, Function.identity());
		TINY_COPPER_DUST = item("tiny_copper_dust", Item::new, Function.identity());
		TINY_TIN_DUST = item("tiny_tin_dust", Item::new, Function.identity());
		Melting.dusts(Molten.IRON, Molten.NUGGET, TINY_IRON_DUST);
		Melting.dusts(Molten.COPPER, Molten.NUGGET, TINY_COPPER_DUST);
		Melting.dusts(Molten.TIN, Molten.NUGGET, TINY_TIN_DUST);

		for (Metal metal : Metal.values()) {
			// mined like the metal's own ore: tools/gen_ores.py puts them in the same needs_*_tool tag
			metal.small = block("small_" + metal.id + "_ore", BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
				.instrument(NoteBlockInstrument.BASEDRUM).strength(3.0F, 3.0F).requiresCorrectToolForDrops());
			metal.smallDeep = block("deepslate_small_" + metal.id + "_ore", BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
				.instrument(NoteBlockInstrument.BASEDRUM).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops());
		}

		DOWSING_SAMPLE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("dowsing_sample"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		DOWSING_ROD = item("dowsing_rod", DowsingRodItem::new, p -> p.stacksTo(1));
		DowsingRodItem.init();

		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("small_ores"), Scatter.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id("small_ores")));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			for (Metal metal : Metal.values()) {
				output.accept(metal.small);
				output.accept(metal.smallDeep);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(TINY_IRON_DUST);
			output.accept(TINY_COPPER_DUST);
			output.accept(TINY_TIN_DUST);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(DOWSING_ROD));
	}

	/**
	 * A lone small ore in the column at {@code x, z}, {@link #SHALLOW_MIN} to {@link #SHALLOW_MOST} blocks under the top of
	 * the ground, if the block there is stone ({@code stone}) or deepslate ({@code deep}). Whether one was laid.
	 */
	static boolean lone(WorldGenLevel level, RandomSource random, int x, int z, BlockState stone, BlockState deep) {
		int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
		BlockPos at = new BlockPos(x, top - SHALLOW_MIN - random.nextInt(SHALLOW_MOST - SHALLOW_MIN + 1), z);
		if (at.getY() <= level.getMinY() || level.isOutsideBuildHeight(at)) {
			return false;
		}
		BlockState there = level.getBlockState(at);
		BlockState ore = there.is(BlockTags.STONE_ORE_REPLACEABLES) ? stone : there.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) ? deep : null;
		if (ore == null) {
			return false;
		}
		level.setBlock(at, ore, Block.UPDATE_CLIENTS);
		return true;
	}

	/** The scatter: a few lone small ores in every chunk, the metal picked by {@link Metal#scatter}. */
	public record Scatter() implements Feature {
		public static final MapCodec<Scatter> CODEC = MapCodec.unit(Scatter::new);

		@Override
		public MapCodec<Scatter> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			int total = 0;
			for (Metal metal : Metal.values()) {
				total += metal.scatter;
			}
			boolean placed = false;
			for (int i = 0; i < SCATTER_TRIES; i++) {
				int roll = random.nextInt(total);
				Metal pick = Metal.IRON;
				for (Metal metal : Metal.values()) {
					roll -= metal.scatter;
					if (roll < 0) {
						pick = metal;
						break;
					}
				}
				placed |= lone(level, random, (origin.getX() & ~15) + random.nextInt(16), (origin.getZ() & ~15) + random.nextInt(16),
					pick.small.defaultBlockState(), pick.smallDeep.defaultBlockState());
			}
			return placed;
		}
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Function<Item.Properties, Item.Properties> props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.apply(new Item.Properties().setId(key))));
	}

	private static Block block(String id, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
