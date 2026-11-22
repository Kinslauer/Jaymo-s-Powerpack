package com.jaymoskingdoms;

import com.mojang.datafixers.util.Pair;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

/**
 * Kingdoms that rise without a visit. Andy (2026-11-21): a village only became a settlement when a player came near
 * its bell, so the world held few realms to make war and trade with; "is it possible to have the player teleport to a
 * random village in each biome, so that it is loaded? or load them otherwise". No one is moved (no visible teleports):
 * in the background, one town at a time, the nearest Epic towns of each of the five lands round the world's spawn
 * ({@link EpicTowns#TOWNS}: plains, desert, savanna, snowy, taiga) have their chunks made with a ticket, as
 * {@link EpicTowns#foundFar} does for a story kingdom, and a kingdom is founded in each ({@link KingdomEvents#foundIn}),
 * until every land holds {@link #PER_LAND} kingdoms or none of its towns is left within {@link #RINGS} village spacings.
 * The ticket lapses once the town is founded or passed over, so its land then rests like any other unvisited land.
 * Which lands are done is read from the kingdoms that stand (the land round each seat), so nothing is saved.
 */
public final class TownWakes {
	/** Kingdoms each land should hold before its towns are left to be found by players. */
	static final int PER_LAND = 2;
	/** How far round the world's spawn its towns are looked for, in village spacings (34 chunks each). */
	static final int RINGS = 8;
	/** How often (ticks) the next step is taken, how long the server must have run first, and how long a town's land may take to be made before it is passed over. */
	static final int STEP_TICKS = 40, START_AFTER = 1200, WAIT_TICKS = 1200;
	/** The chunks made round a town's start while it waits. */
	static final int LAND = 10;
	private static final TicketType WAKE = new TicketType(300L, TicketType.FLAG_LOADING);

	/** Start chunks (packed) already founded in or passed over; the lands that have nothing left to look at. */
	private static final Set<Long> PASSED = new HashSet<>();
	private static final Set<String> EXHAUSTED = new HashSet<>();
	/** The town being made ready: its land, its start chunk, and since when (null: none). */
	private static @Nullable Waiting waiting;

	private record Waiting(String town, ChunkPos chunk, long since) {
	}

	private TownWakes() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TownWakes::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PASSED.clear();
			EXHAUSTED.clear();
			waiting = null;
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() < START_AFTER || server.getTickCount() % STEP_TICKS != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		if (!level.structureManager().shouldGenerateStructures()) {
			return;
		}
		if (waiting == null) {
			String town = nextLand(level);
			if (town == null) {
				return;
			}
			Pair<BlockPos, Holder<Structure>> next = nearest(level, town);
			if (next == null) {
				JaymosKingdoms.LOGGER.info("No {} left within {} spacings of spawn to raise a kingdom in", town, RINGS);
				EXHAUSTED.add(town);
				return;
			}
			waiting = new Waiting(town, ChunkPos.containing(next.getFirst()), level.getGameTime());
			JaymosKingdoms.LOGGER.info("Waking the {} at {} into a kingdom", town, next.getFirst());
		}
		step(level, waiting);
	}

	/** The first land (an Epic town's id) still short of kingdoms and not yet looked through, or null. */
	private static @Nullable String nextLand(ServerLevel level) {
		for (String town : EpicTowns.TOWNS) {
			if (!EXHAUSTED.contains(town) && kingdoms(level, town) < PER_LAND) {
				return town;
			}
		}
		return null;
	}

	/** Kingdoms standing on {@code town}'s land (the town its seat's land takes). */
	static int kingdoms(ServerLevel level, String town) {
		int n = 0;
		for (Faction f : Factions.all(level.getServer(), FactionType.KINGDOM)) {
			if (!f.fallen() && EpicTowns.surface(level, f.seat()) && EpicTowns.villageFor(Palette.of(level.getBiome(f.seat()))).equals(town)) {
				n++;
			}
		}
		return n;
	}

	/** The nearest start of {@code town} round the world's spawn not founded in or passed over yet. */
	private static @Nullable Pair<BlockPos, Holder<Structure>> nearest(ServerLevel level, String town) {
		Optional<Holder.Reference<Structure>> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Identifier.parse(town));
		if (structure.isEmpty()) {
			return null;
		}
		BlockPos spawn = level.getRespawnData().pos();
		return Scout.find(level, HolderSet.direct(List.of(structure.get())), spawn, RINGS, chunk -> PASSED.contains(chunk.pack()));
	}

	/** Its land made a step further; once it stands whole, the kingdom is founded or the town passed over. */
	private static void step(ServerLevel level, Waiting w) {
		if (level.getGameTime() - w.since() > WAIT_TICKS) {
			pass(w, "its land was not made in time");
			return;
		}
		level.getChunkSource().addTicketWithRadius(WAKE, w.chunk(), LAND);
		LevelChunk chunk = level.getChunkSource().getChunkNow(w.chunk().x(), w.chunk().z());
		if (chunk == null) {
			return;
		}
		Optional<Holder.Reference<Structure>> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(Identifier.parse(w.town()));
		StructureStart start = structure.map(s -> level.structureManager().getStartForStructure(s.value(), chunk)).orElse(null);
		if (start == null || !start.isValid()) {
			pass(w, "no town started there");
			return;
		}
		if (!EpicTowns.surface(level, start)) {
			pass(w, "it is not in the Overworld's band");
			return;
		}
		BoundingBox box = start.getBoundingBox();
		BlockPos middle = box.getCenter();
		if (Factions.realmNear(level.getServer(), middle, KingdomEvents.CAPITAL_SPACING) != null) {
			pass(w, "too near another kingdom's capital");
			return;
		}
		if (Factions.near(level.getServer(), middle, KingdomEvents.TOWN_RADIUS) != null) {
			pass(w, "another faction holds it");
			return;
		}
		if (!Loaded.made(level, box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
			return;
		}
		Faction kingdom = EpicTowns.crown(level, start, w.town());
		if (kingdom == null) {
			pass(w, "no building in it is fit for a king");
			return;
		}
		JaymosKingdoms.LOGGER.info("{} rises in the {} at {} without a visit", kingdom.name(), w.town(), kingdom.seat());
		PASSED.add(w.chunk().pack());
		waiting = null;
	}

	private static void pass(Waiting w, String why) {
		JaymosKingdoms.LOGGER.info("The {} at chunk {} is not woken: {}", w.town(), w.chunk(), why);
		PASSED.add(w.chunk().pack());
		waiting = null;
	}
}
