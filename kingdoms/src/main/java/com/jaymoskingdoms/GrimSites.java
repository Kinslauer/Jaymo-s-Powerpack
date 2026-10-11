package com.jaymoskingdoms;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Fiefdoms and barbarian camps in Grim Kingdoms' ruins (0.5; only with Grim Kingdoms: Vanilla Edition loaded, all of it
 * read by structure id: nothing of Grim's is copied). Every so often the Grim structures in the chunks round each player
 * are looked over; one within {@link #RANGE} of a standing kingdom's seat, on nobody's land and out of everyone's
 * sight, is taken: a big hold (a keep, citadel, fortress...) becomes a {@link FactionType#FIEFDOM} of that kingdom (a
 * lord on a throne in its largest building, guards, archers), a small or wild one (a camp, an outpost, a watch tower, a
 * ruin) a barbarian camp (its chief and raiders seated there instead of in tents). A kingdom gets at most
 * {@link #FIEFS_PER_KINGDOM} and {@link #CAMPS_PER_KINGDOM} of them. Each takes one of the structure's buildings for its
 * storehouse ({@link Storehouse#adopt}) and gets a builder crew, who first clear the ruin's rubble ({@link Debris}) and
 * then grow it like any other settlement; the structure itself is never torn down ({@link Holds}). After
 * {@link #ERECT_AFTER_TICKS}, the builders raise one more Grim building nearby, block by block like any project (its
 * template read into a {@link Blueprint}).
 */
public final class GrimSites {
	public static final String MOD_ID = "mr_grim_kingdomsloststructuresruinsvanillaedition";
	static final String NAMESPACE = "grim_kingdoms";
	/** How often the chunks round players are looked over (ticks), and how far round (chunks). */
	static final int SCAN_TICKS = 600, SCAN_CHUNKS = 5;
	/** A Grim structure this near a standing kingdom's seat may be taken; never this near any settlement's seat. */
	static final double RANGE = 600.0, SETTLEMENT_CLEARANCE = 96.0;
	/** Per kingdom: fiefdoms and camps in Grim structures at most. */
	static final int FIEFS_PER_KINGDOM = 2, CAMPS_PER_KINGDOM = 2;
	/** Nobody this near the structure's middle when it's taken (they'd see the folk appear). */
	static final double HIDE_RANGE = 40.0;
	/** Land claimed round the seat (chunks out each way): a fiefdom's, a camp's. */
	static final int FIEF_CLAIM = 4, CAMP_CLAIM = 2;
	/** A fiefdom's guards on foot (an archer on every post besides). */
	static final int FIEF_GUARDS = 5;
	/** A structure passed over (nobody near enough, the kingdom full...) is looked at again after this long (ticks). */
	static final long RETRY_TICKS = 6000L;
	/** The second building: begun this long after the hold was taken, looked for this often, again this long after a miss. */
	static final long ERECT_AFTER_TICKS = 3 * 24000L, ERECT_CHECK_TICKS = 1200L, ERECT_RETRY_TICKS = 24000L;
	/** The biggest Grim template its builders will raise: blocks across, blocks in all. */
	static final int ERECT_MAX_SIDE = 26, ERECT_MAX_BLOCKS = 5000;

	/** Big holds: a fiefdom. */
	static final Set<String> HOLDS = Set.of("glimmervault_fortress", "glimmervault_fortress_plains", "silentgale_citadel", "silentgale_citadel_plains",
		"snowgrave_citadel", "winterhold_citadel", "winterhold_citadel_taiga", "shardspire_castle", "whitewarden_keep", "glacierfall_keep",
		"glacierfall_keep_plains", "grain_sack_keep", "grain_sack_fort", "grain_sack_hold", "frostbloom_bastion", "frozen_onion_fort",
		"frozen_onion_fort_taiga", "zweiberg", "zweiberg_plains", "zweiberg_taiga", "eldermoor", "chill_hallmanor", "weathered_colosseum");
	/** Small and wild ones: a barbarian camp. */
	static final Set<String> CAMPS = Set.of("camp1", "camp2", "camp3", "sandfell_outpost", "stonyfell_outpost", "stonyfell_outpost_plains",
		"thatch_outpost", "blackridge_watch", "blackridge_watch_taiga", "old_timber_watch", "river_bend_watch", "tor_tower", "east_spur_tower",
		"west_spur_tower", "north_spur_tower", "south_spur_tower", "high_spur_tower", "meadow_ruin", "meadow_flower_ruin", "bramblepath",
		"travelers_lodge");
	/** What each raises as its second building (tried in a random order; the first with a site is begun). */
	static final List<String> ERECT_FIEF = List.of("grain_sack_hold", "thatch_outpost", "travelers_lodge", "zweiberg_plains", "zweiberg_taiga");
	static final List<String> ERECT_CAMP = List.of("camp1", "camp2", "camp3", "old_timber_watch", "thatch_outpost");

	/** A Grim structure seen in a scan: its id's path, middle, and whether it's a hold (else a camp site). */
	private record Seen(String path, BlockPos center, boolean hold) {
	}

	/** Structures seen (start chunk, packed → it). */
	private static final Map<Long, Seen> SEEN = new HashMap<>();
	/** Structures passed over (start chunk, packed → game time to look again; Long.MAX_VALUE: never). */
	private static final Map<Long, Long> PASSED = new HashMap<>();
	/** Faction id → game time it may look for a site for its second building again. */
	private static final Map<Integer, Long> NEXT_ERECT = new HashMap<>();
	/** Grim templates read into blueprints (by structure path; null: none fit). */
	private static final Map<String, Optional<Blueprint>> BLUEPRINTS = new HashMap<>();

	private static Boolean loaded;

	private GrimSites() {
	}

	/** Grim Kingdoms is in the game. */
	public static boolean loaded() {
		if (loaded == null) {
			loaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
		}
		return loaded;
	}

	/** The rubble-clearing builders always; the rest only with Grim Kingdoms loaded (call after {@link Construction#register}). */
	static void register() {
		Debris.register();
		if (!loaded()) {
			return;
		}
		JaymosKingdoms.LOGGER.info("Grim Kingdoms found: fiefdoms and barbarian camps take its ruins near kingdoms");
		// the second buildings, in the catalogue but never chosen by the planner on its own (at most 0, from no size)
		for (String path : ERECT_FIEF) {
			Blueprint.register(FactionType.FIEFDOM, new Blueprint.Entry(entry(path), (level, f) -> blueprint(level, path), 0, Integer.MAX_VALUE));
		}
		for (String path : ERECT_CAMP) {
			Blueprint.register(FactionType.BARBARIAN, new Blueprint.Entry(entry(path), (level, f) -> blueprint(level, path), 0, Integer.MAX_VALUE));
		}
		ServerTickEvents.END_SERVER_TICK.register(GrimSites::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SEEN.clear();
			PASSED.clear();
			NEXT_ERECT.clear();
			BLUEPRINTS.clear();
		});
	}

	private static String entry(String path) {
		return "grim_" + path;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % SCAN_TICKS == 0) {
			scan(level, now);
		}
		if (now % ERECT_CHECK_TICKS == 0) {
			erect(level, now);
		}
	}

	// ---- finding sites -----------------------------------------------------------------------------------------------

	/** Looks over the Grim structures in the loaded chunks round every player in the Overworld. */
	private static void scan(ServerLevel level, long now) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			ChunkPos middle = player.chunkPosition();
			for (int dx = -SCAN_CHUNKS; dx <= SCAN_CHUNKS; dx++) {
				for (int dz = -SCAN_CHUNKS; dz <= SCAN_CHUNKS; dz++) {
					int cx = middle.x() + dx, cz = middle.z() + dz;
					if (!level.hasChunk(cx, cz)) {
						continue;
					}
					ChunkAccess chunk = level.getChunk(cx, cz);
					for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
						Identifier id = registry.getKey(e.getKey());
						if (id == null || !id.getNamespace().equals(NAMESPACE) || !(HOLDS.contains(id.getPath()) || CAMPS.contains(id.getPath()))) {
							continue;
						}
						for (long ref : e.getValue()) {
							if (PASSED.getOrDefault(ref, Long.MIN_VALUE) > now) {
								continue;
							}
							ChunkPos from = ChunkPos.unpack(ref);
							if (!level.hasChunk(from.x(), from.z())) {
								continue;
							}
							StructureStart start = level.getChunk(from.x(), from.z()).getStartForStructure(e.getKey());
							if (start != null && start.isValid()) {
								consider(level, start, ref, id.getPath(), now);
							}
						}
					}
				}
			}
		}
	}

	/** One Grim structure seen: taken by a fiefdom or a camp if it may be, else passed over for a while (or for good). */
	private static void consider(ServerLevel level, StructureStart start, long key, String path, long now) {
		MinecraftServer server = level.getServer();
		boolean hold = HOLDS.contains(path);
		BoundingBox area = start.getBoundingBox();
		BlockPos center = area.getCenter();
		SEEN.put(key, new Seen(path, center, hold));
		// already someone's
		for (Holds.Hold h : Holds.all(server).values()) {
			for (BoundingBox box : h.guarded()) {
				if (h.isGrim() && box.intersects(area)) {
					PASSED.put(key, Long.MAX_VALUE);
					return;
				}
			}
		}
		// nobody's land, no settlement near
		if (Factions.at(server, level, center) != null || Factions.at(server, level, new BlockPos(area.minX(), 0, area.minZ())) != null
			|| Factions.at(server, level, new BlockPos(area.maxX(), 0, area.maxZ())) != null) {
			PASSED.put(key, now + RETRY_TICKS);
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && Raids.horizontal(f.seat(), center) < SETTLEMENT_CLEARANCE * SETTLEMENT_CLEARANCE) {
				PASSED.put(key, now + RETRY_TICKS);
				return;
			}
		}
		// the nearest standing kingdom in reach, with room for another
		Faction kingdom = null;
		double best = RANGE * RANGE;
		for (Faction f : Factions.all(server, FactionType.KINGDOM)) {
			double d = Raids.horizontal(f.seat(), center);
			if (!f.fallen() && d <= best) {
				kingdom = f;
				best = d;
			}
		}
		if (kingdom == null || count(server, kingdom, hold) >= (hold ? FIEFS_PER_KINGDOM : CAMPS_PER_KINGDOM)) {
			PASSED.put(key, now + RETRY_TICKS);
			return;
		}
		// out of sight, and all of it loaded (so its buildings can be read)
		if (Raids.playerNear(level, center, HIDE_RANGE) || !level.hasChunksAt(area.minX(), area.minZ(), area.maxX(), area.maxZ())) {
			return;
		}
		Faction taken = hold ? foundFief(level, start, path, kingdom) : foundCamp(level, start, path, kingdom);
		PASSED.put(key, taken != null ? Long.MAX_VALUE : now + RETRY_TICKS * 4);
	}

	/** {@code kingdom}'s standing fiefdoms ({@code hold}) or the standing camps near it, in Grim structures. */
	private static int count(MinecraftServer server, Faction kingdom, boolean hold) {
		int n = 0;
		for (var e : Holds.all(server).entrySet()) {
			Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
			if (f == null || f.fallen() || !e.getValue().isGrim()) {
				continue;
			}
			if (hold ? f.type() == FactionType.FIEFDOM && f.overlord() == kingdom.id()
				: f.type() == FactionType.BARBARIAN && Raids.horizontal(f.seat(), kingdom.seat()) <= RANGE * RANGE) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Whether wild barbarian camps should wait for {@code realm}: a Grim camp site has been seen in its reach that nobody
	 * holds yet (with Grim loaded, camps prefer its ruins to tents in the wild).
	 */
	static boolean prefersGrim(MinecraftServer server, Faction realm) {
		if (!loaded() || count(server, realm, false) >= CAMPS_PER_KINGDOM) {
			return false;
		}
		for (var e : SEEN.entrySet()) {
			Seen s = e.getValue();
			if (!s.hold() && PASSED.getOrDefault(e.getKey(), 0L) != Long.MAX_VALUE && Raids.horizontal(s.center(), realm.seat()) <= RANGE * RANGE) {
				return true;
			}
		}
		return false;
	}

	/** The structure's pieces worth clearing (no bare terrain or street pieces). */
	private static List<BoundingBox> rubble(StructureStart start) {
		List<BoundingBox> out = new ArrayList<>();
		for (var piece : start.getPieces()) {
			String name = Seats.name(piece);
			if (!name.contains("/terrain") && !name.contains("/streets/")) {
				out.add(piece.getBoundingBox());
			}
		}
		return out;
	}

	/** Marks the structure as {@code faction}'s: never torn down, its rubble to clear. */
	private static void hold(MinecraftServer server, Faction faction, StructureStart start, String path) {
		Holds.update(server, faction.id(), h -> {
			Holds.Hold held = h.withGrim(NAMESPACE + ":" + path, rubble(start));
			for (BoundingBox box : Seats.boxes(start)) {
				held = held.withGuarded(box);
			}
			return held;
		});
	}

	/** Its storehouse: another of its buildings, else its seat's own (away from the throne), else a shed on open ground. */
	private static void storehouse(ServerLevel level, Faction faction, Seats.@Nullable Pick pick, Castle.Keep keep) {
		Faction now = Factions.get(level.getServer(), faction.id());
		BlockPos depot = null;
		if (pick != null && pick.store() != null) {
			depot = Storehouse.adopt(level, now, pick.store(), null);
		}
		if (depot == null && pick != null) {
			depot = Storehouse.adopt(level, now, pick.seat(), keep.throne());
		}
		if (depot == null) {
			depot = Storehouse.build(level, now);
		}
		Faction after = Factions.get(level.getServer(), faction.id());
		if (depot == null && after != null && Builders.crewCount(after) == 0) {
			Builders.spawnCrew(level, after);
		}
	}

	/** A fiefdom of {@code kingdom} in a Grim hold: its lord on a throne in its largest building, guards, archers, a storehouse. */
	static @Nullable Faction foundFief(ServerLevel level, StructureStart start, String path, Faction kingdom) {
		MinecraftServer server = level.getServer();
		Seats.Pick pick = Seats.pick(level, Seats.buildings(start), kingdom.seat(), false);
		if (pick == null) {
			JaymosKingdoms.LOGGER.info("The Grim {} at {} has no building fit for a seat", path, start.getBoundingBox().getCenter());
			return null;
		}
		RandomSource random = level.getRandom();
		BlockPos seat = pick.seat().middle();
		Faction fief = Faction.create(Factions.nextId(server), FactionType.FIEFDOM, Names.kingdom(random), kingdom.color(), seat,
			KingdomEvents.claim(server, seat, FIEF_CLAIM));
		Factions.put(server, fief);
		int id = fief.id();
		Relations.vassalize(server, id, kingdom.id());
		Wars.syncRelations(server);
		hold(server, fief, start, path);
		Castle.Keep keep = Seats.keep(level, Factions.get(server, id), pick.seat(), seat, true, true);
		Factions.putKeep(server, id, keep);
		KingdomEvents.crown(level, Factions.get(server, id));
		for (int i = 0; i < FIEF_GUARDS; i++) {
			KingdomEvents.spawn(level, Factions.get(server, id), Folk.GUARD);
		}
		for (int i = 0; i < keep.posts().size(); i++) {
			KingdomEvents.spawnArcher(level, Factions.get(server, id));
		}
		storehouse(level, fief, pick, keep);
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.grim.fiefdom", fief.name(), kingdom.name())
			.withStyle(ChatFormatting.GOLD));
		JaymosKingdoms.LOGGER.info("The fiefdom of {} ({}'s) took the Grim {} at {}; its lord's throne at {}", fief.name(), kingdom.name(), path, seat,
			keep.throne());
		return Factions.get(server, id);
	}

	/** A barbarian camp in a small Grim structure: its chief and raiders seated there, a storehouse, a builder crew. */
	static @Nullable Faction foundCamp(ServerLevel level, StructureStart start, String path, Faction kingdom) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		Seats.Pick pick = Seats.pick(level, Seats.buildings(start), kingdom.seat(), false);
		BlockPos middle = start.getBoundingBox().getCenter();
		BlockPos seat = pick != null ? pick.seat().middle()
			: Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle), 6);
		DyeColor color = DyeColor.values()[random.nextInt(DyeColor.values().length)];
		Faction camp = Faction.create(Factions.nextId(server), FactionType.BARBARIAN, Camps.name(server, random), color, seat,
			KingdomEvents.claim(server, seat, CAMP_CLAIM));
		Factions.put(server, camp);
		int id = camp.id();
		hold(server, camp, start, path);
		Castle.Keep keep = pick != null ? Seats.keep(level, camp, pick.seat(), seat, false, false) : new Castle.Keep(seat, seat, seat, seat, List.of());
		Factions.putKeep(server, id, keep);
		Direction gate = Direction.getApproximateNearest(kingdom.seat().getX() - seat.getX(), 0, kingdom.seat().getZ() - seat.getZ());
		long now = level.getGameTime();
		Camps.putData(server, id, new Camps.CampData(seat, gate, now, now, Production.day(server), -1L, List.of(), 0L, Map.of(), Camps.BARBARIAN, 0, 0));
		Camps.takeSides(server, id, Camps.BARBARIAN);
		KingdomEvents.crown(level, Factions.get(server, id));
		for (int i = 0; i < Camps.START_BARBARIANS; i++) {
			KingdomEvents.spawn(level, Factions.get(server, id), Folk.BARBARIAN);
		}
		storehouse(level, camp, pick, keep);
		Camps.stock(level, Factions.get(server, id));
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.grim.camp", camp.name(), kingdom.name())
			.withStyle(ChatFormatting.RED));
		JaymosKingdoms.LOGGER.info("A barbarian camp, {}, took the Grim {} at {} near {}", camp.name(), path, seat, kingdom.name());
		return Factions.get(server, id);
	}

	// ---- the second building -----------------------------------------------------------------------------------------

	/** Holds cleared and standing long enough begin their second Grim building, if their builders find a site for one. */
	private static void erect(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		for (var e : Holds.all(server).entrySet()) {
			Holds.Hold h = e.getValue();
			int id = Integer.parseInt(e.getKey());
			if (!h.isGrim() || !h.cleared() || h.erected() || now - h.founded() < ERECT_AFTER_TICKS || NEXT_ERECT.getOrDefault(id, 0L) > now) {
				continue;
			}
			Faction f = Factions.get(server, id);
			if (f == null || f.fallen() || !level.isPositionEntityTicking(f.seat()) || !Storage.loaded(level, f)
				|| !Works.of(server, id).projects().isEmpty() || Builders.crew(level, f).isEmpty()) {
				continue;
			}
			List<String> choices = new ArrayList<>(f.type() == FactionType.FIEFDOM ? ERECT_FIEF : ERECT_CAMP);
			Collections.shuffle(choices, new java.util.Random(level.getRandom().nextLong()));
			boolean begun = false;
			for (String path : choices) {
				if (blueprintOrNull(level, path) == null || Planner.entry(f, entry(path)) == null) {
					continue;
				}
				Works.Project project = Planner.plan(level, f, true, entry(path));
				if (project != null) {
					Holds.update(server, id, held -> held.withErected(true));
					KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.grim.erect", f.name()).withStyle(ChatFormatting.GRAY));
					begun = true;
					break;
				}
			}
			if (!begun) {
				NEXT_ERECT.put(id, now + ERECT_RETRY_TICKS);
			}
		}
	}

	/** The Grim structure {@code path}'s main template as a blueprint (an empty one if it can't be read). */
	static Blueprint blueprint(ServerLevel level, String path) {
		Blueprint bp = blueprintOrNull(level, path);
		return bp != null ? bp : new Blueprint(entry(path), 1, 1, 1, List.of());
	}

	/**
	 * Reads the first template of Grim structure {@code path}'s start pool into a blueprint: every block but air,
	 * structure voids and jigsaws, trimmed to what's there, its lowest layer laid in place of the ground. Null if it
	 * can't be read or it's bigger than {@link #ERECT_MAX_SIDE} across or {@link #ERECT_MAX_BLOCKS} blocks. Containers
	 * come empty (a blueprint has no loot).
	 */
	static @Nullable Blueprint blueprintOrNull(ServerLevel level, String path) {
		return BLUEPRINTS.computeIfAbsent(path, p -> Optional.ofNullable(read(level, p))).orElse(null);
	}

	private static @Nullable Blueprint read(ServerLevel level, String path) {
		StructureTemplatePool pool = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL)
			.getValue(Identifier.fromNamespaceAndPath(NAMESPACE, path));
		if (pool == null) {
			return null;
		}
		StructureTemplate template = null;
		for (Pair<StructurePoolElement, Integer> pair : pool.getTemplates()) {
			if (pair.getFirst() instanceof SinglePoolElement single) {
				try {
					template = level.getStructureTemplateManager().get(single.getTemplateLocation()).orElse(null);
				} catch (RuntimeException e) {
					template = null;
				}
				if (template != null) {
					break;
				}
			}
		}
		if (template == null) {
			return null;
		}
		CompoundTag tag = template.save(new CompoundTag());
		ListTag palette = tag.getList("palette").orElseGet(() -> tag.getListOrEmpty("palettes").getListOrEmpty(0));
		List<BlockState> states = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, palette.getCompoundOrEmpty(i)));
		}
		List<Blueprint.Placement> raw = new ArrayList<>();
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE,
			maxZ = Integer.MIN_VALUE;
		ListTag blocks = tag.getListOrEmpty("blocks");
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompoundOrEmpty(i);
			int s = b.getIntOr("state", -1);
			if (s < 0 || s >= states.size()) {
				continue;
			}
			BlockState state = states.get(s);
			if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_BLOCK)) {
				continue;
			}
			ListTag pos = b.getListOrEmpty("pos");
			BlockPos at = new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
			raw.add(new Blueprint.Placement(at, state));
			minX = Math.min(minX, at.getX());
			minY = Math.min(minY, at.getY());
			minZ = Math.min(minZ, at.getZ());
			maxX = Math.max(maxX, at.getX());
			maxY = Math.max(maxY, at.getY());
			maxZ = Math.max(maxZ, at.getZ());
		}
		if (raw.isEmpty() || raw.size() > ERECT_MAX_BLOCKS || maxX - minX + 1 > ERECT_MAX_SIDE || maxZ - minZ + 1 > ERECT_MAX_SIDE) {
			JaymosKingdoms.LOGGER.info("Grim {}: not raised by builders ({} blocks, {} x {})", path, raw.size(), maxX - minX + 1, maxZ - minZ + 1);
			return null;
		}
		List<Blueprint.Placement> placed = new ArrayList<>();
		for (Blueprint.Placement pl : raw) {
			placed.add(new Blueprint.Placement(pl.offset().offset(-minX, -minY, -minZ), pl.state()));
		}
		return new Blueprint(entry(path), maxX - minX + 1, maxZ - minZ + 1, maxY - minY + 1, List.copyOf(placed));
	}
}
