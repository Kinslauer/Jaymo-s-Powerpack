package com.jaymospowers.client.buildblocks;

import com.jaymospowers.buildblocks.MixedSlabEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws the two slabs of a mixed slab, each with its own slab's model, lit as the block they share. */
public class MixedSlabRenderer implements BlockEntityRenderer<MixedSlabEntity, MixedSlabRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final MovingBlockRenderState bottom = new MovingBlockRenderState();
		final MovingBlockRenderState top = new MovingBlockRenderState();
		boolean ready;
	}

	public MixedSlabRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(MixedSlabEntity slabs, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(slabs, state, partialTicks, camera, breakProgress);
		state.ready = slabs.getLevel() instanceof ClientLevel;
		if (!state.ready) {
			return;
		}
		ClientLevel level = (ClientLevel) slabs.getLevel();
		BlockPos pos = slabs.getBlockPos();
		fill(state.bottom, slabs.bottom(), level, pos);
		fill(state.top, slabs.top(), level, pos);
	}

	private static void fill(MovingBlockRenderState moving, BlockState block, ClientLevel level, BlockPos pos) {
		moving.blockState = block;
		moving.blockPos = pos;
		moving.randomSeedPos = pos;
		moving.biome = level.getBiome(pos);
		moving.cardinalLighting = level.cardinalLighting();
		moving.lightEngine = level.getLightEngine();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.ready) {
			collector.submitMovingBlock(poseStack, state.bottom, 0);
			collector.submitMovingBlock(poseStack, state.top, 0);
		}
	}
}
