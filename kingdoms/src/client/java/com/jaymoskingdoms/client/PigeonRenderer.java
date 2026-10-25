package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Pigeon;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.resources.Identifier;

/** (0.7.200) A messenger pigeon ({@link Pigeon}): the parrot's model in a rock dove's grey, its neck sheened green and violet. */
final class PigeonRenderer extends ParrotRenderer {
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/pigeon.png");

	private PigeonRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	static void register() {
		EntityRenderers.register(Pigeon.TYPE, PigeonRenderer::new);
	}

	@Override
	public Identifier getTextureLocation(ParrotRenderState state) {
		return TEXTURE;
	}
}
