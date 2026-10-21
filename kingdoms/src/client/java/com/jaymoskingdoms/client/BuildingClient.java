package com.jaymoskingdoms.client;

import com.jaymoskingdoms.EarthSlabs;
import com.jaymoskingdoms.PlotLines;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** (batch 4) The client's part in the building blocks: plot stakes' lines, the grass slab's tint. */
public final class BuildingClient {
	private BuildingClient() {
	}

	public static void register() {
		BlockEntityRenderers.register(PlotLines.ENTITY, StakeLineRenderer::new);
		// tinted as a grass block is (its top and side overlay)
		BlockColorRegistry.register(List.of(BlockTintSources.grassBlock()), EarthSlabs.GRASS_SLAB);
	}
}
