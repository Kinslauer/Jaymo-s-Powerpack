package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.smeltery.CastingEntity;
import com.jaymospowers.tinkering.smeltery.Molten;
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
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A casting table's cast lying on top, the metal filling over it, and the result once cooled. A basin shows the metal
 * rising inside it, then the block it made.
 */
public class CastingRenderer implements BlockEntityRenderer<CastingEntity, CastingRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState cast = new ItemStackRenderState();
		final ItemStackRenderState output = new ItemStackRenderState();
		boolean hasCast, hasOutput, basin;
		@Nullable Molten molten;
		float fill;
		int lightAbove;
	}

	private final ItemModelResolver itemModels;
	private final SpriteGetter sprites;

	public CastingRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
		this.sprites = context.sprites();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CastingEntity casting, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(casting, state, partialTicks, camera, breakProgress);
		state.basin = casting.basin();
		ItemStack cast = casting.cast(), output = casting.output();
		state.hasCast = !cast.isEmpty();
		state.hasOutput = !output.isEmpty();
		if (state.hasCast) {
			itemModels.updateForTopItem(state.cast, cast, ItemDisplayContext.FIXED, casting.getLevel(), null, 0);
		}
		if (state.hasOutput) {
			itemModels.updateForTopItem(state.output, output, state.basin ? ItemDisplayContext.NONE : ItemDisplayContext.FIXED,
				casting.getLevel(), null, 1);
		}
		state.molten = casting.molten();
		state.fill = casting.needed() > 0 ? Math.min(1.0F, casting.amount() / (float) casting.needed()) : 0.0F;
		state.lightAbove = casting.getLevel() != null
			? LightCoordsUtil.getLightCoords(casting.getLevel(), casting.getBlockPos().above()) : state.lightCoords;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.basin) {
			if (state.molten != null && state.fill > 0) {
				float y0 = 4 / 16.0F, y1 = y0 + (15 / 16.0F - y0) * state.fill;
				MoltenQuads.box(poseStack, collector, MoltenQuads.sprite(sprites, state.molten), 2 / 16.0F, y0, 2 / 16.0F, 14 / 16.0F, y1,
					14 / 16.0F, true);
			}
			if (state.hasOutput) {
				poseStack.pushPose();
				poseStack.translate(0.5F, 4 / 16.0F + 0.375F, 0.5F);
				poseStack.scale(0.75F, 0.75F, 0.75F);
				state.output.submit(poseStack, collector, state.lightAbove, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
			return;
		}
		if (state.hasCast) {
			flat(state.cast, poseStack, collector, state.lightAbove, 1.0F + 1 / 64.0F);
		}
		if (state.molten != null && state.fill > 0) {
			float y0 = 1.0F, y1 = 1.0F + 1 / 32.0F + state.fill / 32.0F;
			MoltenQuads.box(poseStack, collector, MoltenQuads.sprite(sprites, state.molten), 2 / 16.0F, y0, 2 / 16.0F, 14 / 16.0F, y1,
				14 / 16.0F, true);
		}
		if (state.hasOutput) {
			flat(state.output, poseStack, collector, state.lightAbove, 1.0F + 2 / 64.0F);
		}
	}

	/** An item lying flat on the table top. */
	private static void flat(ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector collector, int light, float y) {
		poseStack.pushPose();
		poseStack.translate(0.5F, y, 0.5F);
		poseStack.rotateDegrees(Axis.XP, 90.0F);
		poseStack.scale(0.8F, 0.8F, 0.8F);
		item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
