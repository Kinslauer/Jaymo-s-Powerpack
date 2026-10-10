package com.jaymoskitchen.client.table;

import com.jaymoskitchen.table.CraftingTableEntity;
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

/** Draws what's laid out on a crafting table, lying on its top in the 3x3 grid. */
public class CraftingTableRenderer implements BlockEntityRenderer<CraftingTableEntity, CraftingTableRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState[] items = new ItemStackRenderState[9];
		final boolean[] has = new boolean[9];
		int lightAbove;

		State() {
			for (int i = 0; i < items.length; i++) {
				items[i] = new ItemStackRenderState();
			}
		}
	}

	private final ItemModelResolver itemModels;

	public CraftingTableRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CraftingTableEntity table, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(table, state, partialTicks, camera, breakProgress);
		for (int i = 0; i < 9; i++) {
			ItemStack stack = table.grid().get(i);
			state.has[i] = !stack.isEmpty();
			if (state.has[i]) {
				itemModels.updateForTopItem(state.items[i], stack, ItemDisplayContext.FIXED, table.getLevel(), null, i);
			}
		}
		// lit by the air above the table, not the (dark) inside of the block
		BlockPos above = table.getBlockPos().above();
		state.lightAbove = table.getLevel() != null
			? LightCoordsUtil.getLightCoords(table.getLevel(), above) : state.lightCoords;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int i = 0; i < 9; i++) {
			if (!state.has[i]) {
				continue;
			}
			int column = i % 3, row = i / 3;
			poseStack.pushPose();
			// the grid laid out on the top, as on the crafting screen (first row at the north edge)
			poseStack.translate(0.5F + (column - 1) * 0.25F, 1.0F + 1.0F / 64.0F, 0.5F + (row - 1) * 0.25F);
			poseStack.rotateDegrees(Axis.XP, 90.0F);
			poseStack.scale(0.22F, 0.22F, 0.22F);
			state.items[i].submit(poseStack, collector, state.lightAbove, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
