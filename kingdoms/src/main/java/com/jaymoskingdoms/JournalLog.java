package com.jaymoskingdoms;

import com.jaymoskingdoms.films.BossIntros;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * What the traveller's journal ({@link Journal}) fills in by itself as you go, beside the quests it already writes up:
 * the people you have been introduced to ({@link Introductions}: every name asked or told), the great foes whose entrance
 * you have witnessed ({@link BossIntros}), and the structures you have walked into. Each is one entry on the quests
 * page; nothing is shown anywhere else, and the player does nothing to fill it.
 */
final class JournalLog {
	// ---- tuning ----
	/** How often (ticks) the structures under a player and the faces about them are looked at. */
	static final int STRUCTURE_EVERY = 40, PEOPLE_EVERY = 100;
	/** How far (blocks) the faces about a player are looked at, and the most people and places the journal remembers. */
	static final double PEOPLE_RANGE = 16.0;
	static final int MAX_PEOPLE = 200, MAX_PLACES = 64;
	/** A structure of the same kind within this many blocks of one already written is the same place. */
	static final double SAME_PLACE = 160.0;
	/** The most names listed in one entry before it says how many more there are. */
	static final int LISTED = 24;

	/** On a player: the people whose names they know, id to name. Saved, kept through death. */
	static final AttachmentType<Map<String, String>> PEOPLE = AttachmentRegistry.create(JaymosKingdoms.id("journal_people"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)).copyOnDeath());
	/** On a player: the structures walked into, oldest first, each {@code id|x|y|z}. Saved, kept through death. */
	static final AttachmentType<List<String>> PLACES = AttachmentRegistry.create(JaymosKingdoms.id("journal_places"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	private JournalLog() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("journal log attachments: {} {}", PEOPLE, PLACES);
		ServerTickEvents.END_SERVER_TICK.register(JournalLog::tick);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			if (player.tickCount % STRUCTURE_EVERY == 0) {
				structures(player);
			}
			if (player.tickCount % PEOPLE_EVERY == 0) {
				faces(player);
			}
		}
	}

	// ---- people ----

	/** {@code player} has been told {@code who}'s name: it goes in the journal. */
	static void person(ServerPlayer player, Entity who) {
		if (who == player || who instanceof ServerPlayer other && Identities.maskedAs(other) != null) {
			return;
		}
		String name = who.getName().getString();
		if (name.isBlank()) {
			return;
		}
		Map<String, String> people = player.getAttachedOrElse(PEOPLE, Map.of());
		String id = who.getUUID().toString();
		if (name.equals(people.get(id)) || people.size() >= MAX_PEOPLE && !people.containsKey(id)) {
			return;
		}
		// a new map, so it is saved
		Map<String, String> next = new LinkedHashMap<>(people);
		next.put(id, name);
		player.setAttached(PEOPLE, next);
	}

	/** The people about {@code player} whose names they already knew before the journal kept them. */
	private static void faces(ServerPlayer player) {
		Set<UUID> known = player.getAttached(Introductions.KNOWN);
		if (known == null || known.isEmpty()) {
			return;
		}
		for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(PEOPLE_RANGE), e -> Introductions.named(e) && known.contains(e.getUUID()))) {
			person(player, e);
		}
	}

	// ---- structures ----

	private static void structures(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (Structure structure : level.structureManager().getAllStructuresAt(at).keySet()) {
			StructureStart start = level.structureManager().getStructureWithPieceAt(at.getX(), at.getY(), at.getZ(), structure);
			if (start == null || !start.isValid()) {
				continue;
			}
			Identifier id = registry.getKey(structure);
			if (id == null || id.getNamespace().equals(JaymosKingdoms.MOD_ID)) {
				// (our own settlements are written under the map)
				continue;
			}
			note(player, id.toString(), at);
		}
	}

	private static void note(ServerPlayer player, String id, BlockPos at) {
		List<String> places = player.getAttachedOrElse(PLACES, List.of());
		for (String p : places) {
			String[] parts = p.split("\\|");
			if (parts.length == 4 && parts[0].equals(id)
				&& at.distSqr(new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))) < SAME_PLACE * SAME_PLACE) {
				return;
			}
		}
		List<String> next = new ArrayList<>(places);
		next.add(id + "|" + at.getX() + "|" + at.getY() + "|" + at.getZ());
		while (next.size() > MAX_PLACES) {
			next.remove(0);
		}
		player.setAttached(PLACES, next);
	}

	// ---- the journal ----

	/** "ogre_king" as "Ogre King". */
	private static String pretty(String path) {
		StringBuilder out = new StringBuilder();
		for (String word : path.split("[_/]")) {
			if (word.isEmpty()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
		}
		return out.toString();
	}

	private static Component list(List<String> names, Map<String, Integer> counts) {
		StringBuilder out = new StringBuilder();
		int shown = 0;
		for (String name : names) {
			if (shown == LISTED) {
				break;
			}
			if (shown > 0) {
				out.append(", ");
			}
			out.append(name);
			int count = counts.getOrDefault(name, 1);
			if (count > 1) {
				out.append(" x").append(count);
			}
			shown++;
		}
		Component text = Component.literal(out.toString());
		return names.size() > shown ? Component.translatable("journal.jaymoskingdoms.log.more", text, names.size() - shown) : text;
	}

	/** {@code player}'s people, great foes and places, as journal entries (the Quests page). */
	static List<Journal.Entry> journal(ServerPlayer player) {
		List<Journal.Entry> out = new ArrayList<>();
		Component giver = Component.translatable("journal.jaymoskingdoms.log.giver");
		Map<String, String> people = player.getAttachedOrElse(PEOPLE, Map.of());
		if (!people.isEmpty()) {
			Set<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			sorted.addAll(people.values());
			List<String> names = new ArrayList<>(sorted);
			out.add(new Journal.Entry(false, false, Component.translatable("journal.jaymoskingdoms.log.people"), giver, list(names, Map.of()),
				Component.translatable("journal.jaymoskingdoms.log.people.objective", people.size()), Optional.empty()));
		}
		List<String> seen = BossIntros.seen(player);
		if (!seen.isEmpty()) {
			Map<String, Integer> kinds = new LinkedHashMap<>();
			for (String key : seen) {
				int colon = key.indexOf(':');
				String path = key.substring(colon + 1);
				int slash = path.indexOf('/');
				kinds.merge(pretty(slash < 0 ? path : path.substring(0, slash)), 1, Integer::sum);
			}
			// (kinds told apart by their patron or rank are one name here)
			kinds.replaceAll((name, count) -> 1);
			out.add(new Journal.Entry(false, false, Component.translatable("journal.jaymoskingdoms.log.foes"), giver, list(new ArrayList<>(kinds.keySet()), kinds),
				Component.translatable("journal.jaymoskingdoms.log.foes.objective"), Optional.empty()));
		}
		List<String> places = player.getAttachedOrElse(PLACES, List.of());
		if (!places.isEmpty()) {
			Map<String, Integer> kinds = new LinkedHashMap<>();
			BlockPos last = null;
			for (String p : places) {
				String[] parts = p.split("\\|");
				if (parts.length != 4) {
					continue;
				}
				String id = parts[0];
				kinds.merge(pretty(id.substring(id.indexOf(':') + 1)), 1, Integer::sum);
				last = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
			}
			if (!kinds.isEmpty()) {
				out.add(new Journal.Entry(false, false, Component.translatable("journal.jaymoskingdoms.log.places"), giver, list(new ArrayList<>(kinds.keySet()), kinds),
					Component.translatable("journal.jaymoskingdoms.log.places.objective"), Optional.ofNullable(last)));
			}
		}
		return out;
	}
}
