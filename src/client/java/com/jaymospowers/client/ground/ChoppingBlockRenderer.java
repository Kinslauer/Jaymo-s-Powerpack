package com.jaymospowers.client.ground;

import com.jaymospowers.ground.ChoppingBlockEntity;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The log stood up on a chopping block, ready for the axe (turned a little by where the block stands, so rows of them vary). */
public class ChoppingBlockRenderer implements BlockEntityRenderer<ChoppingBlockEntity, ChoppingBlockRenderer.State> {
	/** The log's size against a full block, and the top of the chopping block it stands on. */
	static final float SCALE = 0.55F, TOP = 10 / 16.0F;

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean has;
		float turn;
	}

	private final ItemModelResolver itemModels;

	public ChoppingBlockRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ChoppingBlockEntity block, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(block, state, partialTicks, camera, breakProgress);
		ItemStack logs = block.logs();
		state.has = !logs.isEmpty();
		if (state.has) {
			int seed = (int) block.getBlockPos().asLong();
			itemModels.updateForTopItem(state.item, logs, ItemDisplayContext.NONE, block.getLevel(), null, seed);
			state.turn = Math.floorMod(seed * 31, 360);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.has) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, TOP + SCALE / 2, 0.5F);
		poseStack.rotateDegrees(Axis.YP, state.turn);
		poseStack.scale(SCALE, SCALE, SCALE);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
