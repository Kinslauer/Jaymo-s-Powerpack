package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.flux.FluxEffects;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/**
 * Client side of {@link FluxEffects}: the wisp (drawn as its flat glowing sprite, always fully lit, by vanilla's
 * thrown-item renderer), the thrown Purifying Salts, and the corruption's senses ({@link CorruptionClient}).
 */
public final class FluxClient {
	private FluxClient() {
	}

	public static void init() {
		EntityRenderers.register(FluxEffects.WISP, ctx -> new ThrownItemRenderer<>(ctx, 1.2F, true));
		EntityRenderers.register(FluxEffects.SALTS_ENTITY, ThrownItemRenderer::new);
		CorruptionClient.init();
	}
}
