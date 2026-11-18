package com.jaymoskingdoms;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
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
 * on water, or won't generate, is passed over, and the nearest town world generation made further out is looked for instead ({@link #foundFar}): never a lone castle.
 */
public final class EpicTowns {
	/** A town with more than this share of its pieces over water is passed over (a shore seat's half in the sea). */
	static final double WET_MOST = 0.25;
	/** Its bell is looked for this far round the town's middle (blocks). */
	static final int BELL_SEARCH = 96;
	/**
	 * How far (chunks) round the spot asked for a town's start is looked for, nearest first. One chunk alone was nearly
	 * always thrown out by the river and sea checks every village passes (Dynamic Waters' clearance, the raised sea), so no
	 * kingdom's town rose on the Overworld ground and each fell back to a lone castle.
	 */
	static final int START_SEARCH = 4;
	/** The most starts that pass those checks to look at the ground under (each loads its chunks) before giving up. */
	static final int GROUND_LOOKS = 3;
	/** The most time (ms) the search may take on the server thread: each try lays a whole town out and has its rivers checked. */
	static final long SEARCH_MS = 2000;
	/**
	 * Only an Epic town is crowned (Andy, 2026-11-18): where none can be raised at the spot, the nearest one world
	 * generation starts is looked for further out, {@link #FAR_RINGS} village spacings round it (a spacing is 34 chunks),
	 * worked out without making a chunk ({@link Scout}).
	 */
	static final int FAR_RINGS = 6;
	/** A far town's land is made in the background, up to this many chunks round its start, for at most this long (ticks) before it is passed over. */
	static final int FAR_LAND = 10, FAR_WAIT_TICKS = 1200;
	/** Keeps a far town's chunks loaded (made, not run) while it waits; lapses on its own if the search is dropped. */
	private static final TicketType FAR = new TicketType(300L, TicketType.FLAG_LOADING);
	/** The start chunks (packed) found to hold no town, or no use, so not looked at again; and the spots a town was already tried to be raised at. */
	private static final Set<Long> PASSED = new HashSet<>(), RAISE_FAILED = new HashSet<>();
	/** The far town being waited on: where, which, and when it was chosen (null: none). */
	private static @Nullable Far far;

	private record Far(ChunkPos chunk, long since) {
	}

