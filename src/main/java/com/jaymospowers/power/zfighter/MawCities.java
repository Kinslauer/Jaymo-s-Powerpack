package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import java.lang.reflect.Method;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * The ground under the Maw: the Lost Cities (MIT, by McJty; github.com/McJtyMods/LostCities; optional, shipped unchanged in the
 * pack, read by name, never linked), built ruined and emptied on the Maw's wasteland ({@code tools/gen_zfighter_space.py},
 * {@code maw_ground}), so the broken world ({@link MawWorld}) hangs over a country of streets and wrecked buildings stretching
 * away in every direction. As the future does ({@code FutureCities}): the city feature is added to the Maw's own biome alone, and
 * each server start tells Lost Cities to build in the Maw (it forgets between starts), with a profile of ours ({@link #RUINS},
 * set on Lost Cities' defaults) that it is handed at the same time. Without Lost Cities the wasteland is bare.
 */
final class MawCities {
	/** The profile's name in Lost Cities' list. */
	static final String PROFILE = "jaymospowers_maw";
	/**
	 * How the ruins are made, as Lost Cities profile fields (each boxed as the field's own type): nearly every building broken off
	 * low, rubble in the streets and no green anywhere, the water gone from fountains, bomb craters in and out of the cities, the
	 * cities large and running into each other (its "largecities" noise), buildings no taller than {@code BUILDING_MAXFLOORS}
	 * floors so they stay well under the broken world, and nothing left in them: no spawners, no loot, no lights.
	 */
	static final Map<String, Object> RUINS = Map.ofEntries(
		Map.entry("RUIN_CHANCE", 0.95F),
		Map.entry("RUIN_MINLEVEL_PERCENT", 0.15F),
		Map.entry("RUIN_MAXLEVEL_PERCENT", 0.7F),
		Map.entry("RUBBLE_DIRT_SCALE", 2.0F),
		Map.entry("RUBBLE_LEAVE_SCALE", 0.0F),
		Map.entry("VINE_CHANCE", 0.0F),
		Map.entry("CHANCE_OF_RANDOM_LEAFBLOCKS", 0.0F),
		Map.entry("AVOID_FOLIAGE", true),
		Map.entry("AVOID_WATER", true),
		Map.entry("PARK_CHANCE", 0.0F),
		Map.entry("OPEN_LOT_PARK_CHANCE", 0.0F),
		Map.entry("EXPLOSION_CHANCE", 0.02F),
		Map.entry("EXPLOSION_MINHEIGHT", 70),
		Map.entry("EXPLOSION_MAXHEIGHT", 110),
		Map.entry("MINI_EXPLOSION_CHANCE", 0.15F),
		Map.entry("MINI_EXPLOSION_MINHEIGHT", 60),
		Map.entry("MINI_EXPLOSION_MAXHEIGHT", 120),
		Map.entry("EXPLOSIONS_IN_CITIES_ONLY", false),
		Map.entry("CITY_CHANCE", -1.0),
		Map.entry("CITY_PERLIN_SCALE", 7.0),
		Map.entry("CITY_PERLIN_OFFSET", 0.2),
		Map.entry("CITY_PERLIN_INNERSCALE", 0.1),
		Map.entry("CITY_THRESHOLD", 0.1F),
		Map.entry("BUILDING_CHANCE", 0.4F),
		Map.entry("BUILDING_MAXFLOORS", 6),
		Map.entry("GENERATE_SPAWNERS", false),
		Map.entry("GENERATE_LOOT", false),
		Map.entry("GENERATE_LIGHTING", false));
	/** The Maw's biome (its id is still the old void's: chunks saved under it keep it). */
	static final ResourceKey<Biome> WASTES = ResourceKey.create(Registries.BIOME, JaymosPowers.id("maw_void"));
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded("lostcities");

	private MawCities() {
	}

	static void init() {
		if (!LOADED) {
			return;
		}
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(WASTES), GenerationStep.Decoration.RAW_GENERATION,
			ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("lostcities", "lostcities")));
		// after Lost Cities clears its dimension list on SERVER_STARTING, before anyone can reach the Maw
		ServerLifecycleEvents.SERVER_STARTED.register(MawCities::register);
	}

	@SuppressWarnings("unchecked")
	private static void register(MinecraftServer server) {
		ServerLevel maw = server.getLevel(ZFighterSpace.DESTS[ZFighterSpace.MAW].level());
		if (maw == null) {
			return;
		}
		try {
			Class<?> profileClass = Class.forName("mcjty.lostcities.config.LostCityProfile");
			Object profile = profileClass.getConstructor(String.class, boolean.class).newInstance(PROFILE, false);
			for (Map.Entry<String, Object> field : RUINS.entrySet()) {
				profileClass.getField(field.getKey()).set(profile, field.getValue());
			}
			((Map<String, Object>)Class.forName("mcjty.lostcities.config.ProfileSetup").getField("STANDARD_PROFILES").get(null)).put(PROFILE, profile);
			Object api = Class.forName("mcjty.lostcities.LostCities").getField("lostCitiesImp").get(null);
			Method registerDimension = Class.forName("mcjty.lostcities.api.ILostCities")
				.getMethod("registerDimension", ServerLevel.class, ResourceKey.class, String.class);
			// the overworld, so Lost Cities reads its own overworld choice from the right save
			registerDimension.invoke(api, server.overworld(), maw.dimension(), PROFILE);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException | IllegalArgumentException e) {
			JaymosPowers.LOGGER.warn("Lost Cities is loaded but the Maw couldn't be given its ruins", e);
		}
	}
}
