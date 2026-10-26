package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.infusion.Infusion;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Client side of the infusion altar: the item turning above each pedestal. */
public final class InfusionClient {
	private InfusionClient() {
	}

	public static void init() {
		BlockEntityRenderers.register(Infusion.PEDESTAL_ENTITY, PedestalRenderer::new);
	}
}
