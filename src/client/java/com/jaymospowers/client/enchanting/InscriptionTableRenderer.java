package com.jaymospowers.client.enchanting;

import com.jaymospowers.enchanting.InscriptionTableEntity;
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

/** Medieval picks (me-inscribe): the gear lying on an Inscription Table, and the gem set beside it. */
public class InscriptionTableRenderer implements BlockEntityRenderer<InscriptionTableEntity, InscriptionTableRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState gear = new ItemStackRenderState();
		final ItemStackRenderState gem = new ItemStackRenderState();
		boolean hasGear, hasGem;
	}

	private final ItemModelResolver itemModels;

	public InscriptionTableRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(InscriptionTableEntity table, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(table, state, partialTicks, camera, breakProgress);
		ItemStack gear = table.gear(), gem = table.gem();
		state.hasGear = !gear.isEmpty();
		state.hasGem = !gem.isEmpty();
		if (state.hasGear) {
			itemModels.updateForTopItem(state.gear, gear, ItemDisplayContext.FIXED, table.getLevel(), null, 0);
		}
		if (state.hasGem) {
			itemModels.updateForTopItem(state.gem, gem, ItemDisplayContext.FIXED, table.getLevel(), null, 1);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.hasGear) {
			flat(state.gear, poseStack, collector, state.lightCoords, 0.45F, 0.6F);
		}
		if (state.hasGem) {
			flat(state.gem, poseStack, collector, state.lightCoords, 0.8F, 0.3F);
		}
	}

	private static void flat(ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector collector, int light, float x, float scale) {
		poseStack.pushPose();
		poseStack.translate(x, 14 / 16.0F + 1 / 64.0F, 0.5F);
		poseStack.rotateDegrees(Axis.XP, 90.0F);
		poseStack.scale(scale, scale, scale);
		item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
