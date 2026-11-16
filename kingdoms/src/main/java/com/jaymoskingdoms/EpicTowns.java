package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
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
public final class EpicTowns {
	/** A town with more than this share of its pieces over water is passed over (a shore seat's half in the sea). */
	static final double WET_MOST = 0.25;
	/** Its bell is looked for this far round the town's middle (blocks). */
	static final int BELL_SEARCH = 96;

	/** The five towns' ids (vanilla's villages, which Epic Structures redraws). */
	static final List<String> TOWNS = List.of("minecraft:village_plains", "minecraft:village_desert", "minecraft:village_savanna",
		"minecraft:village_snowy", "minecraft:village_taiga");
	/**
	 * Each Epic town's weight in the shared village pool ({@link SharedPools}); every other mod's village there weighs 1
	 * to 5 (Dungeons and Taverns' dappled forest village 19). At a spot where three or four of theirs also suit the land,
	 * an Epic town is still drawn about two times in three.
	 */
	static final int POOL_WEIGHT = 30;

	/**
	 * The land each town may stand on, wider than its biome tag (Andy: "can you make epic towns spawn the most often?").
	 * Epic towns only had vanilla's five village biomes (plains, desert, savanna, snowy plains, taiga, which Towns and
	 * Towers' tags hold to those and a few like them), so on most of the pack's land no Epic town could stand. Now every
	 * dry overworld biome takes the town of its land ({@link #villageFor}): forests, meadows, jungles and swamps the plains
	 * town, badlands the desert's, snowy groves the snowy one. Sea, rivers, beaches, bare peaks and slopes, mushroom
	 * islands and caves stay without. Read by {@code StructureBiomesMixin}.
	 */
	private static volatile Map<Structure, HolderSet<Biome>> lands = Map.of();

	private EpicTowns() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> widen(server.registryAccess()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> lands = Map.of());
	}

	/** The land {@code structure} may stand on, if it is an Epic town; null for any other structure. */
	public static @Nullable HolderSet<Biome> lands(Structure structure) {
		return lands.get(structure);
	}

	private static void widen(RegistryAccess access) {
		lands = Map.of();
		Registry<Structure> structures = access.lookupOrThrow(Registries.STRUCTURE);
		Registry<Biome> biomes = access.lookupOrThrow(Registries.BIOME);
		Map<Structure, HolderSet<Biome>> out = new IdentityHashMap<>();
		for (String id : TOWNS) {
			Optional<Holder.Reference<Structure>> town = structures.get(Identifier.parse(id));
			if (town.isEmpty()) {
				continue;
			}
			Structure structure = town.get().value();
			List<Holder<Biome>> land = new ArrayList<>(structure.biomes().stream().toList());
			int own = land.size();
			for (Holder<Biome> biome : biomes.getTagOrEmpty(BiomeTags.IS_OVERWORLD)) {
				if (dryLand(biome) && villageFor(Palette.of(biome)).equals(id) && !land.contains(biome)) {
					land.add(biome);
				}
			}
			out.put(structure, HolderSet.direct(land));
			JaymosKingdoms.LOGGER.info("Epic town {}: {} biomes (its tag had {})", id, land.size(), own);
		}
		lands = out;
	}

	/** Dry land a town can stand on: no sea, river, beach, bare peak, mushroom island or cave. */
	static boolean dryLand(Holder<Biome> biome) {
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
			return false;
		}
		String name = biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
		for (String not : List.of("ocean", "river", "beach", "shore", "peaks", "slopes", "mushroom", "cave", "deep_dark", "void", "underground")) {
			if (name.contains(not)) {
				return false;
			}
		}
		return true;
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
