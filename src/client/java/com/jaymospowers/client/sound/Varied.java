package com.jaymospowers.client.sound;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Andy: "what if we made sounds choose from a pool so the outcomes are more varied?" A sound event already picks one of
 * its recordings at random, but most of ours (and many of the game's) have one or two, and a lot of code plays them at a
 * fixed pitch, so the same hit, step or clink comes back note for note. Here every one-shot sound in the world gets its
 * pitch from a small pool of {@link #STEPS} steps either side of what was asked, and never the step it had the time
 * before, so two plays in a row always differ. Loops, music, voices, menus and anything tuned (note blocks, horns,
 * bells, records, the comparator click of Chronokinesis' clocks) are left exactly as asked. The pick is kept for as long as the sound plays, so a sound whose pitch is
 * read again mid-play doesn't warble.
 */
public final class Varied {
	/** Steps either side of the asked pitch, and the size of one step (0.02 = 2%, about a third of a semitone). */
	static final int STEPS = 3;
	static final float STEP = 0.02F;
	/** Sounds whose id holds one of these are tuned on purpose and stay as they are. */
	static final String[] TUNED = {"note_block", "music_disc", "goat_horn", "bell", "jukebox", "chime", "instrument", "music", "comparator"};

	private static final Map<SoundInstance, Float> PICKED = new WeakHashMap<>();
	private static final Map<Identifier, Integer> LAST = new HashMap<>();
	private static final Map<Identifier, Boolean> IS_TUNED = new HashMap<>();
	private static final RandomSource RANDOM = SoundInstance.createUnseededRandom();

	private Varied() {
	}

	/** The pitch {@code sound} plays at, given the one the engine worked out. */
	public static float pitch(SoundInstance sound, float pitch) {
		Float factor = PICKED.get(sound);
		if (factor == null) {
			factor = varies(sound) ? pick(sound.getIdentifier()) : 1.0F;
			PICKED.put(sound, factor);
		}
		return factor == 1.0F ? pitch : Mth.clamp(pitch * factor, 0.5F, 2.0F);
	}

	private static boolean varies(SoundInstance sound) {
		SoundSource source = sound.getSource();
		if (sound.isLooping() || sound.isRelative() || source == SoundSource.MUSIC || source == SoundSource.RECORDS
			|| source == SoundSource.VOICE || source == SoundSource.UI) {
			return false;
		}
		return !IS_TUNED.computeIfAbsent(sound.getIdentifier(), id -> {
			String path = id.getPath();
			for (String word : TUNED) {
				if (path.contains(word)) {
					return true;
				}
			}
			return false;
		});
	}

	/** One of the 2 x STEPS + 1 steps, never the one this sound had last time. */
	private static float pick(Identifier id) {
		int pool = STEPS * 2 + 1;
		Integer last = LAST.get(id);
		int step = RANDOM.nextInt(last == null ? pool : pool - 1);
		if (last != null && step >= last) {
			step++;
		}
		LAST.put(id, step);
		return 1.0F + (step - STEPS) * STEP;
	}
}
