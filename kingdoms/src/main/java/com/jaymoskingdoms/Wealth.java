package com.jaymoskingdoms;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * (0.5.14) How rich a realm is: its treasury, its people and the fiefdoms that serve it. A <b>prosperous</b> kingdom
 * puts some of its guards on horses; a <b>wealthy</b> one also keeps mounted archers, and its king rides out on his
 * rounds in a carriage ({@link Carriages}, {@link Cavalry}). Horses and carriages are bought from the treasury.
 */
final class Wealth {
	enum Tier {
		POOR, PROSPEROUS, WEALTHY;

		String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Its worth: its emeralds, and this much for every villager and every fiefdom beneath it. */
	static final int PER_VILLAGER = 4, PER_FIEF = 60;
	/** The worth a realm needs to be prosperous, and wealthy. */
	static final int PROSPEROUS = 120, WEALTHY = 260;

	private Wealth() {
	}

	static long worth(MinecraftServer server, Faction f) {
		long fiefs = Factions.all(server, FactionType.FIEFDOM).stream().filter(x -> x.overlord() == f.id() && !x.fallen()).count();
		return f.treasury() + (long)f.holdings().population() * PER_VILLAGER + fiefs * PER_FIEF;
	}

	/** A kingdom's tier (a fiefdom, village or camp is always poor: its riches go to its overlord). */
	static Tier of(MinecraftServer server, Faction f) {
		if (f.type() != FactionType.KINGDOM || f.fallen()) {
			return Tier.POOR;
		}
		long w = worth(server, f);
		return w >= WEALTHY ? Tier.WEALTHY : w >= PROSPEROUS ? Tier.PROSPEROUS : Tier.POOR;
	}

	static boolean atLeast(MinecraftServer server, Faction f, Tier tier) {
		return of(server, f).ordinal() >= tier.ordinal();
	}

	static Component describe(Tier t) {
		return Component.translatable("wealth.jaymoskingdoms." + t.key());
	}
}
