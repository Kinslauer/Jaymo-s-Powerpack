package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.divine.FlyingSword;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * (0.18.48) A Divine-born's Flying Sword, drawn as the very item it carries, point first along its flight (item sprites have
 * their tip in the top-right corner, so after turning to the flight direction it is rolled 45 degrees, as the Frost Lance is).
 */
public class FlyingSwordRenderer extends EntityRenderer<FlyingSword, FlyingSwordRenderer.State> {
	public static class State extends ThrownItemRenderState {
		float yRot;
		float xRot;
	}

	private static final float SCALE = 1.5F;
	/** Faintly lit from within, as a blade of light. */
	private static final int MIN_LIGHT = 10;

	private final ItemModelResolver itemModelResolver;

	public FlyingSwordRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FlyingSword entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot(partialTicks);
		state.xRot = entity.getXRot(partialTicks);
		this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, state.yRot - 90.0F);
		poseStack.rotateDegrees(Axis.ZP, state.xRot - 45.0F);
		poseStack.scale(SCALE, SCALE, SCALE);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected int getBlockLightLevel(FlyingSword entity, BlockPos pos) {
		return Math.max(MIN_LIGHT, super.getBlockLightLevel(entity, pos));
	}
}
