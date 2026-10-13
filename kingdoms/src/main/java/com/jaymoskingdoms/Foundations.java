package com.jaymoskingdoms;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * (0.6.3) No building floats: when any mod's surface structure is placed in a chunk, every column of a piece whose
 * lowest blocks hang over a small drop (up to {@link #DEPTH_MAX} of open air before natural ground) is filled down to
 * the ground with that ground (dirt under grass). Docks over water, bridges over deep gaps and sky structures are left
 * alone: a column over water, or with no ground within reach, isn't filled. Street pieces that follow the terrain are
 * skipped. Run from {@code StructureStartMixin} after the chunk's pieces are placed.
 */
public final class Foundations {
	/** The deepest gap filled under a piece; how many of a piece's lowest layers are looked in for its bottom block. */
	static final int DEPTH_MAX = 6, BOTTOM_LAYERS = 3;

	private Foundations() {
	}

	public static void fill(WorldGenLevel level, Structure structure, List<StructurePiece> pieces, BoundingBox chunk) {
		if (structure.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
			return;
		}
		for (StructurePiece piece : pieces) {
			BoundingBox box = piece.getBoundingBox();
			if (!box.intersects(chunk) || box.minY() - DEPTH_MAX <= level.getMinY() || (piece instanceof PoolElementStructurePiece pool
				&& pool.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING)) {
				continue;
			}
			for (int x = Math.max(box.minX(), chunk.minX()); x <= Math.min(box.maxX(), chunk.maxX()); x++) {
				for (int z = Math.max(box.minZ(), chunk.minZ()); z <= Math.min(box.maxZ(), chunk.maxZ()); z++) {
					column(level, box, x, z);
				}
			}
		}
	}

	/** Fills the drop under the piece's lowest full block in column x/z, if it's a short drop of air onto natural ground. */
	private static void column(WorldGenLevel level, BoundingBox box, int x, int z) {
		BlockPos bottom = null;
		for (int y = box.minY(); y < box.minY() + BOTTOM_LAYERS && y <= box.maxY(); y++) {
			BlockPos at = new BlockPos(x, y, z);
			if (level.getBlockState(at).isCollisionShapeFullBlock(level, at)) {
				bottom = at;
				break;
			}
		}
		if (bottom == null) {
			return;
		}
		for (int d = 1; d <= DEPTH_MAX + 1; d++) {
			BlockPos at = bottom.below(d);
			BlockState s = level.getBlockState(at);
			if (!s.getFluidState().isEmpty()) {
				return;
			}
			if (s.isAir() || s.canBeReplaced()) {
				continue;
			}
			if (d == 1 || !s.isCollisionShapeFullBlock(level, at) || !Castle.natural(s) || s.is(net.minecraft.tags.BlockTags.LEAVES)
				|| s.is(net.minecraft.tags.BlockTags.LOGS)) {
				return;
			}
			BlockState fill = filler(s);
			for (int k = 1; k < d; k++) {
				level.setBlock(bottom.below(k), fill, Block.UPDATE_CLIENTS);
			}
			return;
		}
	}

	/** What a gap over {@code ground} is filled with: dirt under grass and the like, else the ground itself. */
	private static BlockState filler(BlockState ground) {
		return ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MYCELIUM) || ground.is(Blocks.DIRT_PATH)
			|| ground.is(Blocks.SNOW_BLOCK) ? Blocks.DIRT.defaultBlockState() : ground;
	}
}
