package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * The Lost Cities (MIT, by McJty; github.com/McJtyMods/LostCities; optional, shipped unchanged in the pack, read by
 * name, never linked): beyond Los Perrito the future goes on as endless ruined city instead of open sea. Each server
 * start tells Lost Cities to build its {@link #PROFILE} profile in {@link Future#FUTURE} only (it forgets between
 * starts), and its city feature is added to the {@code future_ruins} biome alone, so no other overworld mod's features
 * or structures leak into the future. Its own bed-and-skulls trip to its {@code lostcities:lostcity} dimension is shut
 * off. Chunks it builds decay like the island's ({@link FutureRuins}).
 */
public final class FutureCities {
	/** Lost Cities' built-in profile: "Ancient jungle city, vines and leafs, ruined buildings" (90% of buildings ruined). */
	static final String PROFILE = "ancient";
	/** Lost Cities' teleporter bed must stand on this; bedrock, so no one can build one. */
	static final String BED_BLOCK = "minecraft:bedrock";
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("lostcities");

	private FutureCities() {
	}

	static void init() {
		if (!LOADED) {
			return;
		}
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(FutureRuins.RUINS), GenerationStep.Decoration.RAW_GENERATION,
			ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("lostcities", "lostcities")));
		// after Lost Cities clears its dimension list on SERVER_STARTING, before anyone can reach the future
		ServerLifecycleEvents.SERVER_STARTED.register(FutureCities::register);
	}

	private static void register(MinecraftServer server) {
		ServerLevel future = server.getLevel(Future.FUTURE);
		if (future == null) {
			return;
		}
		try {
			Object api = Class.forName("mcjty.lostcities.LostCities").getField("lostCitiesImp").get(null);
			Method registerDimension = Class.forName("mcjty.lostcities.api.ILostCities")
				.getMethod("registerDimension", ServerLevel.class, ResourceKey.class, String.class);
			// the overworld, so Lost Cities reads its own overworld choice from the right save
			registerDimension.invoke(api, server.overworld(), Future.FUTURE, PROFILE);
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosPowers.LOGGER.warn("Lost Cities is loaded but the future couldn't be given its cities", e);
		}
		try {
			Object bedBlock = Class.forName("mcjty.lostcities.setup.Config").getField("SPECIAL_BED_BLOCK").get(null);
			if (!BED_BLOCK.equals(bedBlock.getClass().getMethod("get").invoke(bedBlock))) {
				bedBlock.getClass().getMethod("set", Object.class).invoke(bedBlock, BED_BLOCK);
			}
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosPowers.LOGGER.warn("Couldn't shut off Lost Cities' teleporter bed", e);
		}
	}
}
