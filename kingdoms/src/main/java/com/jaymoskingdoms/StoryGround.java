package com.jaymoskingdoms;

import com.jaymoskingdoms.remnants.Element;
import com.jaymoskingdoms.remnants.Monoliths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.228, Andy: "I need you to generate whatever is required for the player to choose any of the options") What a life
 * story's start needs, made when the world hasn't got it yet, so no start falls back to the wild: a realm's capital (a
 * new world has none till someone walks up to a village's bell), a barbarian camp, a monolith, a harbour for a whaler.
 * A capital or a harbour needs its land loaded and ticking, so those are made over a few seconds while the player waits.
 */
public final class StoryGround {
	/** Chunks kept loaded round a place being made (it ticks two short of this: 128 blocks, past a harbour's search). */
	static final int TICKET_RADIUS = 10;
	/** How often a waiting job tries again, and how long before it settles for less (a citadel, the wild). */
	static final int TRY_EVERY = 20, GIVE_UP_TICKS = 1800;
	/** The bell searched for round a village's middle. */
	static final int BELL_SEARCH = 96;
	/**
	 * (0.7.243) The first capital: a village at most this far from spawn (chunks), else a citadel by spawn. Andy's world
	 * raised one in a village 900 blocks off, and making all that land at once stalled the server for nearly a minute.
	 * (0.7.350) Really chunks now: findNearestMapStructure counted it in village spacings (24 of them, thousands of
	 * blocks), and its search held a new player's join up for 20 s. 40 chunks still reaches the village Andy's last world
	 * found (30 chunks out).
	 */
	static final int CAPITAL_VILLAGE_SEARCH = 40;
	/** (0.7.350) Time the capital's village search may take each tick (ms), so the server keeps ticking meanwhile. */
	static final int VILLAGE_SEARCH_MS = 15;
	/** A new barbarian camp: this far from spawn. */
	static final int CAMP_MIN = 200, CAMP_MAX = 360;
	/** A new monolith: this far from spawn, and the spots tried. */
	static final int MONOLITH_MIN = 160, MONOLITH_MAX = 320, MONOLITH_TRIES = 24;
	/** The sea searched for a coastal capital; a coastal realm's seat is within a harbour's reach of it. */
	static final int OCEAN_SEARCH = 4800, OCEAN_STEP = 32, OCEAN_VERTICAL_STEP = 64;
	/** A new coastal capital: walked in from the sea in steps to the shore, then this far inland. */
	static final int SHORE_STEP = 8, SHORE_WALK = 1200, SHORE_INLAND = 40;
	/** (0.7.236) How long the loading screen stays up after a story puts you somewhere, while the land there loads. */
	static final int SETTLE_TICKS = 60;
	/** (0.7.236) How often who's held is looked at, and the loading screen raised or let down. */
	static final int HOLD_EVERY = 5;
	/** (0.7.247) Ticks after a newcomer first sees the world before what they'd miss (attuning's flare) may happen. */
	static final int REVEAL_TICKS = 40;
	/** Kept from harm while they wait (refreshed each try). */
	static final int WAIT_RESISTANCE = TRY_EVERY * 3;
	/** (0.7.235) A whaler's story: the sea kept ticking round her mooring, the misses before a realm is passed over, and how long it all may take. */
	static final int WHALER_REACH = 32, WHALER_MISSES = 15, WHALER_GIVE_UP_TICKS = 3600;
	/**
	 * (0.7.414) A trader's road: how long a milestone is waited for (ticks: a road is planned off the server thread, then paved as
	 * its ends load), and how far from the first town a second one is raised when the world has only one (blocks).
	 */
	static final int ROAD_GIVE_UP_TICKS = 4800, SECOND_TOWN_MIN = 220, SECOND_TOWN_MAX = 320;

