package com.jaymospowers.power.zfighter;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.mixin.ZFighterBiomeSourceAccessor;
import com.jaymospowers.power.zfighter.mixin.ZFighterChunkGeneratorAccessor;
import com.jaymospowers.power.zfighter.mixin.ZFighterMultiNoiseAccessor;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.impl.biome.NetherBiomeData;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Keeps the demon realm's biomes (and every other biome a mod gave Fabric's {@code NetherBiomes}) in a Nether whose biome
 * list is written out in full. Fabric adds Nether biomes only to the vanilla Nether preset; Incendium's
 * {@code the_nether.json} lists its 13 biomes outright, so with it in the pack the Gloam, the Ash and the Abyss would
 * stop generating. At {@code SERVER_STARTING} (before any level is made from its stem) each dimension whose generator holds
 * a written-out multi-noise list with the Nether Wastes in it gets the missing biomes added at their climate points, and
 * its biome set and feature order (both cached, the feature order already filled by the client's world-open check) are
 * made again. A stacked world's Nether band builds from the same stem, so it is covered too.
 */
public final class ZFighterNetherList {
	private ZFighterNetherList() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(ZFighterNetherList::fill);
	}

	private static void fill(MinecraftServer server) {
		Registry<LevelStem> stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
		HolderGetter<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
		for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : stems.entrySet()) {
			ChunkGenerator generator = entry.getValue().generator();
			if (!(generator.getBiomeSource() instanceof MultiNoiseBiomeSource source)) {
				continue;
			}
			ZFighterMultiNoiseAccessor noise = (ZFighterMultiNoiseAccessor)source;
			Optional<Climate.ParameterList<Holder<Biome>>> written = noise.jaymospowers$parameters().left();
			if (written.isEmpty() || written.get().values().stream().noneMatch(p -> p.getSecond().is(Biomes.NETHER_WASTES))) {
				continue;
			}
			List<Pair<Climate.ParameterPoint, Holder<Biome>>> had = written.get().values();
			// every biome Fabric was asked for (its own call, so another mod's are kept too), looked up in this world's biomes
			// (as Optionals: the list it builds won't take a missing one as null)
			Climate.ParameterList<Optional<Holder.Reference<Biome>>> asked = NetherBiomeData.withModdedBiomeEntries(
				new Climate.ParameterList<>(had.stream().map(p -> Pair.of(p.getFirst(), Optional.<Holder.Reference<Biome>>empty())).toList()),
				biomes::get);
			List<Pair<Climate.ParameterPoint, Holder<Biome>>> added = asked.values().stream()
				.filter(p -> p.getSecond().isPresent() && had.stream().noneMatch(h -> h.getSecond().is(p.getSecond().get().key())))
				.map(p -> Pair.of(p.getFirst(), (Holder<Biome>)p.getSecond().get()))
				.toList();
			if (added.isEmpty()) {
				continue;
			}
			List<Pair<Climate.ParameterPoint, Holder<Biome>>> all = new java.util.ArrayList<>(had);
			all.addAll(added);
			noise.jaymospowers$setParameters(Either.left(new Climate.ParameterList<>(all)));
			ZFighterBiomeSourceAccessor cached = (ZFighterBiomeSourceAccessor)source;
			cached.jaymospowers$setPossibleBiomes(Suppliers.memoize(
				() -> cached.jaymospowers$collectPossibleBiomes().distinct().collect(ImmutableSet.toImmutableSet())));
			Function<Holder<Biome>, BiomeGenerationSettings> settings = ((ZFighterChunkGeneratorAccessor)generator).jaymospowers$generationSettingsGetter();
			((ZFighterChunkGeneratorAccessor)generator).jaymospowers$setFeaturesPerStep(Suppliers.memoize(
				() -> FeatureSorter.buildFeaturesPerStep(List.copyOf(source.possibleBiomes()), b -> settings.apply(b).features(), true)));
			JaymosPowers.LOGGER.info("Nether biome list in {}: added {}", entry.getKey().identifier(),
				added.stream().map(p -> p.getSecond().getRegisteredName()).toList());
		}
	}
}
