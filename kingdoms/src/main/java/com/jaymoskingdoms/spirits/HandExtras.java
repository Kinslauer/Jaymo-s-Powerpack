package com.jaymoskingdoms.spirits;

import java.util.ArrayList;
import java.util.List;

/** (0.7.236) The second patch's shared hands: Spirit armour and Spirit ride. A stub: track E fills it. */
public final class HandExtras {
	/** The moves every spirit has, after {@link HandMoves#SHARED}. */
	public static final List<Move> SHARED = new ArrayList<>();

	private HandExtras() {
	}

	static void register() {
		for (Move move : SHARED) {
			Moves.register(move);
		}
		HandMoves.SHARED.addAll(SHARED);
	}
}
