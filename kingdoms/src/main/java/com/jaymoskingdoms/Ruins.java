package com.jaymoskingdoms;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Old structures the world generated (village houses, ATi's ruins and keeps, temples...) as ground a faction's builders
 * may clear for a planned building ({@link Planner}, {@link Construction}). A block counts as a structure's when it lies
 * inside one of its pieces; only structures whose start chunk is loaded are seen, so anything unseen is left alone.
 * What's never torn down, whatever piece it's in: a bell, a bed or job block a living villager has claimed (a site with
 * one is refused outright, so a living village keeps its homes), anything unbreakable; and no site on old structures
 * comes near any faction's seat, keep, archer posts or storage, or touches a building a faction holds ({@link Holds}:
 * a village seat or storehouse, a Grim structure; their own builds are already kept apart as taken ground). Player builds outside plots stay safe because a block outside every piece must still be the land's own.
 */
final class Ruins {
	/** How near (blocks) a site on old structures may come to a seat (bell or fire), a camp's fire, a keep's heart, an archer's post, a storage block. */
	static final int SEAT_GUARD = 10, HOLD_GUARD = 2, CAMP_GUARD = 26, KEEP_GUARD = 22, POST_GUARD = 5, STORE_GUARD = 10;
	/** How far down through a structure the ground under it is looked for. */
	static final int GROUND_SCAN = 24;
	/** The most blocks above the floor torn out of a structure's pieces in a building's columns (no floating remains). */
	static final int TEAR_HEIGHT = 48;
	/** How long (ticks) a chunk's structure pieces are remembered. */
	static final long CACHE_TICKS = 200L;

	private record Cached(List<BoundingBox> boxes, long at) {
	}

	private static final Map<Long, Cached> CACHE = new HashMap<>();

	private Ruins() {
	}

	static void clear() {
		CACHE.clear();
	}

	/** The pieces of world-generated structures that reach into chunk ({@code cx}, {@code cz}) (empty if it isn't loaded). */
	static List<BoundingBox> pieces(ServerLevel level, int cx, int cz) {
		long key = ChunkPos.pack(cx, cz);
		long now = level.getGameTime();
		Cached c = CACHE.get(key);
		if (c != null && now - c.at() < CACHE_TICKS) {
			return c.boxes();
		}
		List<BoundingBox> out = new ArrayList<>();
		if (level.hasChunk(cx, cz)) {
			ChunkAccess chunk = level.getChunk(cx, cz);
			BoundingBox area = new BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
			for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
				for (long ref : e.getValue()) {
					ChunkPos from = ChunkPos.unpack(ref);
					if (!level.hasChunk(from.x(), from.z())) {
						continue;
					}
					StructureStart start = level.getChunk(from.x(), from.z()).getStartForStructure(e.getKey());
					if (start == null || !start.isValid()) {
						continue;
					}
					for (StructurePiece piece : start.getPieces()) {
						if (piece.getBoundingBox().intersects(area)) {
							out.add(piece.getBoundingBox());
						}
					}
				}
			}
		}
		if (CACHE.size() > 4096) {
			CACHE.clear();
		}
		CACHE.put(key, new Cached(List.copyOf(out), now));
		return out;
	}

	/** {@code at} lies inside a world-generated structure's piece. */
	static boolean inPiece(ServerLevel level, BlockPos at) {
		for (BoundingBox box : pieces(level, SectionPos.blockToSectionCoord(at.getX()), SectionPos.blockToSectionCoord(at.getZ()))) {
			if (box.isInside(at)) {
				return true;
			}
		}
		return false;
	}

	/** The highest a structure's piece reaches over column ({@code x}, {@code z}), or {@code Integer.MIN_VALUE} for none. */
	static int pieceTop(ServerLevel level, int x, int z) {
		int top = Integer.MIN_VALUE;
		for (BoundingBox box : pieces(level, SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
			if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
				top = Math.max(top, box.maxY());
			}
		}
		return top;
	}

	/** A block the builders may tear out of an old structure: inside a piece, breakable, no bell, not a living villager's. */
	static boolean tearable(ServerLevel level, BlockPos at, BlockState state) {
		if (state.isAir() || state.is(Blocks.BELL) || state.getDestroySpeed(level, at) < 0 || !inPiece(level, at)
			|| Holds.guarded(level.getServer(), at)) {
			return false;
		}
		return level.getPoiManager().getType(at).isEmpty()
			|| level.getPoiManager().getInSquare(type -> true, at, 0, PoiManager.Occupancy.IS_OCCUPIED).noneMatch(r -> r.getPos().equals(at));
	}

	/**
	 * A site on old structures ({@code x0..x1}, {@code z0..z1}, its height {@code y0..y1}) that mustn't be cleared: a
	 * living villager's bed or job block (or a bell anyone gathers at) in it, or a faction's seat, keep, posts or storage
	 * near it. (A bell in its way is caught by {@link Planner#check} as it looks over the site.)
	 */
	static boolean guarded(ServerLevel level, int x0, int z0, int x1, int z1, int y0, int y1) {
		BlockPos mid = new BlockPos((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
		int half = Math.max(x1 - x0, z1 - z0) / 2 + 1;
		boolean claimed = level.getPoiManager().getInSquare(type -> true, mid, half, PoiManager.Occupancy.IS_OCCUPIED).anyMatch(r -> {
			BlockPos p = r.getPos();
			return p.getX() >= x0 && p.getX() <= x1 && p.getZ() >= z0 && p.getZ() <= z1 && p.getY() >= y0 - 4 && p.getY() <= y1;
		});
		if (claimed) {
			return true;
		}
		MinecraftServer server = level.getServer();
		// a building a faction took for its seat or storehouse, or the Grim structure it holds
		if (Holds.guarded(server, x0, z0, x1, z1, HOLD_GUARD)) {
			return true;
		}
		for (Faction f : Factions.all(server)) {
			if (near(f.seat(), f.type() == FactionType.BARBARIAN ? CAMP_GUARD : SEAT_GUARD, x0, z0, x1, z1)) {
				return true;
			}
			for (BlockPos s : f.holdings().storage()) {
				if (near(s, STORE_GUARD, x0, z0, x1, z1)) {
					return true;
				}
			}
			if (f.holdings().depot().isPresent() && near(f.holdings().depot().get(), STORE_GUARD, x0, z0, x1, z1)) {
				return true;
			}
			Castle.Keep keep = Factions.keep(server, f.id());
			if (keep != null) {
				for (BlockPos p : List.of(keep.throne(), keep.cellar(), keep.gate(), keep.hall())) {
					if (near(p, KEEP_GUARD, x0, z0, x1, z1)) {
						return true;
					}
				}
				for (BlockPos p : keep.posts()) {
					if (near(p, POST_GUARD, x0, z0, x1, z1)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** {@code p} is within {@code r} (across) of the rectangle. */
	private static boolean near(BlockPos p, int r, int x0, int z0, int x1, int z1) {
		return p.getX() >= x0 - r && p.getX() <= x1 + r && p.getZ() >= z0 - r && p.getZ() <= z1 + r;
	}
}
