package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * What time remembers about a Chrono player: saved on the player, kept through death, dropped with the power.
 * <ul>
 * <li>habits (the blocks they mine and place most, the items they hold most, a ring of recent walked positions), which
 * their echoes repeat;</li>
 * <li>the echo timers: echoes still to come (one per level-up) and shattered echoes waiting to return;</li>
 * <li>how long they've spent in stopped or rewound time (the road to ascension);</li>
 * <li>the bond: a random number every echo is stamped with, so echoes of an old bond (the power was lost) fade.</li>
 * </ul>
 * Mutable on purpose: the server edits it in place (player data is written whole on every save).
 */
public final class ChronoMemory {
	/** Most block/item kinds remembered per list; the least used one is forgotten to make room. */
	static final int MAX_KINDS = 16;
	/** Walked positions kept (oldest dropped). At one sample per 2 s of walking, about 3 minutes of routes. */
	static final int MAX_PATH_POINTS = 96;

	public record PathPoint(BlockPos pos, ResourceKey<Level> dimension) {
		static final Codec<PathPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(PathPoint::pos),
				Level.RESOURCE_KEY_CODEC.fieldOf("dim").forGetter(PathPoint::dimension))
			.apply(i, PathPoint::new));
	}

	private static final Codec<Map<Identifier, Integer>> COUNTS = Codec.unboundedMap(Identifier.CODEC, Codec.INT);

	public static final Codec<ChronoMemory> CODEC = RecordCodecBuilder.create(i -> i.group(
			COUNTS.optionalFieldOf("mined", Map.of()).forGetter(m -> m.mined),
			COUNTS.optionalFieldOf("placed", Map.of()).forGetter(m -> m.placed),
			COUNTS.optionalFieldOf("held", Map.of()).forGetter(m -> m.held),
			PathPoint.CODEC.listOf().optionalFieldOf("path", List.of()).forGetter(m -> m.path),
			Codec.INT.listOf().optionalFieldOf("echoes_due", List.of()).forGetter(m -> m.echoesDue),
			Codec.INT.listOf().optionalFieldOf("returns_due", List.of()).forGetter(m -> m.returnsDue),
			Codec.INT.optionalFieldOf("stopped_time", 0).forGetter(m -> m.stoppedTime),
			Codec.BOOL.optionalFieldOf("sighted", false).forGetter(m -> m.sighted),
			Codec.LONG.optionalFieldOf("bond", 0L).forGetter(m -> m.bond))
		.apply(i, ChronoMemory::new));

	public static final AttachmentType<ChronoMemory> MEMORY = AttachmentRegistry.create(JaymosPowers.id("chrono_memory"),
		builder -> builder.persistent(CODEC).copyOnDeath());

	final Map<Identifier, Integer> mined;
	final Map<Identifier, Integer> placed;
	final Map<Identifier, Integer> held;
	final List<PathPoint> path;
	/** Survival ticks until each pending echo first appears. */
	final List<Integer> echoesDue;
	/** Survival ticks until each shattered echo comes back. */
	final List<Integer> returnsDue;
	/** Ticks spent with Stasis running or rewound by Rewind. */
	int stoppedTime;
	/** Whether the owner has already been whispered to about their first echo. */
	boolean sighted;
	long bond;

	private ChronoMemory(Map<Identifier, Integer> mined, Map<Identifier, Integer> placed, Map<Identifier, Integer> held, List<PathPoint> path,
		List<Integer> echoesDue, List<Integer> returnsDue, int stoppedTime, boolean sighted, long bond) {
		this.mined = new HashMap<>(mined);
		this.placed = new HashMap<>(placed);
		this.held = new HashMap<>(held);
		this.path = new ArrayList<>(path);
		this.echoesDue = new ArrayList<>(echoesDue);
		this.returnsDue = new ArrayList<>(returnsDue);
		this.stoppedTime = stoppedTime;
		this.sighted = sighted;
		this.bond = bond == 0L ? newBond() : bond;
	}

	private ChronoMemory() {
		this(Map.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(), 0, false, 0L);
	}

	private static long newBond() {
		long bond = 0L;
		while (bond == 0L) {
			bond = new Random().nextLong();
		}
		return bond;
	}

	/** The player's memory, created on first use. */
	public static ChronoMemory of(ServerPlayer player) {
		return player.getAttachedOrCreate(MEMORY, ChronoMemory::new);
	}

	public static @Nullable ChronoMemory peek(ServerPlayer player) {
		return player.getAttached(MEMORY);
	}

	/** The power was lost: forget everything (pending echoes too; a new bond comes with a new memory). */
	static void forget(ServerPlayer player) {
		player.removeAttached(MEMORY);
	}

	// ---------------------------------------------------------------- habits

	static void count(Map<Identifier, Integer> counts, Identifier id) {
		Integer now = counts.get(id);
		if (now == null && counts.size() >= MAX_KINDS) {
			Identifier least = null;
			int leastCount = Integer.MAX_VALUE;
			for (Map.Entry<Identifier, Integer> e : counts.entrySet()) {
				if (e.getValue() < leastCount) {
					leastCount = e.getValue();
					least = e.getKey();
				}
			}
			counts.remove(least);
		}
		counts.put(id, now == null ? 1 : Math.min(now + 1, 1_000_000));
	}

	/** The {@code n} most counted ids, most first. */
	static List<Identifier> top(Map<Identifier, Integer> counts, int n) {
		List<Map.Entry<Identifier, Integer>> entries = new ArrayList<>(counts.entrySet());
		entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		List<Identifier> out = new ArrayList<>(n);
		for (int k = 0; k < entries.size() && k < n; k++) {
			out.add(entries.get(k).getKey());
		}
		return out;
	}

	void addPathPoint(BlockPos pos, ResourceKey<Level> dimension) {
		path.add(new PathPoint(pos.immutable(), dimension));
		while (path.size() > MAX_PATH_POINTS) {
			path.remove(0);
		}
	}

	@Nullable PathPoint lastPathPoint() {
		return path.isEmpty() ? null : path.get(path.size() - 1);
	}

	/** The walked positions in {@code dimension}, oldest first. */
	List<BlockPos> pathIn(ResourceKey<Level> dimension) {
		List<BlockPos> out = new ArrayList<>();
		for (PathPoint p : path) {
			if (p.dimension() == dimension) {
				out.add(p.pos());
			}
		}
		return out;
	}
}
