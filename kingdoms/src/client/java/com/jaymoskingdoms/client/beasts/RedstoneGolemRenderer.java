package com.jaymoskingdoms.client.beasts;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.RedstoneGolem;
import com.jaymoskingdoms.client.inhabitants.InhabitantRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * The redstone golem drawn by GeckoLib ({@code geckolib/models/entity/redstone_golem.geo.json}), its head turned where it
 * looks, with its core and veins glowing over it ({@code textures/entity/redstone_golem_glow.png}, the model's emissive
 * texture). It falls by its own death animation, so it isn't tipped over on its side as well.
 */
final class RedstoneGolemRenderer extends InhabitantRenderer<RedstoneGolem> {
	static final float SCALE = 0.94F, SHADOW = 1.5F;

	RedstoneGolemRenderer(EntityRendererProvider.Context context) {
		super(context, "redstone_golem", SCALE, SHADOW, "redstone_golem_head");
		this.withRenderLayer(new TextureLayerGeoLayer<>(this, JaymosKingdoms.id("textures/entity/redstone_golem_glow.png"), RenderTypes::eyes));
	}

	@Override
	protected float getDeathMaxRotation(GeoRenderState state) {
		return 0.0F;
	}
}
