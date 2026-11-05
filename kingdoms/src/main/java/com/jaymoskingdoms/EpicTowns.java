package com.jaymoskingdoms;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

/**
 * Epic towns for kingdoms raised from nothing (0.7.598). Andy, of a Grim Kingdoms citadel standing as a kingdom's seat
 * on open snow: "these structures should only spawn as fiefs or smaller villages allied to larger kingdoms that are
 * always from epic villages". So a kingdom the story start (or a whaler's shore, or a trader's second town) has to
 * make where no village stands no longer raises a lone castle: a village of the land's kind (vanilla's id, which Epic
 * Structures: Villages redraws into its towns; nothing of theirs is copied) is generated there the way
 * {@code /place structure} does, entered in its chunks as world generation would (so everything that reads villages
 * finds it), and the kingdom rises in it as in any town ({@link KingdomEvents#foundIn}). A town that would stand mostly
 * on water, or won't generate, is passed over for the castle as before ({@link GrandSeats}).
 */
final class EpicTowns {
	/** A town with more than this share of its pieces over water is passed over (a shore seat's half in the sea). */
	static final double WET_MOST = 0.25;
	/** Its bell is looked for this far round the town's middle (blocks). */
	static final int BELL_SEARCH = 96;

	private EpicTowns() {
	}

	/** The village id for {@code land}: vanilla's five, which Epic Structures redraws. */
	static String villageFor(Palette land) {
		if (land == Palette.DESERT || land == Palette.BADLANDS) {
			return "minecraft:village_desert";
		}
		if (land == Palette.SAVANNA) {
			return "minecraft:village_savanna";
		}
		if (land == Palette.SNOWY) {
			return "minecraft:village_snowy";
		}
		if (land == Palette.TAIGA) {
			return "minecraft:village_taiga";
		}
		return "minecraft:village_plains";
	}

	/** A new kingdom in a town raised at {@code at}; null (nothing raised) if no town would do there. */
	static @Nullable Faction found(ServerLevel level, BlockPos at) {
		String id = villageFor(Palette.at(level, at));
		Optional<Holder.Reference<Structure>> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Identifier.parse(id));
		if (holder.isEmpty()) {
			return null;
		}
		ChunkPos origin = ChunkPos.containing(at);
		StructureStart start = GrandSeats.generate(level, holder.get(), origin);
		if (start == null) {
			JaymosKingdoms.LOGGER.info("No {} would generate by {}", id, at);
			return null;
		}
		if (wet(level, start)) {
			JaymosKingdoms.LOGGER.info("The {} by {} would stand in water", id, at);
			return null;
		}
		place(level, holder.get().value(), start, origin);
		BoundingBox box = start.getBoundingBox();
		BlockPos middle = box.getCenter();
		PoiManager poi = level.getPoiManager();
		poi.ensureLoadedAndValid(level, middle, BELL_SEARCH);
		Optional<BlockPos> bell = poi.findClosest(h -> h.is(PoiTypes.MEETING), middle, BELL_SEARCH, PoiManager.Occupancy.ANY);
		BlockPos seat = bell.orElse(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle));
		JaymosKingdoms.LOGGER.info("Raised a {} at {} ({} by {}) for a new kingdom", id, seat, box.getXSpan(), box.getZSpan());
		return KingdomEvents.foundIn(level, seat, start);
	}

	/** Whether more than {@link #WET_MOST} of its pieces stand over water. */
	private static boolean wet(ServerLevel level, StructureStart start) {
		int all = 0, wet = 0;
		for (StructurePiece piece : start.getPieces()) {
			BlockPos c = piece.getBoundingBox().getCenter();
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, c);
			all++;
			if (!level.getFluidState(top.below()).isEmpty()) {
				wet++;
			}
		}
		return all == 0 || wet > all * WET_MOST;
	}

	/** Places it chunk by chunk as world generation would, and enters it (its start and references) in its chunks. */
	private static void place(ServerLevel level, Structure structure, StructureStart start, ChunkPos origin) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomSource random = level.getRandom();
		BoundingBox box = start.getBoundingBox();
		long from = origin.pack();
		for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
				ChunkPos chunk = new ChunkPos(cx, cz);
				ChunkAccess access = level.getChunk(cx, cz);
				start.placeInChunk(level, level.structureManager(), generator, random,
					new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ()),
					chunk);
				if (cx == origin.x() && cz == origin.z()) {
					access.setStartForStructure(structure, start);
				}
				access.addReferenceForStructure(structure, from);
				access.markUnsaved();
			}
		}
	}
}
