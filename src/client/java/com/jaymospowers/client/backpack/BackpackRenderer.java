package com.jaymospowers.client.backpack;

import com.jaymospowers.backpack.BackpackBlock;
import com.jaymospowers.backpack.BackpackEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A set-down backpack: the worn model standing on its bedroll, its outer face (pocket and buckle) to the block's facing. */
public class BackpackRenderer implements BlockEntityRenderer<BackpackEntity, BackpackRenderer.State> {
	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
	}

	public BackpackRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BackpackEntity backpack, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(backpack, state, partialTicks, camera, breakProgress);
		state.facing = backpack.getBlockState().getValue(BackpackBlock.FACING);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5F, BackpackModel.HEIGHT / 16.0F, 0.5F);
		poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot());
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		BackpackClient.submitBag(poseStack, collector, state.lightCoords, 0.0F, 0.0F, -BackpackModel.DEPTH / 2.0F, 0.0F, 0.0F, 0.0F);
		poseStack.popPose();
	}
}
