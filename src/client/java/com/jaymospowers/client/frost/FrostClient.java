package com.jaymospowers.client.frost;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.frost.Frost;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

/** The 26.4 ice's client side: the thrown ice ball, and the Frostbite in its own frozen skin on the zombie's body. */
public final class FrostClient {
	private FrostClient() {
	}

	public static void init() {
		EntityRenderers.register(Frost.ICE_BALL_ENTITY, ThrownItemRenderer::new);
		EntityRenderers.register(Frost.FROSTBITE, FrostbiteRenderer::new);
	}

	/** A zombie drawn in our Frostbite skin ({@code tools/gen_frost.py}). */
	static final class FrostbiteRenderer extends ZombieRenderer {
		private static final Identifier SKIN = JaymosPowers.id("textures/entity/frostbite.png");

		FrostbiteRenderer(EntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		public Identifier getTextureLocation(ZombieRenderState state) {
			return SKIN;
		}
	}
}
