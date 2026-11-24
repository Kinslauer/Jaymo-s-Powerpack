package com.jaymospowers.client.body;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

/** Body animation and the stations' displays (Reference Mods patch 13): the furnaces' mouths, the reading lectern, faces that move. */
public final class BodyClient {
	private BodyClient() {
	}

	public static void init() {
		BlockEntityRenderers.register(BlockEntityTypes.FURNACE, FurnaceRenderer::new);
		BlockEntityRenderers.register(BlockEntityTypes.SMOKER, FurnaceRenderer::new);
		BlockEntityRenderers.register(BlockEntityTypes.BLAST_FURNACE, FurnaceRenderer::new);
		BlockEntityRenderers.register(BlockEntityTypes.LECTERN, LecternReadingRenderer::new);
		BodyFaces.register();
	}
}