	/** How often (ticks) and how long (ticks) a background {@link #seek} tries. */
	static final int SEEK_EVERY = 20, SEEK_TICKS = 12000;
	/** Where a kingdom is being sought for a castle town with none to swear to, and until when (game time); null: nowhere. */
	private static @Nullable BlockPos seeking;
	private static long seekUntil;

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
		ServerTickEvents.END_SERVER_TICK.register(EpicTowns::seekTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			lands = Map.of();
			seeking = null;
			far = null;
			PASSED.clear();
			RAISE_FAILED.clear();
		});
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

	/**
	 * A castle town with no kingdom near to swear to (a seat compound, an Epic town with no building fit for a king): a
	 * kingdom is looked for in the nearest Epic town further out, in the background, until one is founded or none is left.
	 */
	static void seek(ServerLevel level, BlockPos from) {
		if (seeking == null) {
			seeking = from;
			seekUntil = level.getGameTime() + SEEK_TICKS;
		}
	}

	private static void seekTick(net.minecraft.server.MinecraftServer server) {
		BlockPos from = seeking;
		if (from == null || server.getTickCount() % SEEK_EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() > seekUntil || foundFar(level, from) != null || !farLeft(level, from)) {
			seeking = null;
		}
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
		ChunkPos centre = ChunkPos.containing(at);
		// (a spot already tried is not laid out again: a job waiting on a kingdom asks each second)
		if (RAISE_FAILED.contains(centre.pack())) {
			return null;
		}
		List<ChunkPos> around = new ArrayList<>();
		for (int dx = -START_SEARCH; dx <= START_SEARCH; dx++) {
			for (int dz = -START_SEARCH; dz <= START_SEARCH; dz++) {
				around.add(new ChunkPos(centre.x() + dx, centre.z() + dz));
			}
		}
		around.sort(java.util.Comparator.comparingInt(c -> (c.x() - centre.x()) * (c.x() - centre.x()) + (c.z() - centre.z()) * (c.z() - centre.z())));
		StructureStart start = null;
		ChunkPos origin = null;
		int looks = 0;
		long until = System.nanoTime() + SEARCH_MS * 1_000_000L;
		for (ChunkPos c : around) {
			if (System.nanoTime() > until) {
				break;
			}
			StructureStart tried = GrandSeats.generate(level, holder.get(), c);
			if (tried == null) {
				continue;
			}
			if (wet(level, tried)) {
				if (++looks >= GROUND_LOOKS) {
					break;
				}
				continue;
			}
			start = tried;
			origin = c;
			break;
		}
		if (start == null) {
			JaymosKingdoms.LOGGER.info("No {} would generate by {} ({} spots in water)", id, at, looks);
			RAISE_FAILED.add(centre.pack());
			return null;
		}
		RAISE_FAILED.add(centre.pack());
		place(level, holder.get().value(), start, origin);
		return crown(level, start, id);
	}

	/** A new kingdom in {@code start}, a town made in its chunks, round its bell; null if no building there will do. */
	private static @Nullable Faction crown(ServerLevel level, StructureStart start, String id) {
		BoundingBox box = start.getBoundingBox();
		BlockPos middle = box.getCenter();
		PoiManager poi = level.getPoiManager();
		poi.ensureLoadedAndValid(level, middle, BELL_SEARCH);
		Optional<BlockPos> bell = poi.findClosest(h -> h.is(PoiTypes.MEETING), middle, BELL_SEARCH, PoiManager.Occupancy.ANY);
		BlockPos seat = bell.orElse(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle));
		JaymosKingdoms.LOGGER.info("Raising a kingdom in the {} at {} ({} by {})", id, seat, box.getXSpan(), box.getZSpan());
		return KingdomEvents.foundIn(level, seat, start);
	}

	/** The Epic towns' structures. */
	private static HolderSet<Structure> towns(ServerLevel level) {
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Holder<Structure>> out = new ArrayList<>();
		for (String id : TOWNS) {
			structures.get(Identifier.parse(id)).ifPresent(out::add);
		}
		return HolderSet.direct(out);
	}

	/** The nearest Epic town's start world generation makes beyond the ones passed over, within {@link #FAR_RINGS} spacings of {@code from}. */
	private static @Nullable Pair<BlockPos, Holder<Structure>> nextFar(ServerLevel level, BlockPos from) {
		return Scout.find(level, towns(level), from, FAR_RINGS, chunk -> PASSED.contains(chunk.pack()));
	}

	/** Whether a far town is being waited on or another is still to look at ({@link #foundFar}). */
	static boolean farLeft(ServerLevel level, BlockPos from) {
		return far != null || nextFar(level, from) != null;
	}

	/**
	 * A new kingdom in the nearest Epic town that is not too near another faction, further out than the spot asked for: the
	 * town world generation made there (never one laid over it). Its land is made in the background, a try at a time
	 * ({@link #FAR_LAND}), so this never holds the server: null (try again later) until it stands ready, and then also
	 * null if it holds no building fit for a king, when the next is looked at. {@link #farLeft} says if any is left.
	 */
	static @Nullable Faction foundFar(ServerLevel level, BlockPos from) {
		long now = level.getGameTime();
		if (far == null) {
			Pair<BlockPos, Holder<Structure>> next = nextFar(level, from);
			if (next == null) {
				JaymosKingdoms.LOGGER.info("No Epic town left to look at within {} spacings of {}", FAR_RINGS, from);
				return null;
			}
			far = new Far(ChunkPos.containing(next.getFirst()), now);
			JaymosKingdoms.LOGGER.info("Looking for a kingdom in the Epic town at {} (from {})", next.getFirst(), from);
		}
		Far looking = far;
		if (now - looking.since() > FAR_WAIT_TICKS) {
			return pass(looking, "its land was not made in time");
		}
		level.getChunkSource().addTicketWithRadius(FAR, looking.chunk(), FAR_LAND);
		LevelChunk chunk = level.getChunkSource().getChunkNow(looking.chunk().x(), looking.chunk().z());
		if (chunk == null) {
			return null;
		}
		HolderSet<Structure> towns = towns(level);
		StructureStart start = null;
		String id = "";
		for (Holder<Structure> town : towns) {
			StructureStart there = level.structureManager().getStartForStructure(town.value(), chunk);
			if (there != null && there.isValid()) {
				start = there;
				id = town.unwrapKey().map(k -> k.identifier().toString()).orElse("");
				break;
			}
		}
		if (start == null) {
			return pass(looking, "no town started there");
		}
		BoundingBox box = start.getBoundingBox();
		BlockPos middle = box.getCenter();
		if (Factions.realmNear(level.getServer(), middle, KingdomEvents.CAPITAL_SPACING) != null) {
			return pass(looking, "too near another kingdom's capital");
		}
		if (!Loaded.made(level, box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
			return null;
		}
		if (Factions.near(level.getServer(), middle, KingdomEvents.TOWN_RADIUS) != null) {
			return pass(looking, "another faction holds it");
		}
		Faction kingdom = crown(level, start, id);
		return kingdom != null ? done(kingdom) : pass(looking, "no building in it is fit for a king");
	}

	/** The far town being waited on is founded in: the search is over. */
	private static Faction done(Faction kingdom) {
		if (far != null) {
			PASSED.add(far.chunk().pack());
		}
		far = null;
		return kingdom;
	}

	/** The far town is given up on, for {@code why}; the next is looked at on the next try. */
	private static @Nullable Faction pass(Far looking, String why) {
		JaymosKingdoms.LOGGER.info("The Epic town at chunk {} is passed over: {}", looking.chunk(), why);
		PASSED.add(looking.chunk().pack());
		far = null;
		return null;
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
