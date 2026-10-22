package com.jaymospowers.client.aether;

import com.jaymospowers.aether.FloatingBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;

/** A rising gravitite block: the block itself, drawn where the entity is (as falling sand is, upside down in time). */
public final class FloatingBlockRenderer extends EntityRenderer<FloatingBlockEntity, FloatingBlockRenderer.State> {
	public static final class State extends EntityRenderState {
		final MovingBlockRenderState block = new MovingBlockRenderState();
	}

	public FloatingBlockRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FloatingBlockEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		BlockPos at = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
		state.block.blockState = entity.blockState();
		state.block.blockPos = at;
		state.block.randomSeedPos = at;
		if (entity.level() instanceof ClientLevel level) {
			state.block.biome = level.getBiome(at);
			state.block.cardinalLighting = level.cardinalLighting();
			state.block.lightEngine = level.getLightEngine();
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.block.blockState.getRenderShape() == RenderShape.MODEL) {
			poseStack.pushPose();
			// the entity's origin is the middle of its bottom face; the block is drawn from its corner
			poseStack.translate(-0.5F, 0.0F, -0.5F);
			collector.submitMovingBlock(poseStack, state.block, state.outlineColor);
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}
}
