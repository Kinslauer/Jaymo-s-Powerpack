package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * Relations between factions: allied, neutral, rival or at war, kept on both sides. A vassal and its overlord are
 * allied; a barbarian camp is at war with everyone else (implicitly: it's never stored, and doesn't stop anyone being
 * {@link Economy#atPeace at peace}).
 */
public final class Relations {
	private Relations() {
	}

	/** How {@code a} stands with {@code b}. */
	public static Relation between(Faction a, Faction b) {
		if (a.id() == b.id()) {
			return Relation.ALLIED;
		}
		if (a.type() == FactionType.BARBARIAN || b.type() == FactionType.BARBARIAN) {
			return a.type() == b.type() ? a.relation(b.id()) : Relation.WAR;
		}
		if (a.overlord() == b.id() || b.overlord() == a.id()) {
			Relation stored = a.relation(b.id());
			return stored == Relation.NEUTRAL ? Relation.ALLIED : stored;
		}
		return a.relation(b.id());
	}

	public static Relation between(MinecraftServer server, int a, int b) {
		Faction fa = Factions.get(server, a);
		Faction fb = Factions.get(server, b);
		return fa == null || fb == null ? Relation.NEUTRAL : between(fa, fb);
	}

	/** Sets the relation on both sides. */
	public static void set(MinecraftServer server, int a, int b, Relation relation) {
		if (a == b) {
			return;
		}
		Factions.update(server, a, f -> f.withRelation(b, relation));
		Factions.update(server, b, f -> f.withRelation(a, relation));
	}

	public static boolean atWar(Faction a, Faction b) {
		return between(a, b) == Relation.WAR;
	}

	/** {@code vassal} is (directly) a vassal of {@code overlord}. */
	public static boolean isVassal(Faction vassal, Faction overlord) {
		return vassal.overlord() == overlord.id();
	}

	/** The factions {@code faction} has stored a war with. */
	public static List<Faction> enemies(MinecraftServer server, Faction faction) {
		List<Faction> out = new ArrayList<>();
		for (var entry : faction.relations().entrySet()) {
			if (entry.getValue() == Relation.WAR) {
				Faction other = Factions.get(server, Integer.parseInt(entry.getKey()));
				if (other != null && !other.fallen()) {
					out.add(other);
				}
			}
		}
		return out;
	}

	/** Makes {@code vassal} a fiefdom of {@code overlord} (allied with it). */
	public static void vassalize(MinecraftServer server, int vassal, int overlord) {
		Factions.update(server, vassal, f -> f.withType(f.type() == FactionType.KINGDOM ? FactionType.FIEFDOM : f.type()).withOverlord(overlord));
		set(server, vassal, overlord, Relation.ALLIED);
	}

	/** The overlord of {@code faction}, if it has one. */
	public static @Nullable Faction overlord(MinecraftServer server, Faction faction) {
		return faction.overlord() < 0 ? null : Factions.get(server, faction.overlord());
	}
}
