package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.21.53) Andy: "sticks scattered on the ground so starting out doesn't need a tree."
 * Small piles of 1-4 vanilla sticks lie on soil (grass, podzol, dirt, coarse/rooted dirt, mud, moss, mycelium).
 * They generate under trees (leaves within 12 blocks overhead) and rarely in the open.
 * Punch to break the whole pile (drops one stick per stick), right-click to pick up one stick at a time.
 * Assets: tools/gen_loose_sticks.py.
 */
public final class GroundSticks {
	public static Block LOOSE_STICKS;

	private GroundSticks() {
	}

	public static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("loose_sticks"));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("loose_sticks"));
		LOOSE_STICKS = Registry.register(BuiltInRegistries.BLOCK, key,
			LooseSticksBlock.create(sticks().setId(key)));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(LOOSE_STICKS, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("loose_sticks"), LooseSticksFeature.CODEC);

		var land = BiomeSelectors.foundInOverworld()
			.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
			.and(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN).negate());
		BiomeModifications.addFeature(land, GenerationStep.Decoration.VEGETAL_DECORATION, placed("loose_sticks"));
	}

	private static BlockBehaviour.Properties sticks() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instabreak().noCollision().noOcclusion()
			.sound(SoundType.GRASS).pushReaction(PushReaction.POPPED).offsetType(BlockBehaviour.OffsetType.XZ);
	}

	private static ResourceKey<PlacedFeature> placed(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(id));
	}
}