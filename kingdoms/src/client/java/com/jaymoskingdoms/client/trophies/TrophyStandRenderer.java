package com.jaymoskingdoms.client.trophies;

import com.jaymoskingdoms.trophies.Trophies;
import com.jaymoskingdoms.trophies.TrophyStandBlock;
import com.jaymoskingdoms.trophies.TrophyStandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a trophy stand holds, shown on its dish. A head (a skull, or a great foe's mask) stands upright, facing out the way
 * the stand does, and does not move; anything else rests on the dish and turns slowly.
 */
public class TrophyStandRenderer implements BlockEntityRenderer<TrophyStandEntity, TrophyStandRenderer.State> {
	/** Degrees a resting item turns per tick. */
	static final float SPIN = 0.9F;
	/** Height of the dish's top (blocks), and how high a head's and a resting item's middle sit over it. */
	static final float DISH = 10.0F / 16.0F, HEAD_RISE = 0.28F, ITEM_RISE = 0.32F;
	/** Size of a head and of a resting item. */
	static final float HEAD_SCALE = 0.95F, ITEM_SCALE = 0.7F;

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean empty;
		boolean head;
		float yaw;
		float age;
	}

	private final ItemModelResolver itemModels;

	public TrophyStandRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	public static void register() {
		BlockEntityRenderers.register(Trophies.STAND_ENTITY, TrophyStandRenderer::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TrophyStandEntity stand, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(stand, state, partialTicks, camera, breakProgress);
		ItemStack stack = stand.stack();
		state.empty = stack.isEmpty() || stand.getLevel() == null;
		if (state.empty) {
			return;
		}
		state.head = stack.is(Trophies.FOE_TROPHY) || stack.getItem() instanceof BlockItem block && block.getBlock() instanceof AbstractSkullBlock;
		Direction facing = stand.getBlockState().getValue(TrophyStandBlock.FACING);
		// (an item's front looks south, so a stand facing north turns it half way round)
		state.yaw = facing.toYRot();
		state.age = stand.getLevel().getGameTime() + partialTicks + (stand.getBlockPos().asLong() & 63);
		this.itemModels.updateForTopItem(state.item, stack, state.head ? ItemDisplayContext.FIXED : ItemDisplayContext.GROUND, stand.getLevel(), null,
			(int)stand.getBlockPos().asLong());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.empty) {
			return;
		}
		poseStack.pushPose();
		if (state.head) {
			poseStack.translate(0.5F, DISH + HEAD_RISE, 0.5F);
			poseStack.rotateDegrees(Axis.YP, state.yaw);
			poseStack.scale(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
		} else {
			poseStack.translate(0.5F, DISH + ITEM_RISE, 0.5F);
			poseStack.rotateDegrees(Axis.YP, state.age * SPIN);
			poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
		}
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
