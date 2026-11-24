package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * A night when anger spills over. When the mood of a realm's people (MCA's mood, the mean of the villagers loaded in it) has
 * sunk to {@link #ANGRY_MOOD} or lower, a night may come ({@link #CHANCE}, and never twice within {@link #COOLDOWN_DAYS}) when
 * it boils over: through the night windows are smashed in the lanes (at most {@link #MAX_WINDOWS}), old scores are settled
 * (up to {@link #MAX_SCORES} pairs with the worst regard for each other fall on one another, {@link Neighbours#ambush}), and
 * the guards look away ({@link #looksAway}: they don't break the fights up, {@link Neighbours}). At dawn the venting leaves
 * everyone a little lighter ({@link #RELIEF}) and the village has it to talk of ({@link #talk}). Without MCA there is no mood,
 * so it never happens.
 */
public final class Unrest {
	// ---- tuning ----
	/** The mean mood (MCA, -15 to 15) at or under which a night can boil over, how many villagers it takes to judge, the chance each such night, days between. */
	static final int ANGRY_MOOD = -6, PEOPLE_LEAST = 5;
	static final float CHANCE = 0.15F;
	static final long COOLDOWN_DAYS = 10L;
	/** How often (ticks) the night goes on, the most windows and scores in one night, and a score's pair: regard at most this, within this many blocks. */
	static final int EVERY = 100, MAX_WINDOWS = 12, MAX_SCORES = 3;
	static final int GRUDGE = -15;
	static final double SCORE_RANGE = 16.0, WINDOW_REACH = 6.0;
	/** Mood each one regains at dawn, and for how many days after the village still talks of it. */
	static final int RELIEF = 3;
	static final long TALK_DAYS = 3L;
	static final int LINES = 4;

	/** Realm id → the last day it boiled over. On the overworld. */
	static final AttachmentType<Map<String, Long>> LAST = AttachmentRegistry.create(JaymosKingdoms.id("unrest_last"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	/** Realm id → the night it is boiling over, and what's been done that night ({windows, scores}). */
	private static final Map<Integer, Long> ACTIVE = new HashMap<>(), DECIDED = new HashMap<>();
	private static final Map<Integer, int[]> DONE = new HashMap<>();

	private Unrest() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Unrest::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			DECIDED.clear();
			DONE.clear();
		});
	}

	/** Whether the guards of realm {@code faction} are looking away tonight (for {@link Neighbours}). */
	static boolean looksAway(int faction) {
		return !ACTIVE.isEmpty() && ACTIVE.containsKey(faction);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		long clock = level.getOverworldClockTime();
		long night = Vampires.nightIndex(clock);
		boolean dark = Vampires.night(clock);
		if (!ACTIVE.isEmpty()) {
			for (Integer id : new ArrayList<>(ACTIVE.keySet())) {
				Faction f = Factions.get(server, id);
				if (f == null || f.fallen() || !dark || ACTIVE.get(id) != night) {
					dawn(level, f, id, clock);
				} else {
					goOn(level, f, now);
				}
			}
		}
		if (!dark) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if ((f.type() != FactionType.VILLAGE && f.type() != FactionType.KINGDOM) || f.fallen() || DECIDED.getOrDefault(f.id(), -1L) == night
				|| !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			DECIDED.put(f.id(), night);
			if (boils(level, f, clock)) {
				ACTIVE.put(f.id(), night);
				DONE.put(f.id(), new int[2]);
				JaymosKingdoms.LOGGER.info("{} boils over tonight", f.name());
			}
		}
	}

	/** The mean mood of {@code f}'s villagers (null when too few are loaded to say). */
	static @Nullable Double mood(ServerLevel level, Faction f) {
		List<Villager> people = Villages.people(level, f).stream().filter(v -> !v.isBaby()).toList();
		if (people.size() < PEOPLE_LEAST) {
			return null;
		}
		double sum = 0.0;
		for (Villager v : people) {
			sum += McaCompat.mood(v);
		}
		return sum / people.size();
	}

	private static boolean boils(ServerLevel level, Faction f, long clock) {
		Double mean = mood(level, f);
		if (mean == null || mean > ANGRY_MOOD) {
			return false;
		}
		long day = Math.floorDiv(clock, 24000L);
		Long last = level.getServer().overworld().getAttachedOrElse(LAST, Map.<String, Long>of()).get(Integer.toString(f.id()));
		return (last == null || day - last >= COOLDOWN_DAYS) && level.getRandom().nextFloat() < CHANCE;
	}

	/** A step of the night: a window or two broken, and (while there are scores left) a pair falls on each other. */
	private static void goOn(ServerLevel level, Faction f, long now) {
		RandomSource random = level.getRandom();
		int[] done = DONE.computeIfAbsent(f.id(), id -> new int[2]);
		List<Villager> people = new ArrayList<>(Villages.people(level, f).stream().filter(v -> !v.isBaby() && v.isAlive()).toList());
		if (people.isEmpty()) {
			return;
		}
		Villager shouter = people.get(random.nextInt(people.size()));
		if (done[0] < MAX_WINDOWS && smash(level, shouter)) {
			done[0]++;
			Speech.say(level, shouter, Component.translatable("speech.jaymoskingdoms.unrest." + random.nextInt(LINES)), Speech.RED, 24.0);
		}
		if (done[1] < MAX_SCORES) {
			for (Villager a : people) {
				if (Neighbours.meeting(a) || Downed.isDowned(a)) {
					continue;
				}
				Villager worst = null;
				int regard = GRUDGE + 1;
				for (Villager b : people) {
					if (b != a && !Neighbours.meeting(b) && !Downed.isDowned(b) && a.distanceTo(b) <= SCORE_RANGE) {
						int r = Neighbours.regard(a, b);
						if (r < regard) {
							regard = r;
							worst = b;
						}
					}
				}
				if (worst != null) {
					Neighbours.ambush(level, f.id(), a, worst, now);
					done[1]++;
					break;
				}
			}
		}
	}

	/** Breaks one glass block near {@code v} (a window, a pane): false when none is near. */
	private static boolean smash(ServerLevel level, Villager v) {
		List<BlockPos> glass = new ArrayList<>();
		BlockPos c = v.blockPosition();
		int r = (int)WINDOW_REACH;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 3, r))) {
			String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
			if (path.endsWith("glass") || path.endsWith("glass_pane")) {
				glass.add(p.immutable());
			}
		}
		if (glass.isEmpty()) {
			return false;
		}
		glass.sort(Comparator.comparingDouble(p -> p.distSqr(c)));
		BlockPos pick = glass.get(level.getRandom().nextInt(Math.min(glass.size(), 6)));
		level.destroyBlock(pick, false, v);
		return true;
	}

	/** Dawn: the night is over; they're lighter for it, and the realm remembers the day. */
	private static void dawn(ServerLevel level, @Nullable Faction f, int id, long clock) {
		ACTIVE.remove(id);
		DONE.remove(id);
		if (f == null) {
			return;
		}
		for (Villager v : Villages.people(level, f)) {
			McaCompat.cheer(v, RELIEF);
		}
		ServerLevel overworld = level.getServer().overworld();
		Map<String, Long> last = new HashMap<>(overworld.getAttachedOrElse(LAST, Map.<String, Long>of()));
		last.put(Integer.toString(id), Math.floorDiv(clock, 24000L));
		overworld.setAttached(LAST, Map.copyOf(last));
	}

	/** What a realm says of a night that boiled over, for a few days after; else null. */
	static @Nullable Component talk(ServerLevel level, Faction f, RandomSource random) {
		Long last = level.getServer().overworld().getAttachedOrElse(LAST, Map.<String, Long>of()).get(Integer.toString(f.id()));
		long day = Math.floorDiv(level.getServer().overworld().getOverworldClockTime(), 24000L);
		if (last == null || day - last > TALK_DAYS) {
			return null;
		}
		return Component.translatable("talk.jaymoskingdoms.unrest." + random.nextInt(LINES));
	}
}
