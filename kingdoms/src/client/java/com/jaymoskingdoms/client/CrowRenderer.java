package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Crow;
import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.resources.Identifier;

/**
 * The hanging tree's {@link Crow}: the parrot's model in the familiar raven's coat ({@code textures/entity/familiar/raven.png},
 * drawn the way {@code FamiliarClient} draws a raven), whatever variant the parrot underneath picked.
 */
public final class CrowRenderer extends ParrotRenderer {
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/familiar/raven.png");

	public CrowRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public Identifier getTextureLocation(ParrotRenderState state) {
		return TEXTURE;
	}
}
