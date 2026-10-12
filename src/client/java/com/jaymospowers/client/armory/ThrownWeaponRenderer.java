package com.jaymospowers.client.armory;

import com.jaymospowers.armory.ThrownWeapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * A thrown Armory weapon, drawn as its item: point first along its flight (sprites have their tip in the top-right
 * corner, so it's rolled 45 degrees, as RiftBladeRenderer does), or tumbling end over end for hatchets, sticks and stars.
 */
public class ThrownWeaponRenderer extends EntityRenderer<ThrownWeapon, ThrownWeaponRenderer.State> {
	public static class State extends ThrownItemRenderState {
		float yRot;
		float xRot;
		boolean spins;
	}

	/** Tumbling weapons turn this many degrees a tick. */
	private static final float SPIN_DEGREES = 40.0F;
	private static final float SCALE = 1.1F;

	private final ItemModelResolver itemModelResolver;

	public ThrownWeaponRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ThrownWeapon entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot(partialTicks);
		state.xRot = entity.getXRot(partialTicks);
		state.spins = entity.spins();
		this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, state.yRot - 90.0F);
		poseStack.rotateDegrees(Axis.ZP, state.xRot - 45.0F);
		if (state.spins) {
			poseStack.rotateDegrees(Axis.ZN, state.ageInTicks * SPIN_DEGREES);
		}
		poseStack.scale(SCALE, SCALE, SCALE);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
