package com.jaymoskingdoms.spirits;

import java.util.ArrayList;
import java.util.List;

/** (0.7.236) Old Ambrose, the Warden. A stub: its track fills the moves, the hooks and the lines (see docs/Spirits-Plan.md). */
public final class Ambrose extends Spirit {
	/** Its own moves, after the shared hands. */
	public static final List<Move> MOVES = new ArrayList<>();

	Ambrose() {
		super(Spirits.AMBROSE, "ambrose", 0xFF9BE08F, 2.0F);
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
