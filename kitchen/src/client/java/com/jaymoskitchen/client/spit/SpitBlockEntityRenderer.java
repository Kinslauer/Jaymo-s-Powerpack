package com.jaymoskitchen.client.spit;

import com.jaymoskitchen.cooking.spit.SpitBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;

/**
 * Draws what's on the spit: the roasting stack turning slowly over the fire, tinted by how done it is
 * (raw, golden, burnt). When the server reports a chunk torn off ({@link #ripAt}), the item shakes for a few ticks.
 */
public class SpitBlockEntityRenderer implements BlockEntityRenderer<SpitBlockEntity, SpitBlockEntityRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean has;
		float turn;
		int tint;
		int rip;
	}

	/** The positions where a chunk was just torn off, with the animation length left in ticks. */
	private static final Map<BlockPos, Integer> RIPPING = new HashMap<>();

	/** The client plays the tearing animation here when the server reports a chunk torn off. */
	public static void ripAt(BlockPos pos) {
		RIPPING.put(pos, 8);
	}

	private final ItemModelResolver itemModels;

	public SpitBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	/** How far the spit has turned (degrees), advanced each frame the block is rendered. */
	private float turn;

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SpitBlockEntity be, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
		net.minecraft.world.item.ItemStack stack = be.roasting();
		state.has = !stack.isEmpty();
		if (state.has) {
			itemModels.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED, be.getLevel(), null, 0);
		}
		turn = (turn + 1.0F) % 360.0F;
		state.turn = turn;
		state.tint = tintFor(be.doneness());
		Integer rip = RIPPING.remove(be.getBlockPos());
		state.rip = rip == null ? 0 : rip;
	}

	/** Raw pale → cooked golden → burnt dark, following {@link SpitBlockEntity#doneness()}. */
	private static int tintFor(float doneness) {
		if (doneness <= SpitBlockEntity.COOKED) {
			return lerp(0xFFEEEEEE, 0xFFE89C45, doneness / SpitBlockEntity.COOKED);
		}
		if (doneness <= SpitBlockEntity.BURNT) {
			return lerp(0xFFE89C45, 0xFF704020,
				(doneness - SpitBlockEntity.COOKED) / (SpitBlockEntity.BURNT - SpitBlockEntity.COOKED));
		}
		return lerp(0xFF704020, 0xFF302018, (doneness - SpitBlockEntity.BURNT) / SpitBlockEntity.BURNT);
	}

	private static int lerp(int from, int to, float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		int a = Math.round(Mth.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF));
		int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.has) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 1.05F, 0.5F);
		poseStack.rotateDegrees(Axis.YP, state.turn);
		if (state.rip > 0) {
			float shake = Mth.sin(state.rip * 0.9F) * 0.04F;
			poseStack.translate(0.0F, shake, 0.0F);
		}
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.tint);
		poseStack.popPose();
	}
}
