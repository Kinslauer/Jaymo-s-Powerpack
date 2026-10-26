package com.jaymoskingdoms.spirits;

import java.util.ArrayList;
import java.util.List;

/**
 * (0.7.236) Old Varro, the Siege-Mage: a gruff war veteran who barks orders and is proud when he is listened to. Javelins
 * and bombards. Amber, a gauntlet as tall as you. (Track C fills in the moves and the autonomous acts.)
 */
public final class Varro extends Spirit {
	/** Its own moves; Track C adds them. */
	public static final List<Move> MOVES = new ArrayList<>();

	public Varro() {
		super(Spirits.VARRO, "varro", 0xFFFFB347, 2.4F);
	}

	@Override
	public List<Move> moves() {
		return MOVES;
	}

	/** Adds its moves to {@link Moves}. */
	public static void register() {
		for (Move move : MOVES) {
			Moves.register(move);
		}
	}
}
