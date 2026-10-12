package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.StationEntity;
import com.jaymospowers.tinkering.Tinkering;
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
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws what's left on a Part Builder or Tool Station, lying on its top. */
public class StationRenderer implements BlockEntityRenderer<StationEntity, StationRenderer.State> {
	/** Where each item lies on the top (x, z in block units): the Part Builder's pattern and material side by side. */
	private static final float[][] PART_BUILDER_AT = {{0.3F, 0.5F}, {0.7F, 0.5F}};
	/** The Tool Station's four parts in a square. */
	private static final float[][] TOOL_STATION_AT = {{0.3F, 0.3F}, {0.7F, 0.3F}, {0.3F, 0.7F}, {0.7F, 0.7F}};
	private static final float ITEM_SCALE = 0.3F;

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState[] items = new ItemStackRenderState[StationEntity.SIZE];
		final boolean[] has = new boolean[StationEntity.SIZE];
		float[][] at = TOOL_STATION_AT;
		int lightAbove;

		State() {
			for (int i = 0; i < items.length; i++) {
				items[i] = new ItemStackRenderState();
			}
		}
	}

	private final ItemModelResolver itemModels;

	public StationRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(StationEntity table, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(table, state, partialTicks, camera, breakProgress);
		state.at = table.getBlockState().is(Tinkering.PART_BUILDER) ? PART_BUILDER_AT : TOOL_STATION_AT;
		for (int i = 0; i < StationEntity.SIZE; i++) {
			ItemStack stack = table.items().get(i);
			state.has[i] = i < state.at.length && !stack.isEmpty();
			if (state.has[i]) {
				itemModels.updateForTopItem(state.items[i], stack, ItemDisplayContext.FIXED, table.getLevel(), null, i);
			}
		}
		// lit by the air above the table, not the inside of the block
		BlockPos above = table.getBlockPos().above();
		state.lightAbove = table.getLevel() != null ? LightCoordsUtil.getLightCoords(table.getLevel(), above) : state.lightCoords;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int i = 0; i < StationEntity.SIZE; i++) {
			if (!state.has[i]) {
				continue;
			}
			poseStack.pushPose();
			poseStack.translate(state.at[i][0], 1.0F + 1.0F / 64.0F, state.at[i][1]);
			poseStack.rotateDegrees(Axis.XP, 90.0F);
			poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
			state.items[i].submit(poseStack, collector, state.lightAbove, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
