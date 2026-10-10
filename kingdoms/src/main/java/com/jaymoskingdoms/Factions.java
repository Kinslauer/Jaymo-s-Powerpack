package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Every faction in the world (kingdoms, fiefdoms, villages, barbarian camps), kept on the Overworld (saved with it).
 * The one place factions are read and written. All their places are in the Overworld.
 */
public final class Factions {
	/** All factions, fallen ones included (their story isn't over: a ruin can be refounded). Same id as 0.2's kingdoms. */
	static final AttachmentType<List<Faction>> ALL = AttachmentRegistry.create(JaymosKingdoms.id("kingdoms"),
		builder -> builder.persistent(Faction.CODEC.listOf()));

	/** Each realm's keep (faction id → where its leader and guards belong). */
	static final AttachmentType<Map<String, Castle.Keep>> KEEPS = AttachmentRegistry.create(JaymosKingdoms.id("keeps"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Castle.Keep.CODEC)));

	private Factions() {
	}

	public static Castle.@Nullable Keep keep(MinecraftServer server, int id) {
		Map<String, Castle.Keep> keeps = store(server).getAttached(KEEPS);
		return keeps == null ? null : keeps.get(Integer.toString(id));
	}

	static void putKeep(MinecraftServer server, int id, Castle.Keep keep) {
		Map<String, Castle.Keep> keeps = new HashMap<>(store(server).getAttachedOrElse(KEEPS, Map.of()));
		keeps.put(Integer.toString(id), keep);
		store(server).setAttached(KEEPS, Map.copyOf(keeps));
	}

	private static ServerLevel store(MinecraftServer server) {
		return server.overworld();
	}

	/** The level every faction lives in. */
	public static ServerLevel level(MinecraftServer server) {
		return server.overworld();
	}

	public static List<Faction> all(MinecraftServer server) {
		List<Faction> all = store(server).getAttached(ALL);
		return all == null ? List.of() : all;
	}

	/** Every faction of {@code type}. */
	public static List<Faction> all(MinecraftServer server, FactionType type) {
		return all(server).stream().filter(f -> f.type() == type).toList();
	}

	public static @Nullable Faction get(MinecraftServer server, int id) {
		for (Faction f : all(server)) {
			if (f.id() == id) {
				return f;
			}
		}
		return null;
	}

	public static @Nullable Faction byName(MinecraftServer server, String name) {
		for (Faction f : all(server)) {
			if (f.name().equalsIgnoreCase(name)) {
				return f;
			}
		}
		return null;
	}

	/** Adds or replaces (by id). */
	public static void put(MinecraftServer server, Faction faction) {
		List<Faction> all = new ArrayList<>(all(server));
		all.removeIf(f -> f.id() == faction.id());
		all.add(faction);
		all.sort(Comparator.comparingInt(Faction::id));
		store(server).setAttached(ALL, List.copyOf(all));
	}

	/** Re-reads faction {@code id}, changes it and stores it back; returns the stored result (null if there's none). */
	public static @Nullable Faction update(MinecraftServer server, int id, UnaryOperator<Faction> change) {
		Faction faction = get(server, id);
		if (faction == null) {
			return null;
		}
		Faction changed = change.apply(faction);
		put(server, changed);
		return changed;
	}

	static int nextId(MinecraftServer server) {
		return all(server).stream().mapToInt(Faction::id).max().orElse(0) + 1;
	}

	/** The faction whose land this is (Overworld only), if any. */
	public static @Nullable Faction at(MinecraftServer server, Level level, BlockPos pos) {
		if (level != store(server)) {
			return null;
		}
		ChunkPos chunk = ChunkPos.containing(pos);
		for (Faction f : all(server)) {
			if (f.claims(chunk)) {
				return f;
			}
		}
		return null;
	}

	/**
	 * The faction {@code pos} belongs to: the one whose land it's in or, failing that, the standing faction whose seat
	 * is within {@code radius} (a village inside a kingdom's land has a seat but no land of its own).
	 */
	public static @Nullable Faction around(MinecraftServer server, Level level, BlockPos pos, double radius) {
		if (level != store(server)) {
			return null;
		}
		Faction best = null;
		double bestDist = radius * radius;
		for (Faction f : all(server)) {
			double d = f.seat().distSqr(pos);
			if (!f.fallen() && d <= bestDist) {
				best = f;
				bestDist = d;
			}
		}
		return best != null ? best : at(server, level, pos);
	}

	/** The faction with a seat near {@code pos} (within {@code radius}), if any. */
	static @Nullable Faction near(MinecraftServer server, BlockPos pos, double radius) {
		for (Faction f : all(server)) {
			if (f.seat().distSqr(pos) <= radius * radius) {
				return f;
			}
		}
		return null;
	}

	/** A realm (kingdom or fiefdom) with a seat near {@code pos}, if any. */
	static @Nullable Faction realmNear(MinecraftServer server, BlockPos pos, double radius) {
		for (Faction f : all(server)) {
			if (f.type().isRealm() && f.seat().distSqr(pos) <= radius * radius) {
				return f;
			}
		}
		return null;
	}

	/** The faction {@code player} is sworn to, if any. */
	public static @Nullable Faction of(MinecraftServer server, UUID player) {
		for (Faction f : all(server)) {
			if (!f.fallen() && f.members().containsKey(player)) {
				return f;
			}
		}
		return null;
	}

	/** The faction a storage container at {@code pos} belongs to, if any (for theft and permissions). */
	public static @Nullable Faction storageOwner(MinecraftServer server, BlockPos pos) {
		for (Faction f : all(server)) {
			if (f.holdings().storage().contains(pos)) {
				return f;
			}
		}
		return null;
	}

	/**
	 * Brings saves from before 0.3 up to date: a vassal kingdom becomes a fiefdom (allied to its overlord); a realm
	 * that never kept a roster gets one from what 0.2 spawned (its guards and its archers' posts).
	 */
	static void migrate(MinecraftServer server) {
		for (Faction old : all(server)) {
			// (re-read: an earlier one may have changed it)
			Faction f = get(server, old.id());
			Faction m = f;
			if (m.type() == FactionType.KINGDOM && m.overlord() >= 0) {
				m = m.withType(FactionType.FIEFDOM).withRelation(m.overlord(), Relation.ALLIED);
				int overlord = m.overlord();
				int id = m.id();
				update(server, overlord, o -> o.withRelation(id, Relation.ALLIED));
			}
			if (m.type().isRealm() && !m.fallen() && m.holdings().roster().isEmpty()) {
				Castle.Keep keep = keep(server, m.id());
				Faction.Holdings h = m.holdings().adjust(Folk.GUARD, KingdomEvents.GUARDS);
				if (keep != null && !keep.posts().isEmpty()) {
					h = h.adjust(Folk.ARCHER, keep.posts().size());
				}
				m = m.withHoldings(h);
			}
			if (m != f) {
				JaymosKingdoms.LOGGER.info("Migrated {} to 0.3 ({})", m.name(), m.type().id());
				put(server, m);
			}
		}
	}
}
