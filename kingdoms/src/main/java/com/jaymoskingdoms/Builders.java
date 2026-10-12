package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Every faction's builder crew: a {@link Folk#FOREMAN} and his {@link Folk#WORKER}s, dressed in their faction's style.
 * This class only keeps the crews (who's in one, how big it should be, spawning them); what they do is the
 * {@link Brain}'s, which the construction code sets with {@link #setBrain}. Until then they keep near their storage.
 */
public final class Builders {
	/** A new crew: the foreman and this many workers in all. */
	static final int CREW_START = 3;
	/** The most a crew grows to. */
	static final int CREW_MAX = 6;
	/** One more worker for every this many villagers in the settlement. */
	static final int VILLAGERS_PER_WORKER = 8;
	/** How far from their depot (or seat) they keep while idle. */
	static final int HOME_RADIUS = 16;
	/** How far round the seat a crew is looked for (they work all over the settlement). */
	static final double CREW_RANGE = 160.0;

	/** What a builder does each tick (server side). Set by the construction code; idle by default. */
	@FunctionalInterface
	public interface Brain {
		void tick(ServerLevel level, Worker builder);
	}

	private static Brain brain = (level, builder) -> {
	};

	private Builders() {
	}

	/** Sets what every builder does each tick. */
	public static void setBrain(Brain brain) {
		Builders.brain = brain;
	}

	/** What every builder does each tick now (to wrap it: {@link Debris} sends a Grim hold's crew to clear rubble first). */
	public static Brain brain() {
		return brain;
	}

	/** Called from {@link Folk} every server tick for builders. */
	static void tick(ServerLevel level, Worker builder) {
		brain.tick(level, builder);
	}

	/** How many builders (foreman and workers) {@code faction}'s roster holds. */
	public static int crewCount(Faction faction) {
		return faction.holdings().count(Folk.FOREMAN) + faction.holdings().count(Folk.WORKER);
	}

	/** How big {@code faction}'s crew should be: {@link #CREW_START}, a worker more per {@link #VILLAGERS_PER_WORKER} villagers, up to {@link #CREW_MAX}. */
	public static int crewTarget(Faction faction) {
		return Math.min(CREW_MAX, CREW_START + faction.holdings().population() / VILLAGERS_PER_WORKER);
	}

	/** The loaded members of {@code faction}'s crew, foreman first. */
	public static List<Worker> crew(ServerLevel level, Faction faction) {
		List<Worker> crew = new ArrayList<>(Workers.near(level, new AABB(faction.seat()).inflate(CREW_RANGE),
			f -> f.isAlive() && f.factionId() == faction.id() && Folk.isBuilder(f.role())));
		crew.sort(Comparator.comparingInt(f -> f.role() == Folk.FOREMAN ? 0 : 1));
		return crew;
	}

	public static @Nullable Worker foreman(ServerLevel level, Faction faction) {
		List<Worker> crew = crew(level, faction);
		return !crew.isEmpty() && crew.getFirst().role() == Folk.FOREMAN ? crew.getFirst() : null;
	}

	/** Where {@code faction}'s builders load and unload: its depot, or its seat. */
	public static BlockPos depot(Faction faction) {
		return faction.holdings().depot().orElse(faction.seat());
	}

	/** A new crew for {@code faction}: a foreman and workers, {@link #CREW_START} in all, at its depot. */
	public static List<Worker> spawnCrew(ServerLevel level, Faction faction) {
		List<Worker> crew = new ArrayList<>();
		for (int i = 0; i < CREW_START; i++) {
			Worker builder = spawn(level, faction, i == 0 ? Folk.FOREMAN : Folk.WORKER, null);
			if (builder != null) {
				crew.add(builder);
			}
		}
		return crew;
	}

	/** One builder for {@code faction} at {@code at} (or by its depot), counted in its roster. */
	public static @Nullable Worker spawn(ServerLevel level, Faction faction, int role, @Nullable BlockPos at) {
		var random = level.getRandom();
		BlockPos depot = depot(faction);
		BlockPos where = at != null ? at
			: level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, depot.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2));
		if (at == null && Math.abs(where.getY() - depot.getY()) > 3) {
			// (inside the storehouse, under its roof)
			where = depot;
		}
		Worker folk = Workers.body(level, faction, role, where, depot, HOME_RADIUS);
		if (folk == null) {
			return null;
		}
		Factions.update(level.getServer(), faction.id(), f -> f.withHoldings(f.holdings().adjust(role, 1)));
		return folk;
	}
}
