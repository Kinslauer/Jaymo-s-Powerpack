package com.jaymospowers.api;

/**
 * The four ability keys every power shares (defaults R, G, V, B; rebindable under Controls → Powers).
 * Each power decides what its keys do; the core only reports presses and releases.
 */
public enum PowerKey {
	ABILITY_1,
	ABILITY_2,
	ABILITY_3,
	ABILITY_4;

	private static final PowerKey[] VALUES = values();

	public static PowerKey byIndex(int index) {
		return index >= 0 && index < VALUES.length ? VALUES[index] : null;
	}

	/** Lang key suffix, e.g. {@code ability_1}. */
	public String id() {
		return name().toLowerCase();
	}
}
