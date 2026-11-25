package com.jaymoskingdoms.client.glowwood.creature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.glowwood.CreatureRenderState;
import com.jaymoskingdoms.client.glowwood.CreatureRenderer;
import com.jaymoskingdoms.client.glowwood.GlowLayer;
import com.jaymoskingdoms.glowwood.creatures.SporelingEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

public class SporelingRenderer extends CreatureRenderer<SporelingEntity, SporelingModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKingdoms.id("sporeling"), "main");
	private static final Map<SporelingEntity.Variant, Identifier> TEXTURES = new EnumMap<>(SporelingEntity.Variant.class);
	private static final Identifier GLOW = JaymosKingdoms.id("textures/entity/glowwood/sporeling/sporeling_glowcap_glow.png");

	static {
		for (SporelingEntity.Variant v : SporelingEntity.Variant.values()) {
			TEXTURES.put(v, JaymosKingdoms.id("textures/entity/glowwood/sporeling/sporeling_" + v.id() + ".png"));
		}
	}

	public SporelingRenderer(EntityRendererProvider.Context context) {
		super(context, new SporelingModel(context.bakeLayer(LAYER)), 0.3F);
		this.addLayer(new GlowLayer<>(this, e -> GLOW, e -> e.getVariant() == SporelingEntity.Variant.GLOWCAP
				? 0.8 + 0.2 * Mth.sin(e.tickCount * 0.08F) : 0.0));
	}

	@Override
	protected Identifier texture(SporelingEntity entity) {
		return TEXTURES.get(entity.getVariant());
	}

	@Override
	protected void scale(CreatureRenderState<SporelingEntity> state, PoseStack poseStack) {
		if (state.isBaby) {
			poseStack.scale(0.55F, 0.55F, 0.55F);
		}
	}
}
