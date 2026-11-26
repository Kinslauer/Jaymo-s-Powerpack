package com.jaymoskingdoms.client.glowwood.creature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.glowwood.CreatureRenderState;
import com.jaymoskingdoms.client.glowwood.CreatureRenderer;
import com.jaymoskingdoms.glowwood.creatures.TrufflerEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

public class TrufflerRenderer extends CreatureRenderer<TrufflerEntity, TrufflerModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKingdoms.id("truffler"), "main");
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/glowwood/truffler/truffler.png");

	public TrufflerRenderer(EntityRendererProvider.Context context) {
		super(context, new TrufflerModel(context.bakeLayer(LAYER)), 0.6F);
	}

	@Override
	protected Identifier texture(TrufflerEntity entity) {
		return TEXTURE;
	}

	@Override
	protected void scale(CreatureRenderState<TrufflerEntity> state, PoseStack poseStack) {
		if (state.isBaby) {
			poseStack.scale(0.5F, 0.5F, 0.5F);
		}
	}
}
