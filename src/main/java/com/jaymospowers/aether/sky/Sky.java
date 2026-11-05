package com.jaymospowers.aether.sky;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * (0.20.96) Andy (seven-mods plan, patch 10): the Aether sky. Updraft vents ({@link UpdraftVentBlock}), light and heavy
 * pockets and the Hestveil's gas ({@link SkyPockets}, with the {@code sky_mask} bauble), upfalls ({@link UpfallBlock}), the
 * sky warden and its arena ({@link SkyWarden}), the watched vault ({@link VaultHeartEntity}), and the worldgen that places
 * them and dresses the four sky biomes ({@link SkyFeature}; the biomes themselves are data, from tools/gen_aether_world.py).
 */
public final class Sky {
	private Sky() {
	}

	public static void init() {
		SkyBlocks.init();
		SkyMobs.init();
		SkyPockets.init();
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("sky"), SkyFeature.CODEC);
	}
}
