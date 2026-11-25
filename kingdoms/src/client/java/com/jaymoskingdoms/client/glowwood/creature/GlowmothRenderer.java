package com.jaymoskingdoms.client.glowwood.creature;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.glowwood.CreatureRenderer;
import com.jaymoskingdoms.client.glowwood.GlowLayer;
import com.jaymoskingdoms.glowwood.creatures.GlowmothEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class GlowmothRenderer extends CreatureRenderer<GlowmothEntity, GlowmothModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKingdoms.id("glowmoth"), "main");
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/glowwood/glowmoth/glowmoth.png");
	private static final Identifier GLOW = JaymosKingdoms.id("textures/entity/glowwood/glowmoth/glowmoth_glow.png");

	public GlowmothRenderer(EntityRendererProvider.Context context) {
		super(context, new GlowmothModel(context.bakeLayer(LAYER)), 0.15F);
		this.addLayer(new GlowLayer<>(this, e -> GLOW, e -> 0.75 + 0.25 * Mth.sin((e.tickCount + e.getId() * 13) * 0.12F)));
	}

	@Override
	protected Identifier texture(GlowmothEntity entity) {
		return TEXTURE;
	}

	/** Moths glow faintly themselves. */
	@Override
	protected int getBlockLightLevel(GlowmothEntity entity, BlockPos pos) {
		return Math.max(9, super.getBlockLightLevel(entity, pos));
	}
}
