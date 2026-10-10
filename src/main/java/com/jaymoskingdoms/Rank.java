package com.jaymoskingdoms;

/**
 * A member's rank, earned by standing with their kingdom: a peasant on pledging, then soldier, knight and lord. A lord
 * may draw on the treasury.
 */
public enum Rank {
	PEASANT(0), SOLDIER(100), KNIGHT(300), LORD(700);

	/** The standing it takes. */
	public final int standing;

	Rank(int standing) {
		this.standing = standing;
	}

	public static Rank forStanding(int standing) {
		Rank best = PEASANT;
		for (Rank rank : values()) {
			if (standing >= rank.standing) {
				best = rank;
			}
		}
		return best;
	}

	public String key() {
		return "rank.jaymoskingdoms." + this.name().toLowerCase(java.util.Locale.ROOT);
	}
}
