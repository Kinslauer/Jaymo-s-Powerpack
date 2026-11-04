package com.jaymospowers.client.jewellery;

import com.jaymospowers.jewellery.LapidaryWheelEntity;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** (0.19.87) The raw gem, or the cut gem, lying on the lapidary wheel's rest. */
public class LapidaryWheelRenderer implements BlockEntityRenderer<LapidaryWheelEntity, LapidaryWheelRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean has;
	}

	private final ItemModelResolver itemModels;

	public LapidaryWheelRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(LapidaryWheelEntity wheel, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(wheel, state, partialTicks, camera, breakProgress);
		ItemStack stack = wheel.result().isEmpty() ? wheel.gem() : wheel.result();
		state.has = !stack.isEmpty();
		if (state.has) {
			itemModels.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED, wheel.getLevel(), null, 0);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.has) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 13 / 16.0F, 0.5F);
		poseStack.rotateDegrees(Axis.XP, 90.0F);
		poseStack.scale(0.4F, 0.4F, 0.4F);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
