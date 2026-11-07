package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.brewing.MortarEntity;
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

/** (0.20.13) The ingredient, or the ground batch, lying in the mortar bowl. */
public class MortarRenderer implements BlockEntityRenderer<MortarEntity, MortarRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean has;
	}

	private final ItemModelResolver itemModels;

	public MortarRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MortarEntity mortar, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(mortar, state, partialTicks, camera, breakProgress);
		ItemStack stack = mortar.result().isEmpty() ? mortar.contents() : mortar.result();
		state.has = !stack.isEmpty();
		if (state.has) {
			itemModels.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED, mortar.getLevel(), null, 0);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.has) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 3 / 16.0F, 0.5F);
		poseStack.rotateDegrees(Axis.XP, 90.0F);
		poseStack.scale(0.3F, 0.3F, 0.3F);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
