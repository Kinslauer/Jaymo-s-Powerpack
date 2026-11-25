package com.jaymoskingdoms.client.glowwood.rootspike;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.glowwood.CreatureRenderState;
import com.jaymoskingdoms.glowwood.creatures.RootSpikeEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws a Root Spike once it has broken through, with its glowing veins at full brightness. */
public class RootSpikeRenderer extends EntityRenderer<RootSpikeEntity, CreatureRenderState<RootSpikeEntity>> {
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/glowwood/root_spike/root_spike.png");
	private static final Identifier GLOW = JaymosKingdoms.id("textures/entity/glowwood/root_spike/root_spike_glow.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	private final RootSpikeModel model;

	public RootSpikeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new RootSpikeModel(context.bakeLayer(RootSpikeModel.LAYER));
	}

	@Override
	public CreatureRenderState<RootSpikeEntity> createRenderState() {
		return new CreatureRenderState<>();
	}

	@Override
	public void extractRenderState(RootSpikeEntity entity, CreatureRenderState<RootSpikeEntity> state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.entity = entity;
		state.partialTick = partialTick;
		state.ageInTicks = entity.tickCount + partialTick;
		state.bodyRot = entity.getYRot();
	}

	@Override
	public void submit(CreatureRenderState<RootSpikeEntity> state, PoseStack poseStack, SubmitNodeCollector collector,
					   CameraRenderState camera) {
		if (state.entity == null || !state.entity.hasErupted()) {
			return;
		}
		poseStack.pushPose();
		poseStack.rotate(Axis.YP.rotationDegrees(90.0F - state.bodyRot));
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		this.model.setupAnim(state);
		collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
		collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.eyes(GLOW), FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
