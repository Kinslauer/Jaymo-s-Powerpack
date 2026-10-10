package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.StoneSwords;
import com.jaymospowers.power.arsenal.entity.FrostLance;
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
import net.minecraft.world.item.ItemStack;

/** Hrunting's Frost Lance: the ice spear sprite, pointing where it flies (as RiftBladeRenderer turns its blades). */
public class FrostLanceRenderer extends EntityRenderer<FrostLance, FrostLanceRenderer.State> {
	public static class State extends ThrownItemRenderState {
		float yRot;
		float xRot;
	}

	private static final float SCALE = 1.8F;
	/** Faintly lit from within, like ice in the sun. */
	private static final int MIN_LIGHT = 12;

	private final ItemModelResolver itemModelResolver;
	private ItemStack look = ItemStack.EMPTY;

	public FrostLanceRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FrostLance entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot(partialTicks);
		state.xRot = entity.getXRot(partialTicks);
		if (this.look.isEmpty()) {
			this.look = new ItemStack(StoneSwords.FROST_LANCE_ITEM);
		}
		this.itemModelResolver.updateForNonLiving(state.item, this.look, ItemDisplayContext.NONE, entity);
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
	protected int getBlockLightLevel(FrostLance entity, BlockPos pos) {
		return Math.max(MIN_LIGHT, super.getBlockLightLevel(entity, pos));
	}
}
