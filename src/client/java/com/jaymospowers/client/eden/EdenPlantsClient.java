package com.jaymospowers.client.eden;

import com.jaymospowers.eden.Eden;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSources;

/** The Verdant World's small plants take the grass colour of their country on their leaves. (The items are tinted in their item definitions.) */
public final class EdenPlantsClient {
	private EdenPlantsClient() {
	}

	public static void init() {
		BlockColorRegistry.register(List.of(BlockTintSources.grass()), Eden.VIOLUM, Eden.LONLIX, Eden.PARIGNUM, Eden.LIMPHIUM, Eden.EDEN_VINE,
			Eden.EDEN_MOSS);
	}
}
