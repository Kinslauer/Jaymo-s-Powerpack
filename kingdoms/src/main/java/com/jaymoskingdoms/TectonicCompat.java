package com.jaymoskingdoms;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * Tectonic (MIT, by Apollo) rewrites the Overworld's terrain: its own continents, erosion, ridges, depth and noise
 * settings, in an always-on pack. Our continents (the shape in {@code jaymoskingdoms:continents/shape}, Mohknee's land, and the
 * land lifted with the higher sea) rewrite the same density functions, so with Tectonic in the pack ours stand down: the
 * shape files ({@code continents}, {@code erosion}, {@code ridges}, and the {@code offset} files) are in a built-in pack of
 * our own, {@code continent_shape}, which is only turned on when Tectonic is absent, and {@link com.jaymoskingdoms.seas.HighSea}
 * leaves a new world's sea where it was (the higher sea's land lift is in those offset files). Our climate map
 * ({@code temperature} and {@code vegetation}, with {@code continents/latitude}) stays in the mod's own data, as it was.
 * Taking the Tectonic jar out turns it all back on, for worlds made after; chunks already made keep their terrain.
 */
public final class TectonicCompat {
	public static final String MOD_ID = "tectonic";

	private TectonicCompat() {
	}

	/** Tectonic is in the pack. */
	public static boolean loaded() {
		return FabricLoader.getInstance().isModLoaded(MOD_ID);
	}

	public static void register() {
		if (loaded()) {
			JaymosKingdoms.LOGGER.info("Tectonic found: its terrain stands in place of the continents' shape (the climate map stays)");
			return;
		}
		boolean ok = ResourceLoader.registerBuiltinPack(JaymosKingdoms.id("continent_shape"),
			FabricLoader.getInstance().getModContainer(JaymosKingdoms.MOD_ID).orElseThrow(),
			Component.literal("Jaymo's Kingdoms: the continents' shape"), PackActivationType.ALWAYS_ENABLED);
		if (!ok) {
			JaymosKingdoms.LOGGER.error("The continents' shape pack is missing: the world will have the game's own continents");
		}
	}
}
