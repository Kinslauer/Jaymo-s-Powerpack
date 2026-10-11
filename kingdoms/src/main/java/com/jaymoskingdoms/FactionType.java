package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.Locale;

/**
 * What kind of settlement a {@link Faction} is: a {@link #KINGDOM} (a king, a seat, guards and archers), a
 * {@link #FIEFDOM} (a kingdom that's someone's vassal: a lord rules it, in its overlord's colours), a {@link #VILLAGE}
 * (an elder, vanilla villagers, its iron golems for guards) or a {@link #BARBARIAN} camp (a chief and his raiders).
 */
public enum FactionType {
	KINGDOM, FIEFDOM, VILLAGE, BARBARIAN;

	/** Saved by lower-case name; anything unknown reads as a kingdom (the only kind 0.2 saves had). */
	public static final Codec<FactionType> CODEC = Codec.STRING.xmap(FactionType::parse, FactionType::id);

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public static FactionType parse(String id) {
		for (FactionType type : values()) {
			if (type.id().equals(id)) {
				return type;
			}
		}
		return KINGDOM;
	}

	/** A kingdom or a fiefdom: a seat with a keep, guards, archers, a treasury, members. */
	public boolean isRealm() {
		return this == KINGDOM || this == FIEFDOM;
	}

	public String key() {
		return "faction_type.jaymoskingdoms." + this.id();
	}
}
