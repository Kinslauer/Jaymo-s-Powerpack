package com.jaymospowers.client.tinkering.smithing;

import com.jaymospowers.tinkering.smithing.HearthEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** (Smithing) The metal lying in a Forge Hearth's coals, glowing. */
public class HearthRenderer implements BlockEntityRenderer<HearthEntity, HearthRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState held = new ItemStackRenderState();
		boolean hasHeld;
	}

	private final ItemModelResolver itemModels;

	public HearthRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(HearthEntity hearth, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(hearth, state, partialTicks, camera, breakProgress);
		state.hasHeld = !hearth.held().isEmpty();
		if (state.hasHeld) {
			itemModels.updateForTopItem(state.held, hearth.held(), ItemDisplayContext.FIXED, hearth.getLevel(), null, 0);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.hasHeld) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 1.0F + 1 / 64.0F, 0.5F);
		poseStack.rotateDegrees(Axis.XP, 90.0F);
		poseStack.scale(0.55F, 0.55F, 0.55F);
		state.held.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
