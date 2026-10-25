package com.jaymoskitchen.client.tanning;

import com.jaymoskitchen.tanning.DryingRackBlock;
import com.jaymoskitchen.tanning.DryingRackEntity;
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

/** Draws what hangs on a drying rack: up to four hides side by side under its pole. */
public class DryingRackRenderer implements BlockEntityRenderer<DryingRackEntity, DryingRackRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		int count;
		float yaw;
	}

	private final ItemModelResolver itemModels;

	public DryingRackRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(DryingRackEntity rack, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(rack, state, partialTicks, camera, breakProgress);
		ItemStack stack = rack.held();
		state.count = stack.isEmpty() ? 0 : Math.min(4, stack.getCount());
		if (state.count > 0) {
			itemModels.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED, rack.getLevel(), null, 0);
		}
		state.yaw = rack.getBlockState().hasProperty(DryingRackBlock.FACING) ? -rack.getBlockState().getValue(DryingRackBlock.FACING).toYRot() : 0.0F;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int i = 0; i < state.count; i++) {
			poseStack.pushPose();
			poseStack.translate(0.5F, 0.0F, 0.5F);
			poseStack.rotateDegrees(Axis.YP, state.yaw);
			// hung from the pole, spread along it
			float along = state.count == 1 ? 0.0F : -0.3F + 0.6F * i / (state.count - 1);
			poseStack.translate(along, 0.62F, (i % 2 == 0 ? 0.01F : -0.01F));
			poseStack.scale(0.42F, 0.42F, 0.42F);
			state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
