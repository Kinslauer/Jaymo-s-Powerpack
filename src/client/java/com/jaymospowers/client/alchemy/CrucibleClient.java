package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.crucible.Crucibles;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Client side of the crucible: the liquid in the pot and in the jars. */
public final class CrucibleClient {
	private CrucibleClient() {
	}

	public static void init() {
		BlockEntityRenderers.register(Crucibles.CRUCIBLE_ENTITY, CrucibleRenderer::new);
		BlockEntityRenderers.register(Crucibles.JAR_ENTITY, JarRenderer::new);
	}
}
