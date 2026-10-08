package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.entity.RiftBlade;
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
import net.minecraft.world.phys.AABB;

/**
 * Draws a Barrage weapon as its item, pointing where it flies. Item sprites have their tip in the top-right corner, so
 * after turning to the flight direction (as arrows do: local +X forward) the sprite is rolled 45 degrees.
 */
public class RiftBladeRenderer extends EntityRenderer<RiftBlade, RiftBladeRenderer.State> {
	public static class State extends ThrownItemRenderState {
		float yRot;
		float xRot;
	}

	private final ItemModelResolver itemModelResolver;

	public RiftBladeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RiftBlade entity, State state, float partialTicks) {
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
		poseStack.scale(1.4F, 1.4F, 1.4F);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected int getBlockLightLevel(RiftBlade entity, BlockPos pos) {
		return Math.max(10, super.getBlockLightLevel(entity, pos)); // faintly lit by the rift
	}

	@Override
	protected AABB getBoundingBoxForCulling(RiftBlade entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(1.0);
	}
}
