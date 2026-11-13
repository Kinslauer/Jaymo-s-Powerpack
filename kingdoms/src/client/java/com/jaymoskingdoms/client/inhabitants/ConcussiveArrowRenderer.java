package com.jaymoskingdoms.client.inhabitants;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.inhabitants.ConcussiveArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/** A concussive arrow in flight or stuck: a vanilla arrow's shape, with Inhabitants' spike-headed texture. */
final class ConcussiveArrowRenderer extends ArrowRenderer<ConcussiveArrow, ArrowRenderState> {
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/concussive_arrow.png");

	ConcussiveArrowRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected Identifier getTextureLocation(ArrowRenderState state) {
		return TEXTURE;
	}

	@Override
	public ArrowRenderState createRenderState() {
		return new ArrowRenderState();
	}
}
