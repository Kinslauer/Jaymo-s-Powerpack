package com.jaymoskingdoms.client.inhabitants.fts;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.inhabitants.InhabitantRenderer;
import com.jaymoskingdoms.inhabitants.fts.Fts;
import com.jaymoskingdoms.inhabitants.fts.Froglin;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * The renderers of the mobs ported from From the Shadows Reborn (Sonmok14 and L_Ender, AGPL v3; their models, textures
 * and animations, see licenses/from-the-shadows/). The froglin is drawn in the colour its variant says; its vomit as a
 * slime ball.
 */
public final class FtsClient {
	static final float FROGLIN_SHADOW = 0.8F;
	static final DataTicket<Integer> FROGLIN_VARIANT = DataTicket.create("jaymoskingdoms_froglin_variant", Integer.class);
	static final Identifier[] FROGLIN_SKINS = {JaymosKingdoms.id("textures/entity/froglin/froglin.png"),
		JaymosKingdoms.id("textures/entity/froglin/froglin_red.png"), JaymosKingdoms.id("textures/entity/froglin/froglin_gray.png")};

	private FtsClient() {
	}

	public static void register() {
		EntityRenderers.register(Fts.FROGLIN, FroglinRenderer::new);
		EntityRenderers.register(Fts.FROG_VOMIT, ThrownItemRenderer::new);
	}

	static final class FroglinRenderer extends InhabitantRenderer<Froglin> {
		FroglinRenderer(EntityRendererProvider.Context context) {
			super(context, new DefaultedEntityGeoModel<Froglin>(JaymosKingdoms.id("froglin")) {
				@Override
				public Identifier getTextureResource(GeoRenderState state) {
					int variant = state.getOrDefaultGeckolibData(FROGLIN_VARIANT, 0);
					return FROGLIN_SKINS[Math.max(0, Math.min(FROGLIN_SKINS.length - 1, variant))];
				}
			}, 1.0F, FROGLIN_SHADOW, "head");
		}

		@Override
		public void addRenderData(Froglin froglin, Void related, LivingEntityRenderState state, float partialTick) {
			super.addRenderData(froglin, related, state, partialTick);
			state.addGeckolibData(FROGLIN_VARIANT, froglin.variant());
		}
	}
}
