package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.Locale;

/**
 * How one faction stands with another (kept on both sides, see {@link Relations}). A vassal and its overlord are
 * allied; barbarians are at war with everyone (implicitly, never stored).
 */
public enum Relation {
	ALLIED, NEUTRAL, RIVAL, WAR;

	public static final Codec<Relation> CODEC = Codec.STRING.xmap(Relation::parse, Relation::id);

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public static Relation parse(String id) {
		for (Relation relation : values()) {
			if (relation.id().equals(id)) {
				return relation;
			}
		}
		return NEUTRAL;
	}

	public String key() {
		return "relation.jaymoskingdoms." + this.id();
	}
}
