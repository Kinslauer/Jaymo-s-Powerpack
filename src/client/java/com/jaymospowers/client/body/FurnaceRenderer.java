package com.jaymospowers.client.body;

import com.jaymospowers.body.FurnaceHeat;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What cooks in a furnace, smoker or blast furnace shows in its mouth (after Hands-On Vanilla's idea; the code is our
 * own): the ore or the food inside, flat against the opening, dark until the fuel catches and then glowing brighter and
 * brighter as it cooks through (its light climbing from the block's own to full). The block entity's tag reaches the
 * client through FurnaceSyncMixin.
 */
public class FurnaceRenderer implements BlockEntityRenderer<AbstractFurnaceBlockEntity, FurnaceRenderer.State> {
	// ===== the knobs ================================================================================================
	/** The item's size against a block, the height of the mouth's middle, and how far it stands off the face (blocks). */
	static final float SIZE = 0.34F, MOUTH_HEIGHT = 0.36F, OFF_FACE = 0.012F;
	/** How flat the item is pressed against the face (a share of its depth). */
	static final float FLAT = 0.02F;
	/** The light level (0-15) the item glows at when the fuel has just caught, and at the end of the cooking. */
	static final int GLOW_START = 6, GLOW_FULL = 15;
	/** The glow's flicker (a share of the item's size) and its speed (radians a tick). */
	static final float FLICKER = 0.025F, FLICKER_SPEED = 0.5F;
	// ================================================================================================================

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		boolean has;
		Direction facing = Direction.NORTH;
		float time;
	}

	private final ItemModelResolver itemModels;

	public FurnaceRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(AbstractFurnaceBlockEntity furnace, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(furnace, state, partialTicks, camera, breakProgress);
		ItemStack input = furnace.getItem(0);
		state.has = !input.isEmpty() && furnace.getLevel() != null && furnace.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING);
		if (!state.has) {
			return;
		}
		state.facing = furnace.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
		state.time = furnace.getLevel().getGameTime() + partialTicks;
		this.itemModels.updateForTopItem(state.item, input, ItemDisplayContext.GUI, furnace.getLevel(), null, (int)furnace.getBlockPos().asLong());
		// lit from the air in front of the mouth (the block itself is solid), and brighter as it heats
		int front = LightCoordsUtil.getLightCoords(furnace.getLevel(), furnace.getBlockPos().relative(state.facing));
		FurnaceHeat heat = (FurnaceHeat)furnace;
		int glow = heat.jaymospowers$burning() ? Math.round(Mth.lerp(heat.jaymospowers$cooked(), GLOW_START, GLOW_FULL)) : 0;
		state.lightCoords = glow > 0 ? LightCoordsUtil.max(front, LightCoordsUtil.pack(glow, 0)) : front;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.has) {
			return;
		}
		float flicker = state.lightCoords != 0 ? 1.0F + Mth.sin(state.time * FLICKER_SPEED) * FLICKER : 1.0F;
		poseStack.pushPose();
		poseStack.translate(0.5F, MOUTH_HEIGHT, 0.5F);
		// the front is +Z once turned by minus the facing's yaw (south is 0, west 90)
		poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot());
		poseStack.translate(0.0F, 0.0F, 0.5F + OFF_FACE);
		poseStack.scale(SIZE * flicker, SIZE * flicker, SIZE * FLAT);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}
}
