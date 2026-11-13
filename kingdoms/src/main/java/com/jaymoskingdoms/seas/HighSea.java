package com.jaymoskingdoms.seas;

import com.jaymoskingdoms.JaymosKingdoms;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The higher sea (Andy's ocean pick-list, "A higher, wavy sea"; he chose "waves and a higher sea", new worlds only):
 * in a world made since, the Overworld's sea stands {@link #RISE} blocks higher than the land was shaped for. Low
 * coasts and river flats go under, headlands become islands, and caves under the new sea fill (the game's own aquifers
 * flood caves below the sea's level where the ground over them is under it). The land isn't raised with it.
 * <p>
 * Only for the Overworld's own shapes (overworld, large biomes, amplified, as the registry has them when the server
 * starts), and only in a world made with it: a world's first start leaves a marker file in its folder, and a world
 * that has been played without it keeps its old sea (new chunks at a new level would stand as walls of water).
 * The sea's level reaches the client with the level itself, so ships, fishing and the rest follow it.
 */
public final class HighSea {
	/** How much higher the sea stands (blocks). */
	public static final int RISE = 8;
	/** The marker in a world's folder that says it was made with the higher sea. */
	static final String MARKER = "jaymoskingdoms-high-sea.txt";

	/** The Overworld's noise settings this server raises (none: an older world, or before a server starts). */
	private static final Set<NoiseGeneratorSettings> RAISED = Collections.newSetFromMap(new IdentityHashMap<>());

	private HighSea() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(HighSea::starting);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			synchronized (RAISED) {
				RAISED.clear();
			}
		});
	}

	private static void starting(MinecraftServer server) {
		synchronized (RAISED) {
			RAISED.clear();
		}
		Path marker = server.getWorldPath(LevelResource.ROOT).resolve(MARKER);
		boolean on = Files.exists(marker);
		if (!on && !server.getWorldData().overworldData().isInitialized()) {
			// a new world: made with the higher sea, and marked so
			try {
				Files.writeString(marker, "The sea in this world stands " + RISE + " blocks higher (Jaymo's Kingdoms).\n");
				on = true;
			} catch (IOException e) {
				JaymosKingdoms.LOGGER.warn("Couldn't mark the world for the higher sea; keeping the old sea", e);
			}
		}
		if (!on) {
			JaymosKingdoms.LOGGER.info("Higher sea: off (a world made before it)");
			return;
		}
		Registry<NoiseGeneratorSettings> settings = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
		synchronized (RAISED) {
			for (ResourceKey<NoiseGeneratorSettings> key : java.util.List.of(NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.LARGE_BIOMES,
				NoiseGeneratorSettings.AMPLIFIED)) {
				settings.getOptional(key).ifPresent(RAISED::add);
			}
		}
		JaymosKingdoms.LOGGER.info("Higher sea: on (the Overworld's sea stands {} blocks higher)", RISE);
	}

	/** The sea's level for {@code settings}, given the level it was written with. */
	public static int seaLevel(NoiseGeneratorSettings settings, int written) {
		synchronized (RAISED) {
			return RAISED.contains(settings) ? written + RISE : written;
		}
	}

	/** Whether {@code settings} is one this server raises. */
	public static boolean raised(NoiseGeneratorSettings settings) {
		synchronized (RAISED) {
			return RAISED.contains(settings);
		}
	}
}
