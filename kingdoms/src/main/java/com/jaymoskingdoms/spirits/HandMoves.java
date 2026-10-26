package com.jaymoskingdoms.spirits;

import java.util.ArrayList;
import java.util.List;

/**
 * (0.7.236) The mage hands every spirit shares: the far hand, grab and throw, catch, shove, hold the line, the second pair
 * of hands (Track B fills this in). Every spirit's move list starts with these.
 */
public final class HandMoves {
	/** The shared moves, in the order they're cycled. */
	public static final List<Move> SHARED = new ArrayList<>();

	private HandMoves() {
	}

	/** Adds {@link #SHARED} to {@link Moves}. */
	public static void register() {
		for (Move move : SHARED) {
			Moves.register(move);
		}
	}
}
