package com.jaymospowers.client;

import com.jaymospowers.core.BlockCluster;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A torn-out chunk of blocks: each block drawn where it sits in the chunk, the whole thing tumbling as it flies. */
public final class BlockClusterRenderer extends EntityRenderer<BlockCluster, BlockClusterRenderer.State> {
	public static final class State extends EntityRenderState {
		final List<MovingBlockRenderState> blocks = new ArrayList<>();
		final List<Vec3> offsets = new ArrayList<>();
		float tumble;
		float yaw;
	}

	public BlockClusterRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.7F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BlockCluster entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		List<BlockCluster.Piece> pieces = entity.pieces();
		AABB bounds = BlockCluster.bounds(pieces);
		Vec3 middle = bounds.getCenter();
		state.blocks.clear();
		state.offsets.clear();
		BlockPos at = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
		for (BlockCluster.Piece piece : pieces) {
			MovingBlockRenderState block = new MovingBlockRenderState();
			block.blockState = piece.blockState();
			block.blockPos = at;
			block.randomSeedPos = at.offset(piece.x(), piece.y(), piece.z());
			if (entity.level() instanceof ClientLevel level) {
				block.biome = level.getBiome(at);
				block.cardinalLighting = level.cardinalLighting();
				block.lightEngine = level.getLightEngine();
			}
			state.blocks.add(block);
			state.offsets.add(new Vec3(piece.x() - middle.x, piece.y() - middle.y, piece.z() - middle.z));
		}
		// a thrown chunk tumbles; a held one turns slowly
		double speed = entity.getDeltaMovement().length();
		state.tumble = (entity.tickCount + partialTicks) * (float)Math.min(25.0, 2.0 + speed * 18.0);
		state.yaw = entity.getId() * 37.0F;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.7F, 0.0F);
		poseStack.rotateDegrees(Axis.YP, state.yaw);
		poseStack.rotateDegrees(Axis.XP, state.tumble * 0.6F);
		poseStack.rotateDegrees(Axis.ZP, state.tumble * 0.25F);
		// the whole chunk shrunk a touch, so it reads as one lump rather than a wall section
		poseStack.scale(0.8F, 0.8F, 0.8F);
		for (int i = 0; i < state.blocks.size(); i++) {
			MovingBlockRenderState block = state.blocks.get(i);
			if (block.blockState.getRenderShape() != RenderShape.MODEL) {
				continue;
			}
			Vec3 off = state.offsets.get(i);
			poseStack.pushPose();
			poseStack.translate(off.x, off.y, off.z);
			collector.submitMovingBlock(poseStack, block, state.outlineColor);
			poseStack.popPose();
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
