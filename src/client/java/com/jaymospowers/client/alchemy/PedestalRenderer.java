package com.jaymospowers.client.alchemy;

import com.jaymospowers.alchemy.infusion.PedestalEntity;
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

/** The item on a pedestal: it floats a little above the top, bobbing gently and turning slowly. */
public class PedestalRenderer implements BlockEntityRenderer<PedestalEntity, PedestalRenderer.State> {
	/** Degrees turned per tick, and how far it bobs (blocks) and how fast (radians per tick). */
	static final float SPIN = 2.0F, BOB = 0.04F, BOB_SPEED = 0.08F;
	/** Height of the item's centre over the block's floor, and its size. */
	static final float HEIGHT = 1.25F, SCALE = 0.7F;

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean empty;
		float age;
	}

	private final ItemModelResolver itemModels;

	public PedestalRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PedestalEntity pedestal, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(pedestal, state, partialTicks, camera, breakProgress);
		ItemStack stack = pedestal.stack();
		state.empty = stack.isEmpty() || pedestal.getLevel() == null;
		if (state.empty) {
			return;
		}
		// each pedestal turns from its own phase, so a ring of them doesn't move as one
		state.age = pedestal.getLevel().getGameTime() + partialTicks + (pedestal.getBlockPos().asLong() & 63);
		itemModels.updateForTopItem(state.item, stack, ItemDisplayContext.GROUND, pedestal.getLevel(), null, (int) pedestal.getBlockPos().asLong());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.empty) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, HEIGHT + (float) Math.sin(state.age * BOB_SPEED) * BOB, 0.5F);
		poseStack.rotateDegrees(Axis.YP, state.age * SPIN);
		poseStack.scale(SCALE, SCALE, SCALE);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