	/** Something being made: tried each {@link #TRY_EVERY} ticks till {@code attempt} says it's done, or its time is up. */
	private static final class Job {
		BlockPos at;
		long giveUpAt;
		final Predicate<ServerLevel> attempt;
		final java.util.function.Consumer<ServerLevel> fallback;
		final List<Runnable> then = new ArrayList<>();
		final List<UUID> waiting = new ArrayList<>();
		/** (0.7.350) The capital's village still being looked for (null: found, or not looked for). */
		@Nullable VillageSearch search;

		Job(BlockPos at, long giveUpAt, Predicate<ServerLevel> attempt, java.util.function.Consumer<ServerLevel> fallback) {
			this.at = at;
			this.giveUpAt = giveUpAt;
			this.attempt = attempt;
			this.fallback = fallback;
		}
	}

	/** The capital being raised (one for everyone), and each player's harbour being built. */
	private static @Nullable Job capital;
	private static final Map<UUID, Job> HARBOURS = new HashMap<>();
	/** (0.7.414) Each trader's milestone being waited for, and the milestone found for each (taken by {@link #stone}). */
	private static final Map<UUID, Job> ROADWAYS = new HashMap<>();
	private static final Map<UUID, Roads.Stone> STONES = new HashMap<>();
	/** Each player's realm whose whaler was made ready, while they sign on. */
	private static final Map<UUID, Integer> WHALER_REALMS = new HashMap<>();
	/** (0.7.236) Player → server tick their arrival's loading screen comes down; and the hold last sent to each. */
	private static final Map<UUID, Integer> SETTLING = new HashMap<>();
	private static final Map<UUID, String> SENT = new HashMap<>();
	/** (0.7.247) Player → the last server tick they couldn't see the world (story, loading screen, MCA's creator). */
	private static final Map<UUID, Integer> HIDDEN = new HashMap<>();

	/**
	 * (0.7.236) Andy: "can the player be held in a stasis menu or faux load screen at the beginning while everything
	 * loads?" Server → player: show the loading screen with this line ({@code key}, a lang key), or take it down ("").
	 */
	public record Hold(String key) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Hold> TYPE = new CustomPacketPayload.Type<>(JaymosKingdoms.id("story_hold"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hold> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Hold::key, Hold::new);

		@Override
		public CustomPacketPayload.Type<Hold> type() {
			return TYPE;
		}
	}

