package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ChoppedLog;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** (0.5.25) A chopped log: the log it was, drawn thinner about its axis by the chops in it. */
public final class ChoppedLogRenderer implements BlockEntityRenderer<ChoppedLog.Entity, ChoppedLogRenderer.State> {
	public static final class State extends BlockEntityRenderState {
		final MovingBlockRenderState block = new MovingBlockRenderState();
		float width = 1.0F;
		Direction.Axis axis = Direction.Axis.Y;
		/** The log's cracks while someone's chopping at it (null: none), and whether its model is see-through. */
		@Nullable List<BlockStateModelPart> cracks;
		int crackStage;
		boolean cutout;
	}

	public ChoppedLogRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ChoppedLog.Entity log, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(log, state, partialTicks, camera, breakProgress);
		state.block.blockState = log.log();
		state.block.blockPos = log.getBlockPos();
		state.block.randomSeedPos = log.getBlockPos();
		if (log.getLevel() instanceof ClientLevel level) {
			state.block.biome = level.getBiome(log.getBlockPos());
			state.block.cardinalLighting = level.cardinalLighting();
			state.block.lightEngine = level.getLightEngine();
		}
		int chops = log.getBlockState().hasProperty(ChoppedLog.CHOPS) ? log.getBlockState().getValue(ChoppedLog.CHOPS) : 1;
		state.width = (16 - 2 * chops) / 16.0F;
		// (the game draws a block's cracks on its own model, empty here: so they're drawn on the log, as it narrows)
		state.cracks = null;
		if (breakProgress != null && log.log().getRenderShape() == RenderShape.MODEL) {
			BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(log.log());
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(log.log().getSeed(log.getBlockPos())), parts);
			state.cracks = List.copyOf(parts);
			state.crackStage = breakProgress.progress();
			state.cutout = model.hasMaterialFlag(1);
		}
		state.axis = log.getBlockState().hasProperty(ChoppedLog.AXIS) ? log.getBlockState().getValue(ChoppedLog.AXIS) : Direction.Axis.Y;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.block.blockState == null || state.block.blockState.getRenderShape() != RenderShape.MODEL) {
			return;
		}
		float w = state.width;
		poseStack.pushPose();
		// narrowed across its axis, about the block's middle
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.scale(state.axis == Direction.Axis.X ? 1.0F : w, state.axis == Direction.Axis.Y ? 1.0F : w, state.axis == Direction.Axis.Z ? 1.0F : w);
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		collector.submitMovingBlock(poseStack, state.block, 0);
		if (state.cracks != null) {
			collector.submitBreakingBlockModel(poseStack, state.cracks, state.crackStage, state.cutout);
		}
		poseStack.popPose();
	}
}
