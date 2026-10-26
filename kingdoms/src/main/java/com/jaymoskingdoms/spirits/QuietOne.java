package com.jaymoskingdoms.spirits;

import java.util.ArrayList;
import java.util.List;

/** (0.7.236) The Quiet One. A stub: its track fills the moves, the hooks and the lines (see docs/Spirits-Plan.md). */
public final class QuietOne extends Spirit {
	/** Its own moves, after the shared hands. */
	public static final List<Move> MOVES = new ArrayList<>();

	QuietOne() {
		super(Spirits.QUIET, "quiet", 0xFFC8C8DC, 1.2F);
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
