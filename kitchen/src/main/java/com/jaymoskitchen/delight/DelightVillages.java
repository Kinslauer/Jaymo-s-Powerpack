package com.jaymoskitchen.delight;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.mixin.StructureProcessorListAccessor;
import com.jaymoskitchen.mixin.StructureTemplatePoolAccessor;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

/**
 * (Farmer's Delight's village additions, MIT; our own code.) Villages get a compost heap among their houses (a pile of
 * organic compost and rich soil, with a few crops, in each biome's own style), and their farms plant cabbages,
 * tomatoes and onions among the wheat. Done when the server starts, as the data packs have loaded then, the way
 * Farmer's Delight does it: the pools and processor lists vanilla loads are added to in place.
 */
public final class DelightVillages {
	/** Each biome's village houses pool, our compost heap for it, and how often it comes up (as one of the vanilla houses). */
	private static final String[] POOLS = {"plains", "snowy", "savanna", "desert", "taiga"};
	private static final int[] PILE_WEIGHTS = {5, 3, 4, 3, 4};
	/** The chance a wheat (or potato, in the snowy farm) plant in a village farm comes up as one of ours, for each of the three. */
	private static final float WHEAT_SWAP = 0.3F, POTATO_SWAP = 0.2F;

	private DelightVillages() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(DelightVillages::addToVillages);
	}

	private static void addToVillages(MinecraftServer server) {
		Registry<StructureTemplatePool> pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		Registry<StructureProcessorList> processors = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST);
		for (int i = 0; i < POOLS.length; i++) {
			addHouse(pools, processors, Identifier.withDefaultNamespace("village/" + POOLS[i] + "/houses"),
				JaymosKitchen.MOD_ID + ":village/houses/" + POOLS[i] + "_compost_pile", PILE_WEIGHTS[i]);
		}

		StructureProcessor temperate = crops(
			swap(Blocks.WHEAT, WHEAT_SWAP, Delight.CABBAGES), swap(Blocks.WHEAT, WHEAT_SWAP, DelightFarm.TOMATOES),
			swap(Blocks.WHEAT, WHEAT_SWAP, DelightFarm.ONIONS));
		StructureProcessor cold = crops(
			swap(Blocks.WHEAT, WHEAT_SWAP, Delight.CABBAGES), swap(Blocks.WHEAT, WHEAT_SWAP, DelightFarm.ONIONS),
			swap(Blocks.POTATOES, POTATO_SWAP, Delight.CABBAGES), swap(Blocks.POTATOES, POTATO_SWAP, DelightFarm.ONIONS));
		StructureProcessor arid = crops(
			swap(Blocks.WHEAT, WHEAT_SWAP, Delight.CABBAGES), swap(Blocks.WHEAT, WHEAT_SWAP, DelightFarm.TOMATOES));
		addCrops(processors, "farm_plains", temperate);
		addCrops(processors, "farm_savanna", arid);
		addCrops(processors, "farm_snowy", cold);
		addCrops(processors, "farm_taiga", temperate);
		addCrops(processors, "farm_desert", arid);
	}

	/** Puts a house of ours in a village pool, {@code weight} times over (as the vanilla pools list a piece). */
	private static void addHouse(Registry<StructureTemplatePool> pools, Registry<StructureProcessorList> processors, Identifier poolId,
		String house, int weight) {
		StructureTemplatePool pool = pools.getValue(poolId);
		if (pool == null) {
			return;
		}
		Holder<StructureProcessorList> none = processors.getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.withDefaultNamespace("empty")));
		SinglePoolElement piece = SinglePoolElement.single(house, none).apply(StructureTemplatePool.Projection.RIGID);
		StructureTemplatePoolAccessor access = (StructureTemplatePoolAccessor) pool;
		for (int i = 0; i < weight; i++) {
			access.jaymoskitchen$templates().add(piece);
		}
		List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(access.jaymoskitchen$rawTemplates());
		raw.add(new Pair<>(piece, weight));
		access.jaymoskitchen$setRawTemplates(raw);
	}

	private static void addCrops(Registry<StructureProcessorList> processors, String list, StructureProcessor add) {
		processors.getOptional(Identifier.withDefaultNamespace(list)).ifPresent(existing -> {
			List<StructureProcessor> all = new ArrayList<>(existing.list());
			all.add(add);
			((StructureProcessorListAccessor) existing).jaymoskitchen$setList(all);
		});
	}

	private static StructureProcessor crops(ProcessorRule... rules) {
		return new RuleProcessor(List.of(rules));
	}

	/** One village plant in {@code chance} comes up as ours, a fresh sprout. */
	private static ProcessorRule swap(Block was, float chance, Block now) {
		return new ProcessorRule(new RandomBlockMatchTest(was, chance), AlwaysTrueTest.INSTANCE, now.defaultBlockState());
	}
}
