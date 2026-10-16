package com.jaymospowers.core;

import com.jaymospowers.power.zfighter.ZFighterCombo;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * What a shadowboxer throws, and when (shared by the server's swishes and every client's pose, MeditatePose). He runs
 * through combos one after another with a breath between. The basic one is a step-in jab, a reverse punch, a jab and a
 * kick that alternates: a high kick tipped at the hip, then a side kick. Six more unlock as he unlocks abilities
 * ({@link #COMBOS_AT}), drawn from the Z Fighter's martial-arts set ({@link ZFighterCombo}), so practice looks like the
 * fight: those blows ({@link Step#art}) are drawn by ZFighterComboPose over MeditatePose.
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

	/**
	 * One blow of a combo: a shadowboxing move of its own ({@code art} -1), or a move of the martial-arts set
	 * ({@code art}, a {@link ZFighterCombo} move) with a plain stand-in {@code move} for the swish and MeditatePose's
	 * underlay (a jab for a hand, a knee for a leg: neither turns or tips the body).
	 */
	private record Beat(Move move, int art) {
		int ticks() {
			return art < 0 ? move.ticks() : artTicks(art);
		}
	}

	private static Beat own(Move move) {
		return new Beat(move, -1);
	}

	private static Beat art(int art) {
		return new Beat(ZFighterCombo.kick(art) ? Move.REAR_KNEE : Move.JAB, art);
	}

	/** A martial-arts move's beats: kicks and the two heavy hand moves take two. */
	private static int artTicks(int art) {
		return ZFighterCombo.kick(art) || art == ZFighterCombo.DOUBLE_PALM || art == ZFighterCombo.DRAGON_FIST ? BEAT * 2 : BEAT;
	}

	/** The two basics: the kick alternates between them. */
	private static final Beat[] BASIC_HIP = {art(ZFighterCombo.JAB), art(ZFighterCombo.REVERSE_PUNCH), art(ZFighterCombo.JAB), own(Move.HIP_KICK)};
	private static final Beat[] BASIC_SIDE = {art(ZFighterCombo.JAB), art(ZFighterCombo.REVERSE_PUNCH), art(ZFighterCombo.JAB), own(Move.SIDE_KICK)};
	/** The six that unlock, in order: the hold combo's strings in pieces. */
	private static final Beat[][] UNLOCKS = {
		{art(ZFighterCombo.JAB), art(ZFighterCombo.REVERSE_PUNCH), art(ZFighterCombo.ELBOW), art(ZFighterCombo.CHI_PALM)},
		{art(ZFighterCombo.JAB), art(ZFighterCombo.REVERSE_PUNCH), art(ZFighterCombo.FRONT_KICK), art(ZFighterCombo.ROUNDHOUSE)},
		{art(ZFighterCombo.RISING_KNEE), art(ZFighterCombo.LEG_SWEEP), art(ZFighterCombo.AXE_KICK)},
		{art(ZFighterCombo.REVERSE_PUNCH), art(ZFighterCombo.ELBOW), art(ZFighterCombo.SPIN_BACK_KICK)},
		{art(ZFighterCombo.CRESCENT_KICK), art(ZFighterCombo.SPIN_HOOK_KICK), art(ZFighterCombo.DOUBLE_PALM)},
		{art(ZFighterCombo.JAB), art(ZFighterCombo.FLYING_KNEE), art(ZFighterCombo.FLYING_DOUBLE_KICK), art(ZFighterCombo.DRAGON_FIST)},
	};

	/** Playlists by combos unlocked (0-6): the hip-kick basic, half the unlocked, the side-kick basic, the rest. */
	private static final Beat[][][] PLAYLISTS = new Beat[UNLOCKS.length + 1][][];
	private static final int[] LENGTHS = new int[UNLOCKS.length + 1];

	static {
		for (int n = 0; n <= UNLOCKS.length; n++) {
			int half = (n + 1) / 2;
			Beat[][] list = new Beat[n + 2][];
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
			for (Beat[] combo : list) {
				length += BEAT;
				for (Beat beat : combo) {
					length += beat.ticks();
				}
			}
			LENGTHS[n] = length;
		}
	}

	/**
	 * The move being thrown, {@code into} ticks in out of {@code length}; a null move is the breath between combos.
	 * {@code art} is the martial-arts move it really is ({@link ZFighterCombo}'s numbering), or -1 for a move of its own.
	 */
	public record Step(@Nullable Move move, int into, int length, int art) {
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
		for (Beat[] combo : PLAYLISTS[n]) {
			if (t < BEAT) {
				return new Step(null, t, BEAT, -1);
			}
			t -= BEAT;
			for (Beat beat : combo) {
				if (t < beat.ticks()) {
					return new Step(beat.move(), t, beat.ticks(), beat.art());
				}
				t -= beat.ticks();
			}
		}
		return new Step(null, 0, BEAT, -1);
	}
}
