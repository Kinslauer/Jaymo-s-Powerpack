package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.kiln.KilnLoadEntity;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A kiln load: blocks sit as a small block, items lie in a little heap (one for every two, up to four). */
public class KilnLoadRenderer implements BlockEntityRenderer<KilnLoadEntity, KilnLoadRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean empty, block;
		int copies;
	}

	private final ItemModelResolver itemModels;

	public KilnLoadRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(KilnLoadEntity load, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(load, state, partialTicks, camera, breakProgress);
		ItemStack stack = load.stack();
		state.empty = stack.isEmpty();
		if (state.empty) {
			return;
		}
		state.block = stack.getItem() instanceof BlockItem;
		state.copies = Math.min(4, (stack.getCount() + 1) / 2);
		itemModels.updateForTopItem(state.item, stack, state.block ? ItemDisplayContext.NONE : ItemDisplayContext.FIXED, load.getLevel(), null,
			(int) load.getBlockPos().asLong());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.empty) {
			return;
		}
		if (state.block) {
			poseStack.pushPose();
			poseStack.translate(0.5F, 0.25F, 0.5F);
			poseStack.scale(0.5F, 0.5F, 0.5F);
			state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
			return;
		}
		for (int i = 0; i < state.copies; i++) {
			poseStack.pushPose();
			poseStack.translate(0.5F + (i % 2 == 0 ? -0.08F : 0.08F), 1 / 64.0F + i / 32.0F, 0.5F + (i < 2 ? -0.06F : 0.06F));
			poseStack.rotateDegrees(Axis.YP, i * 37.0F);
			poseStack.rotateDegrees(Axis.XP, 90.0F);
			poseStack.scale(0.5F, 0.5F, 0.5F);
			state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
