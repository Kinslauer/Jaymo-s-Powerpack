package com.jaymoskingdoms.spirits;

import java.util.ArrayList;
import java.util.List;

/** (0.7.236) Thessaly the Learned. A stub: its track fills the moves, the hooks and the lines (see docs/Spirits-Plan.md). */
public final class Thessaly extends Spirit {
	/** Its own moves, after the shared hands. */
	public static final List<Move> MOVES = new ArrayList<>();

	Thessaly() {
		super(Spirits.THESSALY, "thessaly", 0xFFFF9ED8, 0.6F);
	}

	static void register() {
		for (Move move : MOVES) {
			Moves.register(move);
		}
	}

	@Override
	public List<Move> moves() {
		return MOVES;
	}
}
