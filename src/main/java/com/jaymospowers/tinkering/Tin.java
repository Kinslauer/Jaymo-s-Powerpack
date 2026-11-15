package com.jaymospowers.tinkering;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.smeltery.Melting;
import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

/**
 * Andy's GT New Horizons picks, patch 4 (docs/GTNH-Plan.md, ss-tin): tin and a real bronze age. Tin ore (stone and
 * deepslate) lies in great veins in the mountains, laid by the same {@code ground/BigVeinFeature} as iron and copper
 * (worldgen/placed_feature/big_vein_tin.json: high up, rarer than copper, smaller than iron; big_vein_tin_deep.json: rarer
 * still, deep down in the deepslate), and dark tin sand lies in the river
 * beds (worldgen/placed_feature/tin_sand.json), where a pan turns up tin nuggets ({@code ground/Panning#TIN}). Tin melts
 * like any ore (doubled), and three of copper to one of tin alloy into bronze in the smeltery ({@link Melting#ALLOYS});
 * bronze is a Tinkering material between flint and iron ({@link TinkerMaterial#BRONZE}), cast into parts and plating.
 * Its ingot is one of {@link Tinkering#ALLOY_INGOTS}. Assets, data and lang: tools/gen_coke_and_steel.py.
 */
public final class Tin {
	public static Block TIN_ORE, DEEPSLATE_TIN_ORE, RAW_TIN_BLOCK, TIN_BLOCK, BRONZE_BLOCK, TIN_SAND;
	public static Item RAW_TIN, TIN_INGOT, TIN_NUGGET, TIN_DUST, BRONZE_INGOT, BRONZE_NUGGET;

	private Tin() {
	}

	/** From {@link Tinkering}, after the alloy ingots (bronze's among them) are registered. */
	static void register() {
		TIN_ORE = block("tin_ore", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(3.0F, 3.0F).requiresCorrectToolForDrops());
		DEEPSLATE_TIN_ORE = block("deepslate_tin_ore", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops());
		RAW_TIN_BLOCK = block("raw_tin_block", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.CLAY)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(5.0F, 6.0F).requiresCorrectToolForDrops());
		TIN_BLOCK = block("tin_block", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
			.instrument(NoteBlockInstrument.IRON_XYLOPHONE).strength(4.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
		BRONZE_BLOCK = block("bronze_block", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
			.instrument(NoteBlockInstrument.BELL).strength(5.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
		TIN_SAND = block("tin_sand", p -> new SandBlock(new ColorRGBA(0x6E6458), p), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
			.instrument(NoteBlockInstrument.SNARE).strength(0.6F).sound(SoundType.SAND));
		RAW_TIN = Tinkering.item("raw_tin", Item::new, Function.identity());
		TIN_INGOT = Tinkering.item("tin_ingot", Item::new, Function.identity());
		TIN_NUGGET = Tinkering.item("tin_nugget", Item::new, Function.identity());
		TIN_DUST = Tinkering.item("tin_dust", Item::new, Function.identity());
		BRONZE_INGOT = Tinkering.ALLOY_INGOTS.get(Molten.BRONZE);
		BRONZE_NUGGET = Tinkering.item("bronze_nugget", Item::new, Function.identity());
		Tinkering.ALLOY_INGOTS.put(Molten.TIN, TIN_INGOT);
		TinkerMaterial.own(TinkerMaterial.BRONZE, 9, BRONZE_BLOCK.asItem());
		Melting.tin(TIN_INGOT, TIN_NUGGET, TIN_BLOCK.asItem(), RAW_TIN, RAW_TIN_BLOCK.asItem(), TIN_ORE.asItem(), DEEPSLATE_TIN_ORE.asItem());
		Melting.alloyForms(Molten.BRONZE, BRONZE_NUGGET, BRONZE_BLOCK.asItem());
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_MOUNTAIN), GenerationStep.Decoration.UNDERGROUND_ORES, placed("big_vein_tin"));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_MOUNTAIN), GenerationStep.Decoration.UNDERGROUND_ORES, placed("big_vein_tin_deep"));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_RIVER), GenerationStep.Decoration.UNDERGROUND_ORES, placed("tin_sand"));
		CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("tinkering"))).register(output -> {
			for (Block block : List.of(TIN_ORE, DEEPSLATE_TIN_ORE, RAW_TIN_BLOCK, TIN_BLOCK, BRONZE_BLOCK, TIN_SAND)) {
				output.accept(block);
			}
			for (Item item : List.of(RAW_TIN, TIN_NUGGET, TIN_DUST, BRONZE_NUGGET)) {
				output.accept(item);
			}
		});
	}

	private static ResourceKey<PlacedFeature> placed(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(id));
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
