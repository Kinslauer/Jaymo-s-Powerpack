package com.jaymoskitchen.client.delight;

import com.jaymoskitchen.delight.CuttingBoardEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws what's waiting on a cutting board, lying flat on it (Farmer's Delight's look). */
public class CuttingBoardRenderer implements BlockEntityRenderer<CuttingBoardEntity, CuttingBoardRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean has;
		float yaw;
	}

	private final ItemModelResolver itemModels;

	public CuttingBoardRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CuttingBoardEntity board, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(board, state, partialTicks, camera, breakProgress);
		ItemStack stack = board.item();
		state.has = !stack.isEmpty();
		if (state.has) {
			itemModels.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED, board.getLevel(), null, 0);
		}
		Direction facing = board.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
		state.yaw = -facing.toYRot();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.has) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 1.0F / 16.0F + 1.0F / 64.0F, 0.5F);
		poseStack.rotateDegrees(Axis.YP, state.yaw);
		poseStack.rotateDegrees(Axis.XP, 90.0F);
		poseStack.scale(0.6F, 0.6F, 0.6F);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
