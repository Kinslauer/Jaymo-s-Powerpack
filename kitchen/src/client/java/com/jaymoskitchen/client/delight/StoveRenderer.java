package com.jaymoskitchen.client.delight;

import com.jaymoskitchen.delight.StoveBlockEntity;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** (From Farmer's Delight's stove renderer, MIT.) The food grilling on a stove, lying flat on its six spots. */
public class StoveRenderer implements BlockEntityRenderer<StoveBlockEntity, StoveRenderer.State> {
	/** Items are drawn this big (of a block). */
	private static final float SIZE = 0.375F;

	public static class State extends BlockEntityRenderState {
		ItemStackRenderState[] stacks = new ItemStackRenderState[0];
		Direction facing = Direction.NORTH;
	}

	private final ItemModelResolver itemModels;

	public StoveRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(StoveBlockEntity stove, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(stove, state, partialTicks, camera, breakProgress);
		state.stacks = new ItemStackRenderState[StoveBlockEntity.SLOTS];
		for (int i = 0; i < StoveBlockEntity.SLOTS; i++) {
			state.stacks[i] = new ItemStackRenderState();
			itemModels.updateForTopItem(state.stacks[i], stove.items().get(i), ItemDisplayContext.FIXED, stove.getLevel(), null, (int) stove.getBlockPos().asLong() + i);
		}
		state.facing = stove.getBlockState().getValue(HorizontalDirectionalBlock.FACING).getOpposite();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int i = 0; i < state.stacks.length; i++) {
			if (state.stacks[i].isEmpty()) {
				continue;
			}
			poseStack.pushPose();
			// centred over the stove, turned to its front, flat
			poseStack.translate(0.5, 1.02, 0.5);
			poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot());
			poseStack.rotateDegrees(Axis.XP, 90.0F);
			Vec2 offset = StoveBlockEntity.offset(i);
			poseStack.translate(offset.x, offset.y, 0.0);
			poseStack.scale(SIZE, SIZE, SIZE);
			state.stacks[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
