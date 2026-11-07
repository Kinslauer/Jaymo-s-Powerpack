package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.SinglePoolElementAccessor;
import com.jaymoskingdoms.mixin.StructureTemplateAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.533) No building is full of earth. Dungeons and Taverns saves its taverns with structure voids where the rooms
 * are (no air blocks), and counts on the ground being carved away round the building ({@code beard_box}). That carving
 * is gentle: on flat sand it clears the rooms, but set into a hill or the pack's rougher ground it leaves them packed
 * with stone and dirt, doors and all (Andy's desert tavern, 2026-11-04).
 *
 * <p>So when a surface structure's piece is placed in a chunk, every cell inside one of its rooms that no piece of the
 * structure put a block in (its own template left a void there, and no piece placed inside it, like the taverns' tables
 * and stalls, filled it) is cleared to air. A room is where the piece's own blocks stand on all four sides of the cell
 * (looking along the same level) and over it. Fluids and anything with a block entity are left alone. Run from
 * {@code StructureStartMixin}, before {@link Foundations}.
 */
public final class BuriedRooms {
	private BuriedRooms() {
	}

	/** One piece's template, placed: every cell it saves a block in, and those that aren't air. */
	private record Shape(BoundingBox box, LongOpenHashSet all, LongOpenHashSet solid) {
	}

	public static void clear(WorldGenLevel level, Structure structure, List<StructurePiece> pieces, BoundingBox chunk) {
		if (structure.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return;
		}
		StructureTemplateManager templates = server.getStructureTemplateManager();
		List<Shape> shapes = new ArrayList<>();
		List<Boolean> rigid = new ArrayList<>();
		for (StructurePiece piece : pieces) {
			if (!piece.getBoundingBox().intersects(chunk) || !(piece instanceof PoolElementStructurePiece pool)
				|| !(pool.getElement() instanceof SinglePoolElement element)) {
				continue;
			}
			Shape shape = shape(templates, pool, element);
			if (shape != null) {
				shapes.add(shape);
				rigid.add(element.getProjection() == StructureTemplatePool.Projection.RIGID);
			}
		}
		if (shapes.isEmpty()) {
			return;
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int i = 0; i < shapes.size(); i++) {
			if (!rigid.get(i)) {
				continue;
			}
			Shape shape = shapes.get(i);
			BoundingBox box = shape.box();
			for (int x = Math.max(box.minX(), chunk.minX()); x <= Math.min(box.maxX(), chunk.maxX()); x++) {
				for (int z = Math.max(box.minZ(), chunk.minZ()); z <= Math.min(box.maxZ(), chunk.maxZ()); z++) {
					for (int y = box.minY(); y <= box.maxY(); y++) {
						long key = BlockPos.asLong(x, y, z);
						if (taken(shapes, key) || !inRoom(shape, x, y, z)) {
							continue;
						}
						at.set(x, y, z);
						BlockState state = level.getBlockState(at);
						if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
							continue;
						}
						level.setBlock(at, air, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/** Some piece put a block (air counts) at {@code key}. */
	private static boolean taken(List<Shape> shapes, long key) {
		for (Shape s : shapes) {
			if (s.all().contains(key)) {
				return true;
			}
		}
		return false;
	}

	/** The piece's own blocks stand over the cell and on all four sides of it, at its level. */
	private static boolean inRoom(Shape s, int x, int y, int z) {
		BoundingBox box = s.box();
		return any(s, x, y, z, 0, 1, 0, box) && any(s, x, y, z, 1, 0, 0, box) && any(s, x, y, z, -1, 0, 0, box)
			&& any(s, x, y, z, 0, 0, 1, box) && any(s, x, y, z, 0, 0, -1, box);
	}

	private static boolean any(Shape s, int x, int y, int z, int dx, int dy, int dz, BoundingBox box) {
		for (x += dx, y += dy, z += dz; box.isInside(x, y, z); x += dx, y += dy, z += dz) {
			if (s.solid().contains(BlockPos.asLong(x, y, z))) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Shape shape(StructureTemplateManager templates, PoolElementStructurePiece piece, SinglePoolElement element) {
		StructureTemplate template = ((SinglePoolElementAccessor) element).jaymoskingdoms$template()
			.map(id -> templates.get(id).orElse(null), t -> t);
		if (template == null) {
			return null;
		}
		List<StructureTemplate.Palette> palettes = ((StructureTemplateAccessor) template).jaymoskingdoms$palettes();
		if (palettes.isEmpty()) {
			return null;
		}
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation());
		BlockPos origin = piece.getPosition();
		LongOpenHashSet all = new LongOpenHashSet(), solid = new LongOpenHashSet();
		for (StructureTemplate.Palette palette : palettes) {
			for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
				long key = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin).asLong();
				all.add(key);
				if (!info.state().isAir()) {
					solid.add(key);
				}
			}
		}
		return new Shape(piece.getBoundingBox(), all, solid);
	}
}
