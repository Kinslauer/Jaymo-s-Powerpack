package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Names for the illagers' places, in their own tongue (village mods patch 1, ideas from Village Names; our own words).
 * <ul>
 * <li><b>Camps</b> take a name from the tongue's syllables ({@link #freshName}); <b>every other hold</b> of theirs (pillager outposts,
 * woodland mansions, every Illager Structures place, Overhauled Village's outposts) gets one the first time someone stands in it, kept
 * in a saved map of structure start to name ({@link #HOLDS}).</li>
 * <li><b>Rank by size</b> ({@link #rank}): a lookout, a warband camp, a host or a war seat, said in illager words
 * ({@link #RANK_WORDS}) on the entering message.</li>
 * <li><b>Scrawl until learned</b> ({@link #shown}): a name reads clearly only to one who knows the word (the tongue's points);
 * to everyone else it is a scrawl.</li>
 * <li>A <b>warlord's title</b> ({@link #warlordTitle}): his own name and his camp's, "Grekath of Vrakthul".</li>
 * <li>A <b>muster page</b> ({@link #muster}) read off an illager ledger, a villager's <b>hint</b> of the nearest hold ({@link #hint}),
 * and a <b>fallen camp's name</b> for the Reaper's host ({@link #nearestFallen}).</li>
 * </ul>
 */
public final class IllagerHolds {
	// ---- tuning ----
	/** How often (ticks) players are looked at for entering a hold. */
	static final int CHECK_TICKS = 20;
	/** Rank by size: a camp's fighters, and a hold's longest side (blocks), at which it is a warband camp, a host, a war seat. */
	static final int[] CAMP_SIZES = {8, 14, 20};
	static final int[] HOLD_SIZES = {28, 56, 96};
	/** Their words for the four ranks (lookout, warband camp, host, war seat): gibberish until learned. */
	static final String[] RANK_WORDS = {"ghaesh", "kulvok", "drazek", "morhask"};
	/** How far a villager knows of a hold or camp (blocks), the chance it comes up, and how far an illager ledger's muster reaches. */
	static final double HINT_RANGE = 500.0, MUSTER_RANGE = 600.0;
	static final float HINT_CHANCE = 0.3F;
	/** How many ways a hint is told, and a fallen camp's name in the host's gossip. */
	static final int HINT_LINES = 3, FALLEN_LINES = 3;
	/** A fallen camp is named by the Reaper's host within this far (blocks). */
	static final double FALLEN_RANGE = 600.0;
	/** Names: place names and personal names, built from the tongue's own sounds. */
	private static final String[] PLACE_HEAD = {"Vrak", "Gor", "Zek", "Kul", "Dras", "Mor", "Ush", "Ghar", "Taz", "Vok", "Esh", "Skar"};
	private static final String[] PLACE_TAIL = {"thul", "dun", "kesh", "mar", "goth", "vash", "rok", "uld", "nak"};
	private static final String[] PERSON_HEAD = {"Grek", "Vor", "Ash", "Kar", "Ruk", "Dra", "Zul", "Hask", "Mak", "Ith"};
	private static final String[] PERSON_TAIL = {"ath", "gar", "ek", "oth", "uk", "vak", "ash", "ur"};

	/** A hold named: its name, where it is, and how big (its longest side). */
	public record Entry(String name, BlockPos pos, int size) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Entry::name),
			BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
			Codec.INT.fieldOf("size").forGetter(Entry::size)).apply(i, Entry::new));
	}

	/** On the Overworld: every hold named so far, by structure start ({@code <dim>|<structure>|<start chunk>}). Saved. */
	static final AttachmentType<Map<String, Entry>> HOLDS = AttachmentRegistry.create(JaymosKingdoms.id("illager_holds"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Entry.CODEC)));

	/** A place of theirs a villager or a standard can name: its name, where, and its rank (0 lookout to 3 war seat). */
	record Found(String name, BlockPos pos, int rank, @Nullable Faction camp) {
	}

	/** Which hold each player last stood in (so the message comes once on entering). */
	private static final Map<UUID, String> HERE = new HashMap<>();

	private IllagerHolds() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("illager hold names: {}", HOLDS);
		ServerTickEvents.END_SERVER_TICK.register(IllagerHolds::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HERE.clear());
		IllagerMarks.register();
	}

	// ---- names ----------------------------------------------------------------------------------------------------------

	/** A place name in the tongue ("Vrakthul"). */
	static String placeName(RandomSource random) {
		return PLACE_HEAD[random.nextInt(PLACE_HEAD.length)] + PLACE_TAIL[random.nextInt(PLACE_TAIL.length)];
	}

	/** A person's name in the tongue ("Grekath"). */
	static String personName(RandomSource random) {
		return PERSON_HEAD[random.nextInt(PERSON_HEAD.length)] + PERSON_TAIL[random.nextInt(PERSON_TAIL.length)];
	}

	/** A place name no faction or hold has yet. */
	static String freshName(MinecraftServer server, RandomSource random) {
		Map<String, Entry> holds = server.overworld().getAttachedOrElse(HOLDS, Map.of());
		String name = "";
		for (int tries = 0; tries < 30; tries++) {
			name = placeName(random);
			String taken = name;
			if (Factions.byName(server, name) == null && holds.values().stream().noneMatch(e -> e.name().equals(taken))) {
				return name;
			}
		}
		return name + " " + Factions.nextId(server);
	}

	/** {@code camp}'s warlord's own name: the same every time for the camp, so a replaced warlord keeps it. */
	static String warlordName(Faction camp) {
		return personName(RandomSource.create(camp.id() * 0x9E3779B97F4A7C15L ^ camp.name().hashCode()));
	}

	/** "Grekath of Vrakthul": a warlord's name over his camp. */
	static Component warlordTitle(Faction camp, boolean reaped) {
		return Component.translatable(reaped ? "entity.jaymoskingdoms.reaped_warlord_titled" : "entity.jaymoskingdoms.warlord_titled", warlordName(camp), camp.name());
	}

	// ---- rank and scrawl -------------------------------------------------------------------------------------------------

	/** Rank (0 lookout, 1 warband camp, 2 host, 3 war seat) of something of {@code size} by the steps {@code steps}. */
	static int rank(int size, int[] steps) {
		int rank = 0;
		for (int step : steps) {
			if (size >= step) {
				rank++;
			}
		}
		return rank;
	}

	/** {@code name} as one with {@code points} of the tongue reads it: clear if they know the word, a scrawl if not. */
	static Component shown(String name, int points) {
		return IllagerTongue.knowsWord(name, points) ? Component.literal(name) : Component.literal(name).withStyle(ChatFormatting.OBFUSCATED);
	}

	/** A rank in illager words: the word, and what it means once they know it. */
	static Component rankText(int rank, int points) {
		String word = RANK_WORDS[Math.clamp(rank, 0, RANK_WORDS.length - 1)];
		return IllagerTongue.knowsWord(word, points) ? Component.translatable("illager_names.jaymoskingdoms.rank." + rank)
			: Component.literal(word).withStyle(ChatFormatting.ITALIC);
	}

	/** The entering message for an illager camp (its rank in their words), or null if {@code camp} is not one. */
	static net.minecraft.network.chat.@Nullable MutableComponent campEntering(MinecraftServer server, ServerPlayer player, Faction camp, Component rep) {
		if (!IllagerCamps.is(server, camp.id())) {
			return null;
		}
		int points = IllagerTongue.points(player);
		return Component.translatable("message.jaymoskingdoms.entering_camp", shown(camp.name(), points), rankText(campRank(camp), points), rep);
	}

	static int campRank(Faction camp) {
		return rank(Illagers.fighters(camp), CAMP_SIZES);
	}

	// ---- holds, named on first approach ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			StructureStart start = IllagerTongue.within(level, player.blockPosition());
			String key = start == null ? "" : key(level, start);
			String was = HERE.put(player.getUUID(), key);
			if (start != null && !key.equals(was)) {
				Entry hold = named(level, start, key);
				int points = IllagerTongue.points(player);
				player.sendOverlayMessage(Component.translatable("illager_names.jaymoskingdoms.entering_hold", shown(hold.name(), points),
					rankText(rank(hold.size(), HOLD_SIZES), points)).withStyle(ChatFormatting.GRAY));
			}
			if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
				IllagerMarks.maybeMark(level, player);
			}
		}
	}

	private static String key(ServerLevel level, StructureStart start) {
		Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
		return level.dimension().identifier() + "|" + id + "|" + start.getChunkPos().pack();
	}

	/** {@code start}'s name, given (and kept) the first time it is asked. */
	private static Entry named(ServerLevel level, StructureStart start, String key) {
		ServerLevel overworld = level.getServer().overworld();
		Map<String, Entry> holds = overworld.getAttachedOrElse(HOLDS, Map.of());
		Entry have = holds.get(key);
		if (have != null) {
			return have;
		}
		var box = start.getBoundingBox();
		Entry made = new Entry(freshName(level.getServer(), RandomSource.create(level.getSeed() ^ key.hashCode() * 0x9E3779B97F4A7C15L)),
			new BlockPos(box.getCenter().getX(), box.minY(), box.getCenter().getZ()), Math.max(box.getXSpan(), box.getZSpan()));
		Map<String, Entry> now = new HashMap<>(holds);
		now.put(key, made);
		overworld.setAttached(HOLDS, Map.copyOf(now));
		return made;
	}

	// ---- finding them (for hints, standards, waymarks) -----------------------------------------------------------------------

	/** Every standing illager camp and every named hold on the Overworld, with its rank. */
	static List<Found> all(MinecraftServer server) {
		List<Found> found = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && IllagerCamps.is(server, f.id())) {
				found.add(new Found(f.name(), f.seat(), campRank(f), f));
			}
		}
		for (Entry e : server.overworld().getAttachedOrElse(HOLDS, Map.<String, Entry>of()).values()) {
			found.add(new Found(e.name(), e.pos(), rank(e.size(), HOLD_SIZES), null));
		}
		return found;
	}

	/** The nearest of them to {@code from} within {@code range} (horizontal), further than {@code least}, or null. */
	static @Nullable Found nearest(MinecraftServer server, Vec3 from, double range, double least) {
		Found best = null;
		double bestD = range * range;
		for (Found f : all(server)) {
			double dx = f.pos().getX() - from.x, dz = f.pos().getZ() - from.z;
			double d = dx * dx + dz * dz;
			if (d < bestD && d >= least * least) {
				bestD = d;
				best = f;
			}
		}
		return best;
	}

	/** The nearest fallen illager camp to {@code from} within {@link #FALLEN_RANGE}, or null. */
	static @Nullable Faction nearestFallen(MinecraftServer server, BlockPos from) {
		Faction best = null;
		double bestD = FALLEN_RANGE * FALLEN_RANGE;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() && IllagerCamps.KIND.equals(Camps.kind(server, f.id()))) {
				double dx = f.seat().getX() - from.getX(), dz = f.seat().getZ() - from.getZ();
				double d = dx * dx + dz * dz;
				if (d < bestD) {
					bestD = d;
					best = f;
				}
			}
		}
		return best;
	}

	/**
	 * What a villager says of the nearest illager hold or camp ({@code teller} knows of one within {@link #HINT_RANGE}): its name
	 * and, roughly, where. Null if none is near or it doesn't come up. Sets {@code where[0]} to the place it points at.
	 */
	static @Nullable Component hint(ServerLevel level, net.minecraft.world.entity.Entity teller, BlockPos[] where) {
		if (level.getRandom().nextFloat() >= HINT_CHANCE) {
			return null;
		}
		Found f = nearest(level.getServer(), teller.position(), HINT_RANGE, 0.0);
		if (f == null) {
			return null;
		}
		where[0] = f.pos();
		return Component.translatable("illager_names.jaymoskingdoms.hint." + level.getRandom().nextInt(HINT_LINES), f.name(), Rumours.words(teller.position(), f.pos()));
	}

	// ---- the muster page ----------------------------------------------------------------------------------------------------

	/**
	 * The page an illager ledger gives {@code player}: the nearest illager camp's name, size, warlord and next target (what
	 * it is mustering against), as they read it; or that the ledger names none near.
	 */
	static void muster(ServerPlayer player) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		Faction best = null;
		double bestD = MUSTER_RANGE * MUSTER_RANGE;
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && IllagerCamps.is(server, f.id())) {
				double dx = f.seat().getX() - player.getX(), dz = f.seat().getZ() - player.getZ();
				double d = dx * dx + dz * dz;
				if (d < bestD) {
					bestD = d;
					best = f;
				}
			}
		}
		if (best == null) {
			player.sendSystemMessage(Component.translatable("illager_names.jaymoskingdoms.muster.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		int points = IllagerTongue.points(player);
		Faction target = Raids.target(server, server.overworld(), best);
		player.sendSystemMessage(Component.translatable("illager_names.jaymoskingdoms.muster.title", shown(best.name(), points)).withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("illager_names.jaymoskingdoms.muster.size", Illagers.fighters(best), rankText(campRank(best), points)));
		player.sendSystemMessage(Component.translatable("illager_names.jaymoskingdoms.muster.warlord", shown(warlordName(best), points)));
		player.sendSystemMessage(target == null ? Component.translatable("illager_names.jaymoskingdoms.muster.no_target")
			: Component.translatable("illager_names.jaymoskingdoms.muster.target", target.name()));
	}
}
