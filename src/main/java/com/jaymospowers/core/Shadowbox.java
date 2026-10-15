package com.jaymospowers.core;

import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * What a shadowboxer throws, and when (shared by the server's swishes and every client's pose, MeditatePose). He runs
 * through combos one after another with a breath between. The basic one is jab, cross, jab and a kick that alternates:
 * a high kick tipped at the hip, then a side kick. Six more unlock as he unlocks abilities ({@link #COMBOS_AT}).
 */
public final class Shadowbox {
	/** Ticks a beat: a punch takes one, a kick two. */
	public static final int BEAT = 9;
	/** How far into a move its blow lands (0-1): the swish plays then. */
	public static final float STRIKE = 0.42F;
	/** Combos unlocked by abilities unlocked (0-4). */
	static final int[] COMBOS_AT = {0, 1, 2, 4, 6};

	/** Lead = left (left foot forward), rear = right. */
	public enum Move {
		JAB(false), CROSS(false), LEAD_HOOK(false), REAR_HOOK(false), LEAD_UPPERCUT(false), REAR_UPPERCUT(false), REAR_ELBOW(false),
		REAR_KNEE(true), TEEP(true), HIP_KICK(true), LEAD_HIP_KICK(true), SIDE_KICK(true), LEAD_SIDE_KICK(true), LOW_KICK(true),
		SPIN_BACK_KICK(true), AXE_KICK(true);

		public final boolean kick;

		Move(boolean kick) {
			this.kick = kick;
		}

		public int ticks() {
			return kick ? BEAT * 2 : BEAT;
		}
	}

	/** The two basics: the kick alternates between them. */
	private static final Move[] BASIC_HIP = {Move.JAB, Move.CROSS, Move.JAB, Move.HIP_KICK};
	private static final Move[] BASIC_SIDE = {Move.JAB, Move.CROSS, Move.JAB, Move.SIDE_KICK};
	/** The six that unlock, in order. */
	private static final Move[][] UNLOCKS = {
		{Move.JAB, Move.CROSS, Move.LEAD_HOOK, Move.LOW_KICK},
		{Move.JAB, Move.JAB, Move.CROSS, Move.TEEP},
		{Move.LEAD_HOOK, Move.REAR_UPPERCUT, Move.REAR_ELBOW, Move.REAR_KNEE},
		{Move.CROSS, Move.LEAD_HOOK, Move.SPIN_BACK_KICK},
		{Move.LEAD_UPPERCUT, Move.REAR_UPPERCUT, Move.LEAD_HIP_KICK, Move.AXE_KICK},
		{Move.JAB, Move.LEAD_SIDE_KICK, Move.CROSS, Move.REAR_HOOK, Move.HIP_KICK},
	};

	/** Playlists by combos unlocked (0-6): the hip-kick basic, half the unlocked, the side-kick basic, the rest. */
	private static final Move[][][] PLAYLISTS = new Move[UNLOCKS.length + 1][][];
	private static final int[] LENGTHS = new int[UNLOCKS.length + 1];

	static {
		for (int n = 0; n <= UNLOCKS.length; n++) {
			int half = (n + 1) / 2;
			Move[][] list = new Move[n + 2][];
			int i = 0;
			list[i++] = BASIC_HIP;
			for (int c = 0; c < half; c++) {
				list[i++] = UNLOCKS[c];
			}
			list[i++] = BASIC_SIDE;
			for (int c = half; c < n; c++) {
				list[i++] = UNLOCKS[c];
			}
			PLAYLISTS[n] = list;
			int length = 0;
			for (Move[] combo : list) {
				length += BEAT;
				for (Move move : combo) {
					length += move.ticks();
				}
			}
			LENGTHS[n] = length;
		}
	}

	/** The move being thrown, {@code into} ticks in out of {@code length}; a null move is the breath between combos. */
	public record Step(@Nullable Move move, int into, int length) {
		public float progress(float partialTicks) {
			return Mth.clamp((into + partialTicks) / (float)length, 0.0F, 1.0F);
		}
	}

	private Shadowbox() {
	}

	/** Combos unlocked for this many abilities unlocked (0-4). */
	public static int combos(int abilities) {
		return COMBOS_AT[Mth.clamp(abilities, 0, COMBOS_AT.length - 1)];
	}

	/** Where he is {@code ticks} into shadowboxing with {@code combos} combos unlocked. */
	public static Step step(long ticks, int combos) {
		int n = Mth.clamp(combos, 0, UNLOCKS.length);
		int t = (int)Math.floorMod(ticks, (long)LENGTHS[n]);
		for (Move[] combo : PLAYLISTS[n]) {
			if (t < BEAT) {
				return new Step(null, t, BEAT);
			}
			t -= BEAT;
			for (Move move : combo) {
				if (t < move.ticks()) {
					return new Step(move, t, move.ticks());
				}
				t -= move.ticks();
			}
		}
		return new Step(null, 0, BEAT);
	}
}
