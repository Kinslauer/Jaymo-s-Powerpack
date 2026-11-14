package com.jaymoskingdoms.client.beasts;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.Satyr;
import com.jaymoskingdoms.client.inhabitants.InhabitantRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws the satyr (com.jaymoskingdoms.beasts.Satyr) with GeckoLib (geckolib/models/entity/satyr.geo.json, our own,
 * kingdoms/tools/gen_satyr.py) in its coat: chestnut, black, or the eldest's grey.
 */
public final class SatyrRenderer extends InhabitantRenderer<Satyr> {
	static final float SHADOW = 0.45F;
	static final DataTicket<Integer> COAT = DataTicket.create("jaymoskingdoms_satyr_coat", Integer.class);
	static final Identifier[] COATS = {JaymosKingdoms.id("textures/entity/satyr.png"), JaymosKingdoms.id("textures/entity/satyr_black.png"),
		JaymosKingdoms.id("textures/entity/satyr_grey.png")};

	SatyrRenderer(EntityRendererProvider.Context context) {
		super(context, new DefaultedEntityGeoModel<Satyr>(JaymosKingdoms.id("satyr")) {
			@Override
			public Identifier getTextureResource(GeoRenderState state) {
				return COATS[Math.clamp(state.getOrDefaultGeckolibData(COAT, 0), 0, COATS.length - 1)];
			}
		}, 1.0F, SHADOW, "head");
	}

	@Override
	public void addRenderData(Satyr satyr, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(satyr, related, state, partialTick);
		state.addGeckolibData(COAT, satyr.coat());
	}
}
