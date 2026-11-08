package com.jaymoskitchen.client.rats;

import com.jaymoskitchen.rats.CageTrapBlock;
import com.jaymoskitchen.rats.CageTrapEntity;
import com.jaymoskitchen.rats.Rat;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws what's in a cage trap (the cage itself is the block's model): the bait at the back, or the rat it caught,
 * facing the door, sniffing at the bars now and then.
 */
public class CageTrapRenderer implements BlockEntityRenderer<CageTrapEntity, CageTrapRenderer.State> {
	/** The caught rat's size in the cage, and how often (ticks) and how long it sniffs. */
	static final float RAT_SCALE = 0.75F;
	static final int SNIFF_EVERY = 80, SNIFF_FOR = 30;
	/** The board's top (blocks). */
	static final float FLOOR = 1.0F / 16.0F;

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState bait = new ItemStackRenderState();
		final RatRenderer.State rat = new RatRenderer.State();
		boolean baited, caught;
		Direction facing = Direction.NORTH;
	}

	private final RatModel ratModel;
	private final ItemModelResolver itemModels;

	public CageTrapRenderer(BlockEntityRendererProvider.Context context) {
		this.ratModel = new RatModel(context.bakeLayer(RatModel.LAYER));
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CageTrapEntity cage, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(cage, state, partialTicks, camera, breakProgress);
		BlockState block = cage.getBlockState();
		state.facing = block.getValue(HorizontalDirectionalBlock.FACING);
		state.caught = block.getValue(CageTrapBlock.CAUGHT);
		ItemStack bait = cage.bait();
		state.baited = !bait.isEmpty() && !state.caught;
		if (state.baited) {
			itemModels.updateForTopItem(state.bait, bait, ItemDisplayContext.FIXED, cage.getLevel(), null, 0);
		}
		if (state.caught) {
			RatRenderer.State rat = state.rat;
			float time = cage.getLevel() == null ? 0.0F : cage.getLevel().getGameTime() % 24000L + partialTicks;
			// each cage's rat on its own clock
			time += Math.floorMod(cage.getBlockPos().hashCode(), SNIFF_EVERY);
			rat.ageInTicks = time;
			float phase = time % SNIFF_EVERY;
			rat.sniff = phase < SNIFF_FOR ? Rat.SNIFF_FULL * Mth.sin(phase / SNIFF_FOR * Mth.PI) : 0.0F;
			rat.coat = Math.floorMod(block.getValue(CageTrapBlock.COAT), RatRenderer.COATS.length);
			rat.lightCoords = state.lightCoords;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.baited) {
			poseStack.pushPose();
			// at the back of the cage, lying on the board
			poseStack.translate(0.5F - state.facing.getStepX() * 0.3F, FLOOR + 0.02F, 0.5F - state.facing.getStepZ() * 0.3F);
			poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot());
			poseStack.rotateDegrees(Axis.XP, 90.0F);
			poseStack.scale(0.35F, 0.35F, 0.35F);
			state.bait.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
		if (state.caught) {
			poseStack.pushPose();
			// as a living rat is drawn, facing the door
			poseStack.translate(0.5F, FLOOR, 0.5F);
			poseStack.rotateDegrees(Axis.YP, 180.0F - state.facing.toYRot());
			poseStack.scale(RAT_SCALE, RAT_SCALE, RAT_SCALE);
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			collector.submitModel(ratModel, state.rat, poseStack, RatRenderer.COATS[state.rat.coat], state.lightCoords, OverlayTexture.NO_OVERLAY,
				0);
			poseStack.popPose();
		}
	}
}
