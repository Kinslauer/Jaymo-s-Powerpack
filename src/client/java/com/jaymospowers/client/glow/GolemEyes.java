package com.jaymospowers.client.glow;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * (Motion patch 5) The iron golem's eyes glow, after Actions &amp; Stuff's emissive mobs: our own glow
 * ({@code textures/entity/iron_golem_eyes.png}, from tools/gen_held_3d.py) laid over its pupils as spiders' eyes are,
 * so they show in the dark.
 */
public final class GolemEyes {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/iron_golem_eyes.png");

	private GolemEyes() {
	}

	public static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof IronGolemRenderer golem) {
				helper.register(new Layer(golem));
			}
		});
	}

	static final class Layer extends EyesLayer<IronGolemRenderState, IronGolemModel> {
		private static final RenderType EYES = RenderTypes.eyes(TEXTURE);

		Layer(RenderLayerParent<IronGolemRenderState, IronGolemModel> renderer) {
			super(renderer);
		}

		@Override
		public RenderType renderType() {
			return EYES;
		}
	}
}
