package com.jaymoskingdoms.client.glowwood;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Draws a creature's emissive texture at full brightness (additive, like eyes): black texels add nothing. The
 * texture and its strength (0-1, e.g. to pulse or fade) come from the entity.
 */
public class GlowLayer<E extends LivingEntity, M extends EntityModel<CreatureRenderState<E>>>
		extends RenderLayer<CreatureRenderState<E>, M> {
	private static final int FULL_BRIGHT = 0xF000F0;
	private final Function<E, Identifier> texture;
	private final ToDoubleFunction<E> strength;

	public GlowLayer(RenderLayerParent<CreatureRenderState<E>, M> parent, Function<E, Identifier> texture,
					 ToDoubleFunction<E> strength) {
		super(parent);
		this.texture = texture;
		this.strength = strength;
	}

	public GlowLayer(RenderLayerParent<CreatureRenderState<E>, M> parent, Identifier texture) {
		this(parent, entity -> texture, entity -> 1.0);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CreatureRenderState<E> state,
					   float yRot, float xRot) {
		E entity = state.entity;
		if (entity == null || state.isInvisible) {
			return;
		}
		float s = (float) Math.max(0.0, Math.min(1.0, strength.applyAsDouble(entity)));
		if (s <= 0.0F) {
			return;
		}
		int c = Math.round(s * 255.0F);
		collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(texture.apply(entity)),
				FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ARGB.color(255, c, c, c), null, state.outlineColor);
	}
}
