package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Tiv: "this mod has sticks on the ground you can grab. alongside the pebbles. we need the same so starting is more
 * manageable." Loose sticks lie about the overworld's soil, mostly under trees, so a start needn't fell a tree for its
 * first sticks (the knife, the flint hatchet): a pile of 1-4 (LooseSticksBlock), a punch takes the pile, a right click
 * one stick. Where and how often: LooseSticksFeature. Assets: tools/gen_loose_sticks.py.
 */
public final class GroundSticks {
	public static Block LOOSE_STICKS;

	private GroundSticks() {
	}

	public static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("loose_sticks"));
		LOOSE_STICKS = Registry.register(BuiltInRegistries.BLOCK, key, new LooseSticksBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.instabreak().noCollision().noOcclusion().sound(SoundType.WOOD).pushReaction(PushReaction.POPPED)
			.offsetType(BlockBehaviour.OffsetType.XZ).setId(key)));

		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("loose_sticks"), LooseSticksFeature.CODEC);
		var land = BiomeSelectors.foundInOverworld()
			.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
			.and(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN).negate());
		BiomeModifications.addFeature(land, GenerationStep.Decoration.VEGETAL_DECORATION,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id("loose_sticks")));
	}
}
