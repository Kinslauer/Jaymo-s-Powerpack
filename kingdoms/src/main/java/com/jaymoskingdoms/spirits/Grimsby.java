package com.jaymoskingdoms.spirits;

import java.util.ArrayList;
import java.util.List;

/**
 * (0.7.236) Grimsby the Weary: a grumpy old wizard of four hundred years' study who has been made a fly swatter for a child,
 * and is secretly fond of them. Blade and parry. Steel-blue-white, long and bony. (Track C fills in the moves and the
 * autonomous acts.)
 */
public final class Grimsby extends Spirit {
	/** Its own moves; Track C adds them. */
	public static final List<Move> MOVES = new ArrayList<>();

	public Grimsby() {
		super(Spirits.GRIMSBY, "grimsby", 0xFF9AB8FF, 1.0F);
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