	private StoryGround() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(StoryGround::searchVillages);
		ServerTickEvents.END_SERVER_TICK.register(StoryGround::tick);
		ServerTickEvents.END_SERVER_TICK.register(StoryGround::holds);
		PayloadTypeRegistry.clientboundPlay().register(Hold.TYPE, Hold.CODEC);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			capital = null;
			HARBOURS.clear();
			ROADWAYS.clear();
			STONES.clear();
			WHALER_REALMS.clear();
			SETTLING.clear();
			SENT.clear();
			HIDDEN.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SETTLING.remove(handler.player.getUUID());
			SENT.remove(handler.player.getUUID());
			HIDDEN.remove(handler.player.getUUID());
		});
		// a newcomer's story will want realms to name: start raising the first capital while they make their MCA character
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (LifeStory.owed(handler.player) && realmless(server)) {
				capital(server, null, null);
			}
		});
	}

	// ---- realms ------------------------------------------------------------------------------------------------------

	/** No standing realm in the world. */
	static boolean realmless(MinecraftServer server) {
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Raises the world's first capital (in the village nearest spawn, else a citadel there), then runs {@code then};
	 * {@code waiting} is held safe meanwhile. With a realm standing already, {@code then} runs at once.
	 */
	static void capital(MinecraftServer server, @Nullable ServerPlayer waiting, @Nullable Runnable then) {
		if (!realmless(server)) {
			if (then != null) {
				then.run();
			}
			return;
		}
		ServerLevel level = Factions.level(server);
		if (capital == null) {
			// (0.7.350) the village is looked for a few places a tick (searchVillages), not all at once on the join
			VillageSearch search = new VillageSearch(level, level.getRespawnData().pos());
			Job job = new Job(search.spawn, Long.MAX_VALUE, l -> {
				if (job().search != null) {
					return false;
				}
				// (0.7.243) with no village near, the citadel goes up at the first try, not after waiting out the village's time
				BlockPos village = search.found;
				return village != null ? foundInVillage(l, village) : citadel(l, job().at) != null || !realmless(l.getServer());
			}, l -> citadel(l, job().at));
			job.search = search;
			capital = job;
		}
		if (waiting != null) {
			capital.waiting.add(waiting.getUUID());
		}
		if (then != null) {
			capital.then.add(then);
		}
	}

	/** The capital's job, from inside its own tries (only ever run while it's set). */
	private static Job job() {
		return java.util.Objects.requireNonNull(capital);
	}

	/**
	 * (0.7.350) Where the first capital's village might be: every village's possible starts within
	 * {@link #CAPITAL_VILLAGE_SEARCH} chunks of spawn, nearest first, looked at for a few ms a tick. What vanilla's
	 * findNearestMapStructure does, but bounded in chunks and spread over ticks.
	 */
	private static final class VillageSearch {
		record Spot(Structure structure, StructurePlacement placement, ChunkPos chunk, int distance) {
		}

		final BlockPos spawn;
		final List<Spot> spots = new ArrayList<>();
		int next;
		@Nullable BlockPos found;
		final long startedAt = System.nanoTime();

		VillageSearch(ServerLevel level, BlockPos spawn) {
			this.spawn = spawn;
			if (!level.structureManager().shouldGenerateStructures()) {
				return;
			}
			Optional<HolderSet.Named<Structure>> villages = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(StructureTags.VILLAGE);
			if (villages.isEmpty()) {
				return;
			}
			ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
			ChunkPos origin = ChunkPos.containing(spawn);
			int reach = CAPITAL_VILLAGE_SEARCH * CAPITAL_VILLAGE_SEARCH;
			for (Holder<Structure> village : villages.get()) {
				for (StructurePlacement placement : state.getPlacementsForStructure(village)) {
					if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
						continue;
					}
					int spacing = spread.spacing();
					int cells = CAPITAL_VILLAGE_SEARCH / spacing + 1;
					for (int dx = -cells; dx <= cells; dx++) {
						for (int dz = -cells; dz <= cells; dz++) {
							ChunkPos chunk = spread.getPotentialStructureChunk(state.getLevelSeed(), origin.x() + dx * spacing, origin.z() + dz * spacing);
							int distance = chunk.distanceSquared(origin);
							if (distance <= reach) {
								this.spots.add(new Spot(village.value(), placement, chunk, distance));
							}
						}
					}
				}
			}
			this.spots.sort(Comparator.comparingInt(Spot::distance));
		}

		/** Looks at spots till {@code budgetNanos} is used up; true once it's over (found, or none left). */
		boolean step(ServerLevel level, long budgetNanos) {
			long until = System.nanoTime() + budgetNanos;
			while (this.next < this.spots.size()) {
				Spot spot = this.spots.get(this.next++);
				this.found = village(level, spot);
				if (this.found != null) {
					return true;
				}
				if (System.nanoTime() >= until) {
					return false;
				}
			}
			return true;
		}

		/** Whether a village starts at {@code spot}, where vanilla's search would say (null: none). */
		private static @Nullable BlockPos village(ServerLevel level, Spot spot) {
			StructureManager structures = level.structureManager();
			StructureCheckResult result = structures.checkStructurePresence(spot.chunk(), spot.structure(), spot.placement(), false);
			if (result == StructureCheckResult.START_PRESENT) {
				return spot.placement().getLocatePos(spot.chunk());
			}
			if (result == StructureCheckResult.CHUNK_LOAD_NEEDED) {
				ChunkAccess chunk = level.getChunk(spot.chunk().x(), spot.chunk().z(), ChunkStatus.STRUCTURE_STARTS);
				StructureStart start = structures.getStartForStructure(spot.structure(), chunk);
				if (start != null && start.isValid()) {
					return spot.placement().getLocatePos(start.getChunkPos());
				}
			}
			return null;
		}
	}

	/** (0.7.350) Each tick: a few ms more of the capital's village search, and the capital set going once it's over. */
	private static void searchVillages(MinecraftServer server) {
		Job job = capital;
		if (job == null || job.search == null) {
			return;
		}
		VillageSearch search = job.search;
		ServerLevel level = Factions.level(server);
		boolean over;
		try {
			over = search.step(level, VILLAGE_SEARCH_MS * 1_000_000L);
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("The first capital's village search failed; raising it at spawn", e);
			search.found = null;
			over = true;
		}
		if (!over) {
			return;
		}
		job.search = null;
		if (search.found != null) {
			job.at = search.found;
		}
		job.giveUpAt = level.getGameTime() + GIVE_UP_TICKS;
		JaymosKingdoms.LOGGER.info("No realm yet for a life story: raising a capital {} ({} of {} places looked at in {} ms)",
			search.found != null ? "in the village at " + search.found : "at spawn", search.next, search.spots.size(),
			(System.nanoTime() - search.startedAt) / 1_000_000L);
	}

	/** The village at {@code village} becomes a kingdom's capital, once it's loaded round its bell (false: not yet). */
	private static boolean foundInVillage(ServerLevel level, BlockPos village) {
		if (!realmless(level.getServer())) {
			// someone walked up to a bell meanwhile
			return true;
		}
		if (!level.isPositionEntityTicking(village)) {
			return false;
		}
		PoiManager poi = level.getPoiManager();
		poi.ensureLoadedAndValid(level, village, BELL_SEARCH);
		Optional<BlockPos> bell = poi.findClosest(holder -> holder.is(PoiTypes.MEETING), village, BELL_SEARCH, PoiManager.Occupancy.ANY);
		if (bell.isEmpty()) {
			return false;
		}
		Seats.Found found = Seats.structureAt(level, bell.get(), Seats::village);
		if (found.status() != Seats.Status.FOUND || found.start() == null) {
			return false;
		}
		BoundingBox area = found.start().getBoundingBox();
		BlockPos b = bell.get();
		int reach = KingdomEvents.LOAD_REACH;
		if (!level.hasChunksAt(Math.max(area.minX(), b.getX() - reach), Math.max(area.minZ(), b.getZ() - reach),
			Math.min(area.maxX(), b.getX() + reach), Math.min(area.maxZ(), b.getZ() + reach))) {
			return false;
		}
		if (KingdomEvents.foundIn(level, b, found.start()) == null) {
			KingdomEvents.found(level, b, Castle.Kind.CITADEL);
		}
		return true;
	}

	/** A kingdom's citadel on open ground near {@code near} (no village would do). */
	private static @Nullable Faction citadel(ServerLevel level, BlockPos near) {
		if (!realmless(level.getServer())) {
			return null;
		}
		BlockPos ground = LifeStory.ground(level, near, 0, LifeStory.STRUCTURE_NEAR * 4, level.getRandom());
		BlockPos at = ground != null ? ground : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near);
		return KingdomEvents.found(level, at, Castle.Kind.CITADEL);
	}

	// ---- camps and monoliths (made at once) --------------------------------------------------------------------------

	/** A new barbarian camp a few hundred blocks from {@code from} (null: no ground found for it). */
	static @Nullable Faction camp(ServerLevel level, BlockPos from, RandomSource random) {
		BlockPos at = LifeStory.ground(level, from, CAMP_MIN, CAMP_MAX, random);
		if (at == null) {
			return null;
		}
		JaymosKingdoms.LOGGER.info("No barbarian camp for a captive's story: raising one at {}", at);
		return Camps.found(level, at, at.getY(), Direction.Plane.HORIZONTAL.getRandomDirection(random));
	}

	/** A new monolith a few hundred blocks from {@code from}: its foot. */
	static @Nullable BlockPos monolith(ServerLevel level, BlockPos from, RandomSource random) {
		BlockPos rough = null;
		for (int i = 0; i < MONOLITH_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = MONOLITH_MIN + random.nextInt(MONOLITH_MAX - MONOLITH_MIN + 1);
			BlockPos column = from.offset(Mth.floor(Math.cos(angle) * r), 0, Mth.floor(Math.sin(angle) * r));
			level.getChunk(column.getX() >> 4, column.getZ() >> 4);
			BlockPos foot = Monoliths.fit(level, column);
			if (foot == null) {
				if (rough == null && level.getFluidState(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below()).isEmpty()) {
					rough = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				}
				continue;
			}
			if (Factions.around(level.getServer(), level, foot, Monoliths.SETTLEMENT_CLEARANCE) == null) {
				return raise(level, foot, random);
			}
		}
		// (rough ground or crowded land everywhere tried: it stands on the first dry ground anyway)
		return rough == null ? null : raise(level, rough, random);
	}

	private static BlockPos raise(ServerLevel level, BlockPos foot, RandomSource random) {
		JaymosKingdoms.LOGGER.info("No monolith for a life story: raising one at {}", foot);
		return Monoliths.raise(level, foot, Element.of(level.getBiome(foot)), random).pos();
	}

	// ---- harbours ----------------------------------------------------------------------------------------------------

	/**
	 * {@code player}'s whaler, fitted out and lying at a realm's harbour, then {@code then}; on giving up, {@code fallback}.
	 * (0.7.235: the whaler start sent everyone to the wild: she was fitted out at once, at a harbour whose sea wasn't
	 * loaded round her.) The realm is the one nearest spawn with a harbour that has a berth, else the coastal realm
	 * nearest spawn (a harbour built for it), else a new kingdom raised on the shore nearest spawn; its land is kept
	 * loaded and ticking till she floats. A realm whose harbour can't be built or whose whaler won't float is passed
	 * over for the next.
	 */
	static void harbour(ServerPlayer player, Runnable then, Runnable fallback) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = Factions.level(server);
		BlockPos spawn = level.getRespawnData().pos();
		Whaler w = new Whaler(player.getUUID(), spawn);
		w.realm = w.next(level);
		Faction first = w.realm < 0 ? null : Factions.get(server, w.realm);
		if (first == null) {
			fallback.run();
			return;
		}
		JaymosKingdoms.LOGGER.info("A whaler's story: {} fits out a whaler", first.name());
		Job job = new Job(first.seat(), level.getGameTime() + WHALER_GIVE_UP_TICKS, w::attempt, l -> {
			WHALER_REALMS.remove(w.player);
			if (!w.made) {
				fallback.run();
			}
		});
		w.job = job;
		job.waiting.add(player.getUUID());
		job.then.add(() -> {
			if (w.made) {
				WHALER_REALMS.put(w.player, w.realm);
				then.run();
				WHALER_REALMS.remove(w.player);
			}
		});
		HARBOURS.put(player.getUUID(), job);
	}

	// ---- roads (0.7.414) ---------------------------------------------------------------------------------------------

	/**
	 * (0.7.414) A King's Road milestone for {@code player} to wake beside, then {@code then} ({@link #stone} gives it); on giving
	 * up, {@code fallback}. A capital is raised if there is no realm; a second town (a citadel a few hundred blocks off) if only
	 * one; the road between them is planned and its ends loaded so the milestones are set up ({@link Roads#hurry},
	 * {@link Roads#openEnds}).
	 */
	static void milestone(ServerPlayer player, Runnable then, Runnable fallback) {
		MinecraftServer server = player.level().getServer();
		if (realmless(server)) {
			capital(server, player, () -> milestone(player, then, fallback));
			return;
		}
		ServerLevel level = Factions.level(server);
		UUID id = player.getUUID();
		BlockPos spawn = level.getRespawnData().pos();
		boolean[] raised = {false};
		Job job = new Job(spawn, level.getGameTime() + ROAD_GIVE_UP_TICKS, l -> {
			Roads.Stone stone = Roads.stoneNear(l, spawn);
			if (stone != null) {
				STONES.put(id, stone);
				return true;
			}
			if (Roads.towns(l.getServer()).size() < 2) {
				if (!raised[0]) {
					raised[0] = true;
					secondTown(l, spawn);
				}
				return false;
			}
			Roads.hurry(l.getServer());
			BlockPos opened = Roads.openEnds(l, spawn);
			Job mine = ROADWAYS.get(id);
			if (opened != null && mine != null) {
				mine.at = opened;
			}
			return false;
		}, l -> fallback.run());
		job.waiting.add(id);
		job.then.add(then);
		ROADWAYS.put(id, job);
	}

	/** A second kingdom's citadel a few hundred blocks from the first town's seat (so a road can join them). */
	private static void secondTown(ServerLevel level, BlockPos spawn) {
		List<Faction> towns = Roads.towns(level.getServer());
		BlockPos from = towns.isEmpty() ? spawn : towns.getFirst().seat();
		BlockPos ground = LifeStory.ground(level, from, SECOND_TOWN_MIN, SECOND_TOWN_MAX, level.getRandom());
		if (ground != null) {
			JaymosKingdoms.LOGGER.info("No second town for a trader's road: raising one at {}", ground);
			KingdomEvents.found(level, ground, Castle.Kind.CITADEL);
		}
	}

	/** The milestone made ready for {@code player}'s story (taken: null if there is none). */
	static Roads.@Nullable Stone stone(ServerPlayer player) {
		return STONES.remove(player.getUUID());
	}

	/** The realm whose whaler was made ready for {@code player}'s story, while they're signed on (-1: none). */
	static int whalerRealm(ServerPlayer player) {
		return WHALER_REALMS.getOrDefault(player.getUUID(), -1);
	}

	/** A whaler being made ready for a story: the realm tried, the realms passed over, and how many tries it's missed. */
	private static final class Whaler {
		final UUID player;
		final BlockPos spawn;
		final java.util.Set<Integer> passed = new java.util.HashSet<>();
		int realm = -1, misses;
		boolean founded, made, settled;
		Job job;

		Whaler(UUID player, BlockPos spawn) {
			this.player = player;
			this.spawn = spawn;
		}

		/** The next realm to try (-1: none left, and no shore to raise one on). */
		int next(ServerLevel level) {
			misses = 0;
			Faction f = coastalRealm(level, spawn, passed);
			if (f == null && !founded) {
				founded = true;
				BlockPos seat = shore(level, spawn);
				f = seat == null ? null : KingdomEvents.found(level, seat, Castle.Kind.CITADEL);
			}
			return f == null ? -1 : f.id();
		}

		/** One try: the realm's land ticking, its harbour built, its whaler afloat at her mooring. */
		boolean attempt(ServerLevel level) {
			MinecraftServer server = level.getServer();
			Faction f = realm < 0 ? null : Factions.get(server, realm);
			if (f == null || f.fallen() || f.seat() == null) {
				passOver(level);
				return false;
			}
			Navies.Harbour h = Navies.harbour(server, f.id());
			if (h == null) {
				job.at = f.seat();
				if (!ticking(level, f.seat(), PirateCoves.COAST_REACH)) {
					return false;
				}
				h = Navies.build(level, f);
				if (h == null) {
					if (++misses >= WHALER_MISSES) {
						JaymosKingdoms.LOGGER.info("A whaler's story: {} can't build a harbour, trying elsewhere", f.name());
						passOver(level);
					}
					return false;
				}
				misses = 0;
			}
			job.at = h.mooring();
			if (!ticking(level, h.mooring(), WHALER_REACH)) {
				return false;
			}
			if (!settled) {
				// (one try's grace for her, already afloat there, to load with the land; else she'd be fitted out twice)
				settled = true;
				return false;
			}
			if (Whalers.ready(level, f, h)) {
				made = true;
				return true;
			}
			if (++misses >= WHALER_MISSES) {
				JaymosKingdoms.LOGGER.info("A whaler's story: {}'s whaler won't float at {}, trying elsewhere", f.name(), h.mooring());
				passOver(level);
			}
			return false;
		}

		private void passOver(ServerLevel level) {
			settled = false;
			if (realm >= 0) {
				passed.add(realm);
			}
			realm = next(level);
			if (realm < 0) {
				// (nowhere left to try: give up now)
				job.giveUpAt = 0L;
			}
		}
	}

	/**
	 * The standing realm nearest {@code from}, not in {@code passed}, with a harbour whose whaler is in or fitting out,
	 * or with no harbour yet and its seat within a harbour's reach of the sea.
	 */
	private static @Nullable Faction coastalRealm(ServerLevel level, BlockPos from, java.util.Set<Integer> passed) {
		MinecraftServer server = level.getServer();
		List<Faction> realms = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen() && f.seat() != null && !passed.contains(f.id())) {
				realms.add(f);
			}
		}
		realms.sort(Comparator.comparingDouble(f -> f.seat().distSqr(from)));
		for (Faction f : realms) {
			if (Navies.harbour(server, f.id()) != null) {
				if (Whalers.free(server, f)) {
					return f;
				}
			} else if (level.findClosestBiome3d(StoryGround::ocean, f.seat(), PirateCoves.COAST_REACH, OCEAN_STEP / 2, OCEAN_VERTICAL_STEP) != null) {
				return f;
			}
		}
		return null;
	}

	/** Dry ground a little inland of the shore nearest {@code from}, walked in from the sea toward it; or null. */
	private static @Nullable BlockPos shore(ServerLevel level, BlockPos from) {
		com.mojang.datafixers.util.Pair<BlockPos, Holder<Biome>> sea = level.findClosestBiome3d(StoryGround::ocean, from, OCEAN_SEARCH, OCEAN_STEP,
			OCEAN_VERTICAL_STEP);
		if (sea == null) {
			return null;
		}
		BlockPos water = sea.getFirst();
		double dx = from.getX() - water.getX(), dz = from.getZ() - water.getZ();
		double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		for (int d = 0; d < Math.min(len, SHORE_WALK); d += SHORE_STEP) {
			BlockPos column = water.offset(Mth.floor(dx / len * d), 0, Mth.floor(dz / len * d));
			level.getChunk(column.getX() >> 4, column.getZ() >> 4);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (level.getFluidState(top.below()).isEmpty()) {
				// on land: a little further in, for the castle
				BlockPos inland = water.offset(Mth.floor(dx / len * (d + SHORE_INLAND)), 0, Mth.floor(dz / len * (d + SHORE_INLAND)));
				level.getChunk(inland.getX() >> 4, inland.getZ() >> 4);
				BlockPos seat = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inland);
				return level.getFluidState(seat.below()).isEmpty() ? seat : top;
			}
		}
		return null;
	}

	private static boolean ocean(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_OCEAN);
	}

	/** {@code center} and the land {@code reach} round it are loaded and ticking. */
	private static boolean ticking(ServerLevel level, BlockPos center, int reach) {
		return level.isPositionEntityTicking(center) && level.isPositionEntityTicking(center.offset(reach, 0, 0))
			&& level.isPositionEntityTicking(center.offset(-reach, 0, 0)) && level.isPositionEntityTicking(center.offset(0, 0, reach))
			&& level.isPositionEntityTicking(center.offset(0, 0, -reach));
	}

	// ---- the tick ----------------------------------------------------------------------------------------------------

	// ---- the loading screen (0.7.236) -----------------------------------------------------------------------------

	/** Holds {@code player} behind the loading screen a few seconds more, while the land where the story put them loads. */
	static void settle(ServerPlayer player) {
		SETTLING.put(player.getUUID(), player.level().getServer().getTickCount() + SETTLE_TICKS);
	}

	/**
	 * (0.7.247) Andy: "if they spawn attuned they miss the animation." Whether {@code player} can't see the world yet:
	 * their story is owed, the loading screen is up, MCA's creator is still to come, or they came out from behind
	 * those less than {@link #REVEAL_TICKS} ago. What should play where they can see it (attuning) waits on this.
	 */
	public static boolean unseen(ServerPlayer player) {
		UUID id = player.getUUID();
		int now = player.level().getServer().getTickCount();
		if (LifeStory.owed(player) || SENT.containsKey(id) || SETTLING.containsKey(id) || McaCompat.destinyPending(player)) {
			HIDDEN.put(id, now);
			return true;
		}
		Integer last = HIDDEN.get(id);
		if (last == null) {
			return false;
		}
		if (now - last < REVEAL_TICKS) {
			return true;
		}
		HIDDEN.remove(id);
		return false;
	}

	/** (0.7.304) Whether {@code player} waits on a capital or a harbour being made for their start. */
	static boolean waiting(ServerPlayer player) {
		UUID id = player.getUUID();
		return capital != null && capital.waiting.contains(id) || HARBOURS.containsKey(id) || ROADWAYS.containsKey(id);
	}

	/** What {@code player} is held for (the loading screen's line), or null: free to go. */
	private static @Nullable String held(ServerPlayer player, int now) {
		UUID id = player.getUUID();
		if (capital != null && capital.waiting.contains(id)) {
			return "gui.jaymoskingdoms.story.hold.realm";
		}
		if (HARBOURS.containsKey(id)) {
			return "gui.jaymoskingdoms.story.hold.harbour";
		}
		if (ROADWAYS.containsKey(id)) {
			return "gui.jaymoskingdoms.story.hold.road";
		}
		if (LifeStory.of(player) != null && PowersCompat.waitingForFuture(player)) {
			return "gui.jaymoskingdoms.story.hold.future";
		}
		Integer until = SETTLING.get(id);
		if (until != null) {
			if (now < until) {
				return "gui.jaymoskingdoms.story.hold.arriving";
			}
			SETTLING.remove(id);
		}
		return null;
	}

	/** Raises or lets down each player's loading screen as what they're held for changes. */
	private static void holds(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % HOLD_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String key = held(player, now);
			if (!java.util.Objects.equals(key, SENT.get(player.getUUID()))) {
				if (key == null) {
					SENT.remove(player.getUUID());
				} else {
					SENT.put(player.getUUID(), key);
				}
				if (ServerPlayNetworking.canSend(player, Hold.TYPE)) {
					ServerPlayNetworking.send(player, new Hold(key == null ? "" : key));
				}
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (capital == null && HARBOURS.isEmpty() && ROADWAYS.isEmpty() || server.getTickCount() % TRY_EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		if (capital != null && step(level, capital, "message.jaymoskingdoms.story.making.realm")) {
			Job done = capital;
			capital = null;
			done.then.forEach(Runnable::run);
		}
		for (UUID id : List.copyOf(HARBOURS.keySet())) {
			Job job = HARBOURS.get(id);
			if (server.getPlayerList().getPlayer(id) == null) {
				HARBOURS.remove(id);
			} else if (step(level, job, "message.jaymoskingdoms.story.making.harbour")) {
				HARBOURS.remove(id);
				job.then.forEach(Runnable::run);
			}
		}
		for (UUID id : List.copyOf(ROADWAYS.keySet())) {
			Job job = ROADWAYS.get(id);
			if (server.getPlayerList().getPlayer(id) == null) {
				ROADWAYS.remove(id);
			} else if (step(level, job, "message.jaymoskingdoms.story.making.road")) {
				ROADWAYS.remove(id);
				job.then.forEach(Runnable::run);
			}
		}
	}

	/** One try at {@code job} (its land kept loaded, its waiting players kept safe): whether it's over, made or given up. */
	private static boolean step(ServerLevel level, Job job, String making) {
		level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(job.at), TICKET_RADIUS);
		for (UUID id : job.waiting) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player != null) {
				// (0.7.236: what they wait for is on their loading screen now, not the overlay)
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, WAIT_RESISTANCE, 4, false, false));
			}
		}
		boolean made;
		try {
			made = job.attempt.test(level);
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("A life story's start couldn't be made at {}", job.at, e);
			made = false;
		}
		if (made) {
			return true;
		}
		if (level.getGameTime() >= job.giveUpAt) {
			job.fallback.accept(level);
			return true;
		}
		return false;
	}
}
