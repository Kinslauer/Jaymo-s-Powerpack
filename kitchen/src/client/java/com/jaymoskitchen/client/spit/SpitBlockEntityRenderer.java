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
import com.jaymoskitchen.corpse.CorpseData;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.corpse.Corpses;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
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
		@Nullable Entity corpseEntity;
		@Nullable EntityRenderState corpseState;
		@Nullable EntityRenderer<Entity, EntityRenderState> corpseRenderer;
	}

	/** How high the frame's crossbar runs above the spit block's floor (blocks; kitchen/tools/gen_spit_frame.py prints it). */
	static final float BAR_HEIGHT = 0.204F;

	/** The game time a tear started at each position, for the tearing countdown. */
	private static final Map<BlockPos, Long> RIP_START = new HashMap<>();

	/** The client plays the tearing animation here when the server reports a chunk torn off. */
	public static void ripAt(BlockPos pos, long startTick) {
		RIP_START.put(pos, startTick);
	}

	private final ItemModelResolver itemModels;

	public SpitBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

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
		if (state.has && state.corpseState == null) {
			itemModels.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED, be.getLevel(), null, 0);
		}
		state.turn = (be.getLevel().getGameTime() + partialTicks) * 2.0F % 360.0F;
		state.tint = tintFor(be.doneness());
		Long ripStart = RIP_START.get(be.getBlockPos());
		if (ripStart != null && be.getLevel().getGameTime() - ripStart >= 8) {
			RIP_START.remove(be.getBlockPos());
			ripStart = null;
		}
		state.rip = ripStart == null ? 0 : (int) Mth.clamp(8 - (be.getLevel().getGameTime() - ripStart), 0, 8);
		state.corpseEntity = null;
		state.corpseState = null;
		state.corpseRenderer = null;
		if (state.has && stack.is(Corpses.CORPSE_ITEM)) {
			CorpseData data = stack.get(Corpses.CORPSE_DATA);
			if (data != null) {
				CorpseEntity corpse = new CorpseEntity(Corpses.CORPSE, be.getLevel());
				corpse.restore(data);
				Entity body = corpse.clientBody(0.0F);
				if (body != null) {
					EntityRenderer rawRenderer = (EntityRenderer) net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body);
					state.corpseRenderer = (EntityRenderer<Entity, EntityRenderState>) rawRenderer;
					state.corpseState = rawRenderer.createRenderState(body, partialTicks);
					rawRenderer.extractRenderState(body, state.corpseState, partialTicks);
					state.corpseEntity = corpse;
				}
			}
		}
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
		poseStack.translate(0.5F, BAR_HEIGHT, 0.5F);
		poseStack.rotateDegrees(Axis.XP, state.turn);
		if (state.corpseState != null && state.corpseRenderer != null) {
			state.corpseRenderer.submit(state.corpseState, poseStack, collector, camera);
		} else {
			if (state.rip > 0) {
				float shake = Mth.sin(state.rip * 0.9F) * 0.04F;
				poseStack.translate(0.0F, shake, 0.0F);
			}
			state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.tint);
		}
		poseStack.popPose();
	}
}
