package com.jaymospowers.client.tinkering.smithing;

import com.jaymospowers.tinkering.smithing.HotMetal;
import com.jaymospowers.tinkering.smithing.SmithingAnvilBlock;
import com.jaymospowers.tinkering.smithing.SmithingAnvilEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** (Smithing) The work on a Smithing Anvil (glowing while hot) and the finished head, lying along its face. */
public class SmithingAnvilRenderer implements BlockEntityRenderer<SmithingAnvilEntity, SmithingAnvilRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState work = new ItemStackRenderState();
		final ItemStackRenderState result = new ItemStackRenderState();
		boolean hasWork, hasResult, workHot, resultHot, alongX;
	}

	private final ItemModelResolver itemModels;

	public SmithingAnvilRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SmithingAnvilEntity anvil, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(anvil, state, partialTicks, camera, breakProgress);
		ItemStack work = anvil.work(), result = anvil.result();
		long now = anvil.getLevel() == null ? 0L : anvil.getLevel().getGameTime();
		state.hasWork = !work.isEmpty();
		state.hasResult = !result.isEmpty();
		state.workHot = HotMetal.hot(work, now);
		state.resultHot = HotMetal.hot(result, now);
		state.alongX = anvil.getBlockState().getValue(SmithingAnvilBlock.FACING).getAxis() == Direction.Axis.X;
		if (state.hasWork) {
			itemModels.updateForTopItem(state.work, work, ItemDisplayContext.FIXED, anvil.getLevel(), null, 0);
		}
		if (state.hasResult) {
			itemModels.updateForTopItem(state.result, result, ItemDisplayContext.FIXED, anvil.getLevel(), null, 1);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.hasWork) {
			flat(state, state.work, poseStack, collector, state.workHot ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords, 1.0F + 1 / 64.0F);
		}
		if (state.hasResult) {
			flat(state, state.result, poseStack, collector, state.resultHot ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords, 1.0F + 2 / 64.0F);
		}
	}

	private static void flat(State state, ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector collector, int light, float y) {
		poseStack.pushPose();
		poseStack.translate(0.5F, y, 0.5F);
		if (!state.alongX) {
			poseStack.rotateDegrees(Axis.YP, 90.0F);
		}
		poseStack.rotateDegrees(Axis.XP, 90.0F);
		poseStack.scale(0.6F, 0.6F, 0.6F);
		item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
