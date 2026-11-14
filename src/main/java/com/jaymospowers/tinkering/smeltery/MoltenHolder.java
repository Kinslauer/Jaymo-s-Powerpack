package com.jaymospowers.tinkering.smeltery;

import org.jspecify.annotations.Nullable;

/**
 * Something that gives molten metal, takes it, or both: what a faucet pours from and into, and what a channel runs into.
 * The smeltery and the melter give, casting tables and basins take, tanks and channels do both.
 */
public interface MoltenHolder {
	/** The liquid it would give now (a pool's bottom one), or null. */
	default @Nullable Molten offers() {
		return null;
	}

	/** Takes some out; returns how much there was. */
	default int drain(Molten molten, int amount) {
		return 0;
	}

	/** How much of this liquid it would still take (0: none). */
	default int wants(Molten molten) {
		return 0;
	}

	/** Pours some in (no more than {@link #wants}). */
	default void fill(Molten molten, int amount) {
	}
}
