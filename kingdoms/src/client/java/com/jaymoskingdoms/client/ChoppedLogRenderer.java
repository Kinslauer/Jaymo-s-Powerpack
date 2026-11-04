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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.25) A chopped log: the log it was. (0.7.510) Drawn whole, as Timber Tales has it, with the game's own cracks cut
 * into it deeper for every chop (or deeper still while someone's axe is at it).
 */
public final class ChoppedLogRenderer implements BlockEntityRenderer<ChoppedLog.Entity, ChoppedLogRenderer.State> {
	public static final class State extends BlockEntityRenderState {
		final MovingBlockRenderState block = new MovingBlockRenderState();
		/** The log's cracks (null: none), how deep (0-9), and whether its model is see-through. */
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
		// (the game draws a block's cracks on its own model, empty here: so they're drawn on the log; each chop leaves
		// them deeper, the deepest at the most a log takes, and a swing in progress shows deeper ones while it lasts)
		int stage = Math.min(9, Math.round(chops * 9.0F / ChoppedLog.MOST));
		if (breakProgress != null) {
			stage = Math.max(stage, breakProgress.progress());
		}
		state.cracks = null;
		if (stage > 0 && log.log().getRenderShape() == RenderShape.MODEL) {
			BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(log.log());
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(log.log().getSeed(log.getBlockPos())), parts);
			state.cracks = List.copyOf(parts);
			state.crackStage = stage;
			state.cutout = model.hasMaterialFlag(1);
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.block.blockState == null || state.block.blockState.getRenderShape() != RenderShape.MODEL) {
			return;
		}
		poseStack.pushPose();
		collector.submitMovingBlock(poseStack, state.block, 0);
		if (state.cracks != null) {
			collector.submitBreakingBlockModel(poseStack, state.cracks, state.crackStage, state.cutout);
		}
		poseStack.popPose();
	}
}
