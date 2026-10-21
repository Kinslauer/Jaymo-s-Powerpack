package com.jaymoskitchen.client.mill;

import com.jaymoskitchen.mill.power.PowerPartBlock.Part;
import com.jaymoskitchen.mill.power.Power;
import com.jaymoskitchen.mill.power.Rotor;
import com.jaymoskitchen.mill.power.RotorEntity;
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
 * Draws a windmill or waterwheel turning (after Better With Mods, MIT; our own code and models): the hub, then four
 * sails (a bare length of spar by the hub, then six lengths with sailcloth) or eight paddles on spokes, all spun
 * round the shaft. The pieces are block models held by {@link Part}.
 */
public class RotorRenderer implements BlockEntityRenderer<RotorEntity, RotorRenderer.State> {
	private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
	/** Sails on a windmill, paddles on a waterwheel. */
	private static final int SAILS = 4, PADDLES = 8;

	public static class State extends BlockEntityRenderState {
		Rotor rotor = Rotor.WINDMILL;
		float yaw;
		float angle;
		final BlockModelRenderState hub = new BlockModelRenderState();
		final BlockModelRenderState spar = new BlockModelRenderState();
		final BlockModelRenderState blade = new BlockModelRenderState();
	}

	private final BlockModelResolver blocks;

	public RotorRenderer(BlockEntityRendererProvider.Context context) {
		this.blocks = context.blockModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RotorEntity rotor, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(rotor, state, partialTicks, camera, breakProgress);
		state.rotor = rotor.rotor();
		// turn the model's -z (where its shaft is) to the shaft
		state.yaw = 180.0F - rotor.facing().toYRot();
		state.angle = Mth.lerp(partialTicks, rotor.lastAngle, rotor.angle);
		boolean windmill = state.rotor == Rotor.WINDMILL;
		blocks.update(state.hub, Power.PART.part(windmill ? Part.WINDMILL_HUB : Part.WATERWHEEL_HUB), DISPLAY);
		blocks.update(state.spar, Power.PART.part(Part.WINDMILL_SPAR), DISPLAY);
		blocks.update(state.blade, Power.PART.part(windmill ? Part.WINDMILL_SAIL : Part.WATERWHEEL_PADDLE), DISPLAY);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = state.lightCoords;
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.rotateDegrees(Axis.YP, state.yaw);
		poseStack.rotateDegrees(Axis.ZP, state.angle);
		block(state.hub, poseStack, collector, light, 0.0F);
		if (state.rotor == Rotor.WINDMILL) {
			for (int sail = 0; sail < SAILS; sail++) {
				poseStack.pushPose();
				poseStack.rotateDegrees(Axis.ZP, sail * 90.0F);
				// a bare length by the hub, then the sailcloth out to the tip
				block(state.spar, poseStack, collector, light, 0.5F);
				for (int length = 1; length < Rotor.WINDMILL.radius; length++) {
					block(state.blade, poseStack, collector, light, 0.5F + length);
				}
				poseStack.popPose();
			}
		} else {
			for (int paddle = 0; paddle < PADDLES; paddle++) {
				poseStack.pushPose();
				poseStack.rotateDegrees(Axis.ZP, paddle * 360.0F / PADDLES);
				// the paddle model's block sits a block out; its spokes reach back in to the hub (its y -16)
				block(state.blade, poseStack, collector, light, 1.0F);
				poseStack.popPose();
			}
		}
		poseStack.popPose();
	}

	/** One piece, its model's block set {@code out} blocks up the current spoke (0: centred on the hub). */
	private static void block(BlockModelRenderState part, PoseStack poseStack, SubmitNodeCollector collector, int light, float out) {
		poseStack.pushPose();
		poseStack.translate(-0.5F, out == 0.0F ? -0.5F : out, -0.5F);
		part.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		// the sails reach far past the hub's block
		return true;
	}

	@Override
	public int getViewDistance() {
		return 128;
	}
}
