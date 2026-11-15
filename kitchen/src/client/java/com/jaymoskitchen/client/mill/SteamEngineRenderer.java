package com.jaymoskitchen.client.mill;

import com.jaymoskitchen.mill.power.Power;
import com.jaymoskitchen.mill.power.PowerPartBlock.Part;
import com.jaymoskitchen.mill.steam.SteamEngineEntity;
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

/**
 * Draws a steam engine's flywheel turning on its shaft at the engine's front (GT New Horizons picks, patch 8). The bed
 * and cylinder are the block's own model; the flywheel is a {@link Part#ENGINE_FLYWHEEL} block model spun about the
 * shaft, so a fast wheel means a full boiler.
 */
public class SteamEngineRenderer implements BlockEntityRenderer<SteamEngineEntity, SteamEngineRenderer.State> {
	private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();

	public static class State extends BlockEntityRenderState {
		float yaw;
		float angle;
		final BlockModelRenderState wheel = new BlockModelRenderState();
	}

	private final BlockModelResolver blocks;

	public SteamEngineRenderer(BlockEntityRendererProvider.Context context) {
		this.blocks = context.blockModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SteamEngineEntity engine, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(engine, state, partialTicks, camera, breakProgress);
		// turn the model's -z (where its shaft leaves) to the engine's front
		state.yaw = 180.0F - engine.facing().toYRot();
		state.angle = Mth.lerp(partialTicks, engine.lastAngle, engine.angle);
		blocks.update(state.wheel, Power.PART.part(Part.ENGINE_FLYWHEEL), DISPLAY);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.rotateDegrees(Axis.YP, state.yaw);
		poseStack.rotateDegrees(Axis.ZP, state.angle);
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		state.wheel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
