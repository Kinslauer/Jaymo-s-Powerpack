package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FallingTree;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/**
 * (0.5.9) A felled tree toppling: every block of it drawn where it stood, the whole tree turned about the cut.
 * <p>
 * A fallen tree lies as this until it's chopped up, and a great one is hundreds of blocks drawn afresh every frame (Andy,
 * 2026-11-26, a mangrove swamp on fire at 2 to 6 frames a second). So what it draws is worked out once for each shape it
 * takes and kept ({@link Shape}): a block with tree on all six sides is hidden and left out (a leaf behind other pieces
 * the way the pack's leaf culling leaves out the inside of a canopy; a log only behind solid wood), and each block sees
 * the rest of its tree beside it, so the faces wood turns to wood aren't drawn either.
 */
public final class FallingTreeRenderer extends EntityRenderer<FallingTree, FallingTreeRenderer.State> {
	public static final class State extends EntityRenderState {
		List<? extends MovingBlockRenderState> blocks = List.of();
		List<FallingTree.Piece> pieces = List.of();
		Quaternionf turn = new Quaternionf();
	}

	/** What a tree draws, worked out from its pieces and where it is: rebuilt only when either changes. */
	private record Shape(List<FallingTree.Piece> from, BlockPos at, List<TreeBlock> blocks, List<FallingTree.Piece> pieces, int reach) {
	}

	/** One of its blocks, which sees the tree's other blocks round it (so its hidden faces aren't drawn). */
	private static final class TreeBlock extends MovingBlockRenderState {
		final Long2ObjectOpenHashMap<BlockState> tree;

		TreeBlock(Long2ObjectOpenHashMap<BlockState> tree) {
			this.tree = tree;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			return pos.equals(this.blockPos) ? this.blockState : this.tree.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
		}
	}

	private final Map<FallingTree, Shape> shapes = new WeakHashMap<>();

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
		return entity.getBoundingBox().inflate(this.shape(entity).reach());
	}

	@Override
	public void extractRenderState(FallingTree entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		Shape shape = this.shape(entity);
		state.blocks = shape.blocks();
		state.pieces = shape.pieces();
		state.turn = FallingTree.turn(Mth.lerp(partialTicks, entity.leanO, entity.lean()), entity.heading());
	}

	/** The tree's shape as it is now: the one kept, unless its pieces or its place have changed since. */
	private Shape shape(FallingTree entity) {
		List<FallingTree.Piece> pieces = entity.pieces();
		BlockPos at = entity.blockPosition();
		Shape shape = this.shapes.get(entity);
		if (shape == null || shape.from() != pieces || !shape.at().equals(at)) {
			shape = build(entity, pieces, at);
			this.shapes.put(entity, shape);
		}
		return shape;
	}

	private static Shape build(FallingTree entity, List<FallingTree.Piece> pieces, BlockPos at) {
		// every piece by where it stands relative to the cut, and (for its neighbours' sake) where it's drawn
		Long2ObjectOpenHashMap<BlockState> byOffset = new Long2ObjectOpenHashMap<>(pieces.size());
		Long2ObjectOpenHashMap<BlockState> drawn = new Long2ObjectOpenHashMap<>(pieces.size());
		int reach = 2;
		for (FallingTree.Piece p : pieces) {
			BlockState s = p.blockState();
			byOffset.put(BlockPos.asLong(p.x(), p.y(), p.z()), s);
			// (a piece under the cut is drawn clamped to its foot: it isn't anyone's neighbour)
			if (p.y() >= 0) {
				drawn.put(at.offset(p.x(), p.y(), p.z()).asLong(), s);
			}
			reach = Math.max(reach, Math.max(Math.abs(p.x()), Math.max(Math.abs(p.y()), Math.abs(p.z()))) + 1);
		}
		List<TreeBlock> blocks = new ArrayList<>(pieces.size());
		List<FallingTree.Piece> shown = new ArrayList<>(pieces.size());
		ClientLevel level = entity.level() instanceof ClientLevel l ? l : null;
		for (FallingTree.Piece p : pieces) {
			BlockState s = p.blockState();
			if (s.getRenderShape() != RenderShape.MODEL || hidden(byOffset, p, s)) {
				continue;
			}
			TreeBlock block = new TreeBlock(drawn);
			block.blockState = s;
			block.blockPos = at.offset(p.x(), Math.max(0, p.y()), p.z());
			block.randomSeedPos = at.offset(p.x(), p.y(), p.z());
			if (level != null) {
				block.biome = level.getBiome(at);
				block.cardinalLighting = level.cardinalLighting();
				block.lightEngine = level.getLightEngine();
			}
			blocks.add(block);
			shown.add(p);
		}
		return new Shape(pieces, at.immutable(), List.copyOf(blocks), List.copyOf(shown), reach);
	}

	/** A piece with tree on all six sides can't be seen: a leaf behind any of its pieces, a log only behind solid ones. */
	private static boolean hidden(Long2ObjectOpenHashMap<BlockState> tree, FallingTree.Piece p, BlockState s) {
		boolean leaf = s.getBlock() instanceof LeavesBlock;
		for (Direction d : Direction.values()) {
			BlockState n = tree.get(BlockPos.asLong(p.x() + d.getStepX(), p.y() + d.getStepY(), p.z() + d.getStepZ()));
			if (n == null || !leaf && !n.canOcclude()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotate(state.turn);
		for (int i = 0; i < state.blocks.size(); i++) {
			FallingTree.Piece p = state.pieces.get(i);
			poseStack.pushPose();
			// (a piece's place is its middle's offset from the cut's foot; a block is drawn from its corner)
			poseStack.translate(p.x() - 0.5F, p.y(), p.z() - 0.5F);
			collector.submitMovingBlock(poseStack, state.blocks.get(i), state.outlineColor);
			poseStack.popPose();
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
