package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.codex.Scans;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * What the wearer has studied, for the wrist table's list of enemy kinds. Two sources count: the Scrying Lens reading
 * of a creature ({@link Scans}, which keeps an entry for each essence of "entity:minecraft:zombie"), and the plain
 * habit of fighting it, kept here as a count of kills for each kind ({@link #KILLS}). A kind is studied once the Lens
 * has read it or {@link #STUDY_KILLS} of it have fallen to the wearer.
 */
public final class RigStudy {
	/** Kills of one kind that count as studying it. */
	public static final int STUDY_KILLS = 5;
	/** The longest list the table is sent, and the most kills counted for one kind. */
	public static final int LIST_MAX = 80, KILLS_CAP = 9999;

	/** On the player: kills of each creature kind (its registry id), kept through death. */
	public static final AttachmentType<Map<String, Integer>> KILLS = AttachmentRegistry.create(JaymosPowers.id("rig_study_kills"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath().initializer(() -> Map.of()));

	/** One studied kind: its id, the kills counted, and whether the Lens has read it. */
	public record Entry(String id, int kills, boolean lens) {
	}

	private RigStudy() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer player) || entity instanceof Player) {
				return;
			}
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
			Map<String, Integer> now = new HashMap<>(player.getAttachedOrCreate(KILLS));
			now.merge(id, 1, (a, b) -> Math.min(KILLS_CAP, a + b));
			player.setAttached(KILLS, Map.copyOf(now));
		});
	}

	/** The studied kinds, most fought first (server). */
	public static List<Entry> studied(ServerPlayer player) {
		Map<String, Integer> kills = player.getAttachedOrCreate(KILLS);
		Set<String> scanned = player.getAttachedOrCreate(Scans.SCANNED);
		Map<String, Boolean> lens = new HashMap<>();
		for (String entry : scanned) {
			if (entry.startsWith("entity:")) {
				int slash = entry.indexOf('/');
				lens.put(entry.substring("entity:".length(), slash < 0 ? entry.length() : slash), true);
			}
		}
		Map<String, Entry> all = new HashMap<>();
		for (Map.Entry<String, Integer> kill : kills.entrySet()) {
			if (kill.getValue() >= STUDY_KILLS || lens.containsKey(kill.getKey())) {
				all.put(kill.getKey(), new Entry(kill.getKey(), kill.getValue(), lens.containsKey(kill.getKey())));
			}
		}
		for (String id : lens.keySet()) {
			all.putIfAbsent(id, new Entry(id, kills.getOrDefault(id, 0), true));
		}
		List<Entry> list = new ArrayList<>(all.values());
		list.sort((a, b) -> b.kills != a.kills ? Integer.compare(b.kills, a.kills) : a.id.compareTo(b.id));
		return list.size() > LIST_MAX ? new ArrayList<>(list.subList(0, LIST_MAX)) : list;
	}
}
