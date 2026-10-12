package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FallingTree;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/** (0.5.9) A felled tree toppling: every block of it drawn where it stood, the whole tree turned about the cut. */
public final class FallingTreeRenderer extends EntityRenderer<FallingTree, FallingTreeRenderer.State> {
	public static final class State extends EntityRenderState {
		final List<MovingBlockRenderState> blocks = new ArrayList<>();
		final List<FallingTree.Piece> pieces = new ArrayList<>();
		Quaternionf turn = new Quaternionf();
	}

	public FallingTreeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	/** Culled by the whole tree's reach, not its little box at the cut. */
	@Override
	protected AABB getBoundingBoxForCulling(FallingTree entity, float partialTicks) {
		int reach = 2;
		for (FallingTree.Piece p : entity.pieces()) {
			reach = Math.max(reach, Math.max(Math.abs(p.x()), Math.max(Math.abs(p.y()), Math.abs(p.z()))) + 1);
		}
		return entity.getBoundingBox().inflate(reach);
	}

	@Override
	public void extractRenderState(FallingTree entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.blocks.clear();
		state.pieces.clear();
		BlockPos at = entity.blockPosition();
		for (FallingTree.Piece piece : entity.pieces()) {
			MovingBlockRenderState block = new MovingBlockRenderState();
			block.blockState = piece.blockState();
			block.blockPos = at.offset(piece.x(), Math.max(0, piece.y()), piece.z());
			block.randomSeedPos = at.offset(piece.x(), piece.y(), piece.z());
			if (entity.level() instanceof ClientLevel level) {
				block.biome = level.getBiome(at);
				block.cardinalLighting = level.cardinalLighting();
				block.lightEngine = level.getLightEngine();
			}
			state.blocks.add(block);
			state.pieces.add(piece);
		}
		state.turn = FallingTree.turn(Mth.lerp(partialTicks, entity.leanO, entity.lean()), entity.heading());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotate(state.turn);
		for (int i = 0; i < state.blocks.size(); i++) {
			MovingBlockRenderState block = state.blocks.get(i);
			if (block.blockState.getRenderShape() != RenderShape.MODEL) {
				continue;
			}
			FallingTree.Piece p = state.pieces.get(i);
			poseStack.pushPose();
			// (a piece's place is its middle's offset from the cut's foot; a block is drawn from its corner)
			poseStack.translate(p.x() - 0.5F, p.y(), p.z() - 0.5F);
			collector.submitMovingBlock(poseStack, block, state.outlineColor);
			poseStack.popPose();
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
