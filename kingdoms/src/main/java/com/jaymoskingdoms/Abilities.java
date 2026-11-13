package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import net.minecraft.server.level.ServerPlayer;

/**
 * The class key (default {@code J}, "Imbue" for a Sorcerer) is every class's one button for what the class does by a press:
 * a Cleric blesses the band, a Druid takes a shape or leaves it, a Monk readies a stunning blow or catches an arrow, a
 * Paladin lays on hands, a Ranger reads the ground, a Warlock settles with the patron, a Tinkerer reads its infusions. With sneak
 * held each does its other thing. The press arrives as {@link Spellcraft.Imbue}; this is where it is told apart.
 */
public final class Abilities {
	private Abilities() {
	}

	/** Answers the class key for the classes that have a button of their own; false for the others (Sorcerers' imbue goes on). */
	static boolean key(ServerPlayer player, boolean sneak) {
		return answer(player, Classes.classOf(player), sneak);
	}

	/** The class key held with Alt: the second class's button, with sneak as for the first. */
	static void second(ServerPlayer player, boolean sneak) {
		PlayerClass cls = Classes.second(player);
		if (cls == PlayerClass.SORCERER) {
			Spellcraft.sorcerer(player, sneak);
			return;
		}
		answer(player, cls, sneak);
	}

	private static boolean answer(ServerPlayer player, PlayerClass cls, boolean sneak) {
		if (cls == null || player.isSpectator() || !player.isAlive()) {
			return false;
		}
		switch (cls) {
			case CLERIC -> Clerics.key(player, sneak);
			case DRUID -> Druids.key(player, sneak);
			case MONK -> Monks.key(player, sneak);
			case PALADIN -> Paladins.key(player, sneak);
			case RANGER -> Rangers.key(player, sneak);
			case WARLOCK -> Warlocks.key(player, sneak);
			case TINKERER -> Tinkerers.key(player, sneak);
			default -> {
				return false;
			}
		}
		return true;
	}
}
