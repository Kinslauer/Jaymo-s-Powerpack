package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The class key (default {@code J}, "Imbue" for a Sorcerer) is every class's one button for what the class does by a press:
 * a Cleric blesses the band, a Druid takes a shape or leaves it, a Monk readies a stunning blow or catches an arrow, a
 * Paladin lays on hands, a Ranger reads the ground, a Warlock settles with the patron, a Tinkerer reads its infusions. With sneak
 * held each does its other thing. The press arrives as {@link Spellcraft.Imbue}; this is where it is told apart. Sneak and the
 * key held ({@link #HOLD_TICKS}) is a third thing for a Druid and a Sorcerer ({@link #held}).
 */
public final class Abilities {
	/** Sneak and the class key held this long (ticks) is a hold ({@link #held}); let go sooner, it was sneak and a tap. */
	public static final int HOLD_TICKS = 10;

	private Abilities() {
	}

	/**
	 * Whether sneak and {@code player}'s class key (their second class's, if {@code second}) has a working of its own on
	 * the hold: a Druid's Barkskin, a Sorcerer's element working. The client waits to tell a hold from a tap only for these.
	 */
	public static boolean holds(Player player, boolean second) {
		PlayerClass cls = second ? Classes.second(player) : Classes.classOf(player);
		return cls == PlayerClass.DRUID || cls == PlayerClass.SORCERER && Spellcraft.hasWorking(Classes.optionOf(player, PlayerClass.SORCERER));
	}

	/** Sneak and the class key held ({@link Spellcraft.Held}); answered as sneak and a tap when the class has nothing on the hold. */
	static void held(ServerPlayer player, boolean second) {
		if (player.isSpectator() || !player.isAlive() || ElementSpells.lanceKey(player)) {
			return;
		}
		PlayerClass cls = second ? Classes.second(player) : Classes.classOf(player);
		if (cls == PlayerClass.DRUID && Druids.held(player) || cls == PlayerClass.SORCERER && Spellcraft.working(player)) {
			return;
		}
		if (second) {
			second(player, true);
		} else {
			Spellcraft.imbue(player, true);
		}
	}

	/** Answers the class key for the classes that have a button of their own; false for the others (Sorcerers' imbue goes on). */
	static boolean key(ServerPlayer player, boolean sneak) {
		// a Fire Lance locked with another stream: the key shoves the knot
		if (ElementSpells.lanceKey(player)) {
			return true;
		}
		return answer(player, Classes.classOf(player), sneak);
	}

	/** The class key held with Alt: the second class's button, with sneak as for the first. */
	static void second(ServerPlayer player, boolean sneak) {
		if (ElementSpells.lanceKey(player)) {
			return;
		}
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
			// (class trees) the Marksman's Field Kit
			case MARKSMAN -> Workshop.key(player, sneak);
			// (class trees) Perfect Form and the Rampage
			case WARLORD -> Discipline.key(player, sneak);
			case BRUTE -> Fury.key(player, sneak);
			// (class trees) Spur, and hanging off the side
			case RIDER -> com.jaymoskingdoms.equestrian.Horsemanship.key(player, sneak);
			default -> {
				return false;
			}
		}
		return true;
	}
}
