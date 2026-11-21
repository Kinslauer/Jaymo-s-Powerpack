package com.jaymospowers.core;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * Which pieces of one structure start cover a block, answered from the pieces in that block's chunk column instead of
 * all of them. Vanilla's {@code structureHasPieceAt} walks every piece, and a natural spawn attempt asks it for each
 * structure whose spawn override is per piece; a town with hundreds of pieces made that 150 samples of one 22 s freeze
 * on the Steam Deck (2026-11-18). The answers are the same as the walk's. Built once per start, from the pieces as they
 * are when it is first asked (a start's pieces don't move after it is made).
 */
public final class PieceIndex {
	/** Starts with fewer pieces than this are walked the vanilla way; an index isn't worth building for them. */
	public static final int MIN_PIECES = 16;

	/** A structure start that keeps its index (put on {@code StructureStart} by a mixin). */
	public interface Holder {
		PieceIndex jaymospowers$pieceIndex();
	}

	private final Long2ObjectOpenHashMap<BoundingBox[]> columns = new Long2ObjectOpenHashMap<>();

	public PieceIndex(List<StructurePiece> pieces) {
		Long2ObjectOpenHashMap<List<BoundingBox>> building = new Long2ObjectOpenHashMap<>();
		for (StructurePiece piece : pieces) {
			BoundingBox box = piece.getBoundingBox();
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
				for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					building.computeIfAbsent(ChunkPos.pack(cx, cz), key -> new ArrayList<>()).add(box);
				}
			}
		}
		building.long2ObjectEntrySet().forEach(entry -> this.columns.put(entry.getLongKey(), entry.getValue().toArray(new BoundingBox[0])));
	}

	/** Some piece's bounding box has {@code x, y, z} inside it. */
	public boolean has(int x, int y, int z) {
		BoundingBox[] boxes = this.columns.get(ChunkPos.pack(x >> 4, z >> 4));
		if (boxes != null) {
			for (BoundingBox box : boxes) {
				if (box.isInside(x, y, z)) {
					return true;
				}
			}
		}
		return false;
	}
}
