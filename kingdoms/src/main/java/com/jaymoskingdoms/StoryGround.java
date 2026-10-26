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
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.227, Andy: "I need you to generate whatever is required for the player to choose any of the options") What a life
 * story's start needs, made when the world hasn't got it yet, so no start falls back to the wild: a realm's capital (a
 * new world has none till someone walks up to a village's bell), a barbarian camp, a monolith, a harbour for a whaler.
 * A capital or a harbour needs its land loaded and ticking, so those are made over a few seconds while the player waits.
 */
final class StoryGround {
	/** Chunks kept loaded round a place being made (it ticks two short of this: 128 blocks, past a harbour's search). */
	static final int TICKET_RADIUS = 10;
	/** How often a waiting job tries again, and how long before it settles for less (a citadel, the wild). */
	static final int TRY_EVERY = 20, GIVE_UP_TICKS = 1800;
	/** The bell searched for round a village's middle. */
	static final int BELL_SEARCH = 96;
	/** A new barbarian camp: this far from spawn. */
	static final int CAMP_MIN = 200, CAMP_MAX = 360;
	/** A new monolith: this far from spawn, and the spots tried. */
	static final int MONOLITH_MIN = 160, MONOLITH_MAX = 320, MONOLITH_TRIES = 24;
	/** The sea searched for a coastal capital; a coastal realm's seat is within a harbour's reach of it. */
	static final int OCEAN_SEARCH = 4800, OCEAN_STEP = 32, OCEAN_VERTICAL_STEP = 64;
	/** A new coastal capital: walked in from the sea in steps to the shore, then this far inland. */
	static final int SHORE_STEP = 8, SHORE_WALK = 1200, SHORE_INLAND = 40;
	/** Kept from harm while they wait (refreshed each try). */
	static final int WAIT_RESISTANCE = TRY_EVERY * 3;

	/** Something being made: tried each {@link #TRY_EVERY} ticks till {@code attempt} says it's done, or its time is up. */
	private static final class Job {
		final BlockPos at;
		final long giveUpAt;
		final Predicate<ServerLevel> attempt;
		final java.util.function.Consumer<ServerLevel> fallback;
		final List<Runnable> then = new ArrayList<>();
		final List<UUID> waiting = new ArrayList<>();

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

	private StoryGround() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(StoryGround::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			capital = null;
			HARBOURS.clear();
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
			BlockPos spawn = level.getRespawnData().pos();
			BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, spawn, LifeStory.STRUCTURE_SEARCH, false);
			BlockPos at = village != null ? village : spawn;
			capital = new Job(at, level.getGameTime() + GIVE_UP_TICKS, l -> village != null && foundInVillage(l, village),
				l -> citadel(l, at));
			JaymosKingdoms.LOGGER.info("No realm yet for a life story: raising a capital {}", village != null ? "in the village at " + village : "at spawn");
		}
		if (waiting != null) {
			capital.waiting.add(waiting.getUUID());
		}
		if (then != null) {
			capital.then.add(then);
		}
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
	 * A realm with a harbour for {@code player}'s whaler, built for them if none has one: by the coastal realm nearest
	 * spawn, or (no realm on a coast) a new kingdom raised on the shore nearest spawn. Then {@code then}; on giving up,
	 * {@code fallback}.
	 */
	static void harbour(ServerPlayer player, Runnable then, Runnable fallback) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = Factions.level(server);
		BlockPos spawn = level.getRespawnData().pos();
		Faction realm = coastalRealm(level, spawn);
		if (realm == null) {
			BlockPos seat = shore(level, spawn);
			realm = seat == null ? null : KingdomEvents.found(level, seat, Castle.Kind.CITADEL);
		}
		if (realm == null) {
			fallback.run();
			return;
		}
		int id = realm.id();
		JaymosKingdoms.LOGGER.info("No harbour for a whaler's story: {} builds one", realm.name());
		Job job = new Job(realm.seat(), level.getGameTime() + GIVE_UP_TICKS, l -> {
			Faction f = Factions.get(server, id);
			return f != null && ticking(l, f.seat(), PirateCoves.COAST_REACH) && Navies.build(l, f) != null;
		}, l -> fallback.run());
		job.waiting.add(player.getUUID());
		job.then.add(() -> {
			if (Navies.harbour(server, id) != null) {
				then.run();
			}
		});
		HARBOURS.put(player.getUUID(), job);
	}

	/** The standing realm nearest {@code from} without a harbour whose seat lies within a harbour's reach of the sea. */
	private static @Nullable Faction coastalRealm(ServerLevel level, BlockPos from) {
		List<Faction> realms = new ArrayList<>();
		for (Faction f : Factions.all(level.getServer())) {
			if (f.type().isRealm() && !f.fallen() && f.seat() != null && Navies.harbour(level.getServer(), f.id()) == null) {
				realms.add(f);
			}
		}
		realms.sort(Comparator.comparingDouble(f -> f.seat().distSqr(from)));
		for (Faction f : realms) {
			if (level.findClosestBiome3d(StoryGround::ocean, f.seat(), PirateCoves.COAST_REACH, OCEAN_STEP / 2, OCEAN_VERTICAL_STEP) != null) {
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

	private static void tick(MinecraftServer server) {
		if (capital == null && HARBOURS.isEmpty() || server.getTickCount() % TRY_EVERY != 0) {
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
	}

	/** One try at {@code job} (its land kept loaded, its waiting players kept safe): whether it's over, made or given up. */
	private static boolean step(ServerLevel level, Job job, String making) {
		level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(job.at), TICKET_RADIUS);
		for (UUID id : job.waiting) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player != null) {
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, WAIT_RESISTANCE, 4, false, false));
				player.sendOverlayMessage(Component.translatable(making).withStyle(ChatFormatting.GOLD));
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
