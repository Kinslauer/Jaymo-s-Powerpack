package com.jaymospowers.client.aether;

import com.jaymospowers.aether.AetherFlora;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSources;

/** The Aether II ground plants take the biome's grass colour, like vanilla grass and ferns. (The items are tinted in their item definitions.) */
public final class AetherFloraClient {
	private AetherFloraClient() {
	}

	public static void init() {
		BlockColorRegistry.register(List.of(BlockTintSources.grass()), AetherFlora.AETHER_SHORT_GRASS, AetherFlora.AETHER_MEDIUM_GRASS,
			AetherFlora.AETHER_LONG_GRASS, AetherFlora.BUSH_FERN, AetherFlora.POTTED.get("bush_fern"));
	}
}
