package com.jaymoskitchen.client.rats;

import com.jaymoskitchen.rats.Rats;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** The rats' and the traps' renderers (see rats/Rats). */
public final class RatsClient {
	private RatsClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(RatModel.LAYER, RatModel::createLayer);
		ModelLayerRegistry.registerModelLayer(SnapTrapRenderer.LAYER, SnapTrapRenderer::createLayer);
		EntityRendererRegistry.register(Rats.RAT, RatRenderer::new);
		BlockEntityRenderers.register(Rats.SNAP_TRAP_ENTITY, SnapTrapRenderer::new);
		BlockEntityRenderers.register(Rats.CAGE_TRAP_ENTITY, CageTrapRenderer::new);
	}
}
