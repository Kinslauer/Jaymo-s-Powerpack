package com.jaymoskingdoms.client.glowwood.creature;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.glowwood.CreatureRenderer;
import com.jaymoskingdoms.client.glowwood.GlowLayer;
import com.jaymoskingdoms.glowwood.creatures.ShamblerEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class ShamblerRenderer extends CreatureRenderer<ShamblerEntity, ShamblerModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKingdoms.id("shambler"), "main");
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/glowwood/shambler/shambler.png");
	private static final Identifier GLOW = JaymosKingdoms.id("textures/entity/glowwood/shambler/shambler_glow.png");

	public ShamblerRenderer(EntityRendererProvider.Context context) {
		super(context, new ShamblerModel(context.bakeLayer(LAYER)), 0.5F);
		this.addLayer(new GlowLayer<>(this, e -> GLOW, e -> 0.85 + 0.15 * Mth.sin(e.tickCount * 0.11F)));
	}

	@Override
	protected Identifier texture(ShamblerEntity entity) {
		return TEXTURE;
	}
}
