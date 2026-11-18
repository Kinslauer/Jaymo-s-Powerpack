package com.jaymospowers.stoneores;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Ores in the stone they grow in (Clouds and Bits, world bits). Coal, iron, copper, gold, redstone, lapis, diamond and
 * emerald each come in andesite, diorite, granite and tuff: {@code andesite_coal_ore} and so on, 32 blocks. Each is the plain
 * ore in everything but its look: it has the plain ore's hardness, tool tier, experience and loot table (so fortune and the
 * plain drops apply; silk touch gives the plain ore), smelts and blasts to the same (data/jaymospowers/recipe/stone_ores),
 * and is in the same tags (minecraft:*_ores and c:ores/*, block and item), which is how pickaxe tiers, the prospector and
 * the smeltery ({@code tinkering/smeltery/Melting}, by {@code ItemTags.IRON_ORES} and the like) take it for the plain ore.
 * <p>
 * Where they come from: the vanilla ore features of coal, lapis, redstone and emerald are overridden by
 * data/minecraft/worldgen/feature/ore_*.json, which lay the variant first wherever the stone is one of the four; the great
 * veins ({@code ground/BigVeinFeature}) call {@link #variant} for the stone they run through. Diamond ore is never laid
 * (see {@code ground/BigVeins}), but the block exists for veins and commands. On top of those, {@link OreVeinFeature} lays
 * large coal veins in andesite and tuff (now and then a coal block) and gold and quartz veins in the Nether (now and then
 * a raw gold block): worldgen/placed_feature/stone_ores/*. New chunks only. Assets, data and lang come from
 * tools/gen_stone_ores.py.
 */
public final class StoneOres {
	/** The stones an ore can take its look from, in the order the blocks are made. */
	public static final List<String> STONES = List.of("andesite", "diorite", "granite", "tuff");
	/** The eight ores (the id stem of the plain ore and its deepslate kind). */
	private static final List<String> ORES = List.of("coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald");

	/** Plain ore block (stone and deepslate) to host stone block to the variant. */
	private static final Map<Block, Map<Block, Block>> VARIANTS = new HashMap<>();
	/** Every variant block, for the creative tab. */
	private static final List<Block> ALL = new ArrayList<>();

	private StoneOres() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("ore_vein"), OreVeinFeature.CODEC);
		for (String ore : ORES) {
			Block plain = vanilla(ore + "_ore");
			Block deep = vanilla("deepslate_" + ore + "_ore");
			Map<Block, Block> byHost = new HashMap<>();
			for (String stone : STONES) {
				Block made = block(stone + "_" + ore + "_ore", ore, plain, stone.equals("tuff"));
				byHost.put(vanilla(stone), made);
				ALL.add(made);
			}
			VARIANTS.put(plain, byHost);
			VARIANTS.put(deep, byHost);
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			for (Block block : ALL) {
				output.accept(block);
			}
		});
		// the big coal veins of andesite and tuff, in the Overworld
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, placed("stone_ores/coal_vein_andesite"));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, placed("stone_ores/coal_vein_tuff"));
		// and the Nether's gold and quartz
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Decoration.UNDERGROUND_ORES, placed("stone_ores/nether_gold_vein"));
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Decoration.UNDERGROUND_ORES, placed("stone_ores/nether_quartz_vein"));
	}

	/**
	 * What this ore turns into when it is laid in {@code host}: its variant if the host is andesite, diorite, granite or tuff and
	 * the ore is one of the eight plain ones (stone or deepslate), else the ore itself.
	 */
	public static BlockState variant(BlockState ore, BlockState host) {
		Map<Block, Block> byHost = VARIANTS.get(ore.getBlock());
		if (byHost == null) {
			return ore;
		}
		Block made = byHost.get(host.getBlock());
		return made == null ? ore : made.defaultBlockState();
	}

	private static Block vanilla(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
	}

	private static Block block(String id, String ore, Block plain, boolean tuff) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(plain).overrideLootTable(plain.getLootTable()).setId(key);
		if (tuff) {
			properties.sound(SoundType.TUFF);
		}
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, switch (ore) {
			case "coal" -> new DropExperienceBlock(UniformInt.of(0, 2), properties);
			case "lapis" -> new DropExperienceBlock(UniformInt.of(2, 5), properties);
			case "diamond", "emerald" -> new DropExperienceBlock(UniformInt.of(3, 7), properties);
			case "redstone" -> new RedStoneOreBlock(properties);
			default -> new Block(properties);
		});
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static ResourceKey<PlacedFeature> placed(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(id));
	}
}
