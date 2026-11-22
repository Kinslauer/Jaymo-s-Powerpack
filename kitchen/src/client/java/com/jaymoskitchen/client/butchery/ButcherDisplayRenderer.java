package com.jaymoskitchen.client.butchery;

import com.jaymoskitchen.butchery.ButcherDisplayBlock;
import com.jaymoskitchen.butchery.ButcherDisplayEntity;
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

/** Draws the food laid out on a butcher's display: lying flat on the counter top, two rows of two. */
public class ButcherDisplayRenderer implements BlockEntityRenderer<ButcherDisplayEntity, ButcherDisplayRenderer.State> {
	/** The counter top's height, and how far each spot sits from the middle (blocks). */
	private static final float TOP = 12.0F / 16.0F, SPREAD = 0.22F, SCALE = 0.4F;

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState[] items = new ItemStackRenderState[ButcherDisplayEntity.HOLDS];
		final boolean[] shown = new boolean[ButcherDisplayEntity.HOLDS];
		float yaw;

		State() {
			for (int i = 0; i < items.length; i++) {
				items[i] = new ItemStackRenderState();
			}
		}
	}

	private final ItemModelResolver itemModels;

	public ButcherDisplayRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ButcherDisplayEntity display, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(display, state, partialTicks, camera, breakProgress);
		for (int i = 0; i < ButcherDisplayEntity.HOLDS; i++) {
			ItemStack stack = display.items().get(i);
			state.shown[i] = !stack.isEmpty();
			if (state.shown[i]) {
				itemModels.updateForTopItem(state.items[i], stack, ItemDisplayContext.FIXED, display.getLevel(), null, i);
			}
		}
		state.yaw = display.getBlockState().hasProperty(ButcherDisplayBlock.FACING)
			? -display.getBlockState().getValue(ButcherDisplayBlock.FACING).toYRot() : 0.0F;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int i = 0; i < ButcherDisplayEntity.HOLDS; i++) {
			if (!state.shown[i]) {
				continue;
			}
			poseStack.pushPose();
			poseStack.translate(0.5F, TOP + 0.02F, 0.5F);
			poseStack.rotateDegrees(Axis.YP, state.yaw);
			poseStack.translate(i % 2 == 0 ? -SPREAD : SPREAD, 0.0F, i < 2 ? -SPREAD : SPREAD);
			// lying flat, a little askew each
			poseStack.rotateDegrees(Axis.XP, 90.0F);
			poseStack.rotateDegrees(Axis.ZP, (i * 37) % 30 - 15.0F);
			poseStack.scale(SCALE, SCALE, SCALE);
			state.items[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
