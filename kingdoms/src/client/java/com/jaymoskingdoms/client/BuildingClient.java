package com.jaymoskingdoms.client;

import com.jaymoskingdoms.PlotLines;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** (batch 4) The client's part in the building blocks: plot stakes' lines, the grass slab's tint. */
public final class BuildingClient {
	private BuildingClient() {
	}

	public static void register() {
		BlockEntityRenderers.register(PlotLines.ENTITY, StakeLineRenderer::new);
	}
}
