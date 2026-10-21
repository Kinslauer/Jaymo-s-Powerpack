package com.jaymoskitchen.client.mill;

import com.jaymoskitchen.mill.machines.PottersWheelBlock;
import com.jaymoskitchen.mill.machines.PottersWheelEntity;
import com.jaymoskitchen.mill.machines.PowerMachines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws a potter's wheel's stone wheel turning on its stand (the stand is the block's own model). */
public class PottersWheelRenderer implements BlockEntityRenderer<PottersWheelEntity, PottersWheelRenderer.State> {
	private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();

	public static class State extends BlockEntityRenderState {
		float angle;
		final BlockModelRenderState wheel = new BlockModelRenderState();
	}

	private final BlockModelResolver blocks;

	public PottersWheelRenderer(BlockEntityRendererProvider.Context context) {
		this.blocks = context.blockModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PottersWheelEntity wheel, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(wheel, state, partialTicks, camera, breakProgress);
		state.angle = Mth.lerp(partialTicks, wheel.lastAngle, wheel.angle);
		blocks.update(state.wheel, PowerMachines.POTTERS_WHEEL.defaultBlockState().setValue(PottersWheelBlock.WHEEL, true), DISPLAY);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.rotateDegrees(Axis.YP, state.angle);
		poseStack.translate(-0.5F, 0.0F, -0.5F);
		state.wheel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
