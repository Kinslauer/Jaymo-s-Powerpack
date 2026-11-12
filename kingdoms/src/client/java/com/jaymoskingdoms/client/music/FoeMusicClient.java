package com.jaymoskingdoms.client.music;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.music.FoeThemes;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A great foe's theme on your own game ({@link FoeThemes} says which and how tense). Nothing loops: a theme is eight short
 * stems (two drones, two drum patterns, three motifs, a stab for when the fight turns), and each layer here plays one of
 * its stems at its own uneven interval with a little pitch jitter, so they recombine and never read as one recording.
 * <ul>
 * <li>It fades in over {@link #FADE_IN} ticks when the server says the fight is on, and out over {@link #FADE_OUT} when it
 * is over (the foe slain, left behind, or you fallen), or when the server has been silent for {@link #SILENCE}.</li>
 * <li>A tense fight plays the drums more, a desperate one adds the stab and quickens everything.</li>
 * <li>Every stem is {@link SoundSource#MUSIC}, so the Music volume slider rules it. While a theme sounds, the game's own
 * music is stopped every {@link #DUCK_EVERY} ticks so the two do not play over each other; it comes back by itself
 * afterwards.</li>
 * </ul>
 */
public final class FoeMusicClient {
	/** Fade in and out (ticks), and how long without word from the server before the theme is let go (ticks). */
	static final int FADE_IN = 50, FADE_OUT = 45, SILENCE = FoeThemes.REPEAT_TICKS * 2 + 20;
	/** How often the game's own music is stopped while a theme sounds (ticks). */
	static final int DUCK_EVERY = 20;
	/** Each layer's volume and its gap between plays (ticks), calm fight; a tense fight shortens the gaps. */
	static final float DRONE_VOLUME = 0.55F, PULSE_VOLUME = 0.6F, MOTIF_VOLUME = 0.5F, STAB_VOLUME = 0.7F;
	static final int DRONE_GAP_MIN = 90, DRONE_GAP_MAX = 125, PULSE_GAP_MIN = 40, PULSE_GAP_MAX = 110, MOTIF_GAP_MIN = 100, MOTIF_GAP_MAX = 220,
		STAB_GAP_MIN = 120, STAB_GAP_MAX = 240;
	/** The share a gap shrinks to at tension 1 and 2. */
	static final float[] QUICKEN = {1.0F, 0.8F, 0.55F};
	/** Pitch steps a motif may be played at, and the jitter on every stem. */
	static final float[] MOTIF_PITCHES = {1.0F, 1.0F, 0.8909F, 1.1225F};
	static final float JITTER = 0.03F;

	private static final String[] DRONES = {"drone_a", "drone_b"}, PULSES = {"pulse_a", "pulse_b"}, MOTIFS = {"motif_a", "motif_b", "motif_c"};

	private static String theme = "";
	private static int tension;
	private static long lastTold;
	private static int age;
	/** The master gain, 0..1, eased toward 1 while a theme is wanted and toward 0 when not. */
	private static float gain;
	private static boolean wanted;
	private static final long[] NEXT = new long[4];
	private static final List<Stem> STEMS = new ArrayList<>();

	private FoeMusicClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FoeThemes.Play.TYPE, (payload, context) -> told(payload.theme(), payload.tension()));
		ClientTickEvents.END_CLIENT_TICK.register(FoeMusicClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> hush());
	}

	private static void told(String newTheme, int newTension) {
		lastTold = age;
		if (newTheme.isEmpty()) {
			wanted = false;
			return;
		}
		if (!newTheme.equals(theme)) {
			// a different foe: the old theme's tails run out under the fade; the new one starts its layers at once
			theme = newTheme;
			for (int i = 0; i < NEXT.length; i++) {
				NEXT[i] = 0;
			}
		}
		tension = Mth.clamp(newTension, 0, 2);
		wanted = true;
	}

	private static void hush() {
		wanted = false;
		gain = 0.0F;
		theme = "";
		STEMS.clear();
	}

	private static void tick(Minecraft minecraft) {
		age++;
		if (wanted && age - lastTold > SILENCE) {
			wanted = false;
		}
		if (minecraft.player == null || minecraft.level == null) {
			if (gain > 0.0F || !theme.isEmpty()) {
				hush();
			}
			return;
		}
		if (wanted) {
			gain = Math.min(1.0F, gain + 1.0F / FADE_IN);
		} else {
			gain = Math.max(0.0F, gain - 1.0F / FADE_OUT);
		}
		STEMS.removeIf(Stem::isStopped);
		if (gain <= 0.0F || theme.isEmpty()) {
			return;
		}
		if (age % DUCK_EVERY == 0 && minecraft.getMusicManager() != null) {
			minecraft.getMusicManager().stopPlaying();
		}
		if (!wanted) {
			return;
		}
		RandomSource random = minecraft.level.getRandom();
		long now = age;
		float quicken = QUICKEN[tension];
		if (due(0, now, DRONE_GAP_MIN, DRONE_GAP_MAX, 1.0F, random)) {
			play(minecraft, DRONES[random.nextInt(DRONES.length)], DRONE_VOLUME, pitch(random, 1.0F));
		}
		if (due(1, now, PULSE_GAP_MIN, PULSE_GAP_MAX, quicken, random)) {
			// a calm fight keeps the drums sparse; a tense one rarely misses
			if (tension > 0 || random.nextFloat() < 0.6F) {
				play(minecraft, PULSES[random.nextInt(PULSES.length)], PULSE_VOLUME * (0.8F + 0.1F * tension), pitch(random, 1.0F));
			}
		}
		if (due(2, now, MOTIF_GAP_MIN, MOTIF_GAP_MAX, quicken, random) && random.nextFloat() < 0.85F) {
			play(minecraft, MOTIFS[random.nextInt(MOTIFS.length)], MOTIF_VOLUME, pitch(random, MOTIF_PITCHES[random.nextInt(MOTIF_PITCHES.length)]));
		}
		if (tension >= 1 && due(3, now, STAB_GAP_MIN, STAB_GAP_MAX, tension == 2 ? 0.5F : 1.0F, random)) {
			play(minecraft, "stab", STAB_VOLUME, pitch(random, 1.0F));
		}
	}

	/** Whether layer {@code index} is due: true at once the first time, then after a random gap (shrunk by {@code quicken}). */
	private static boolean due(int index, long now, int min, int max, float quicken, RandomSource random) {
		if (now < NEXT[index]) {
			return false;
		}
		int gap = min + random.nextInt(Math.max(1, max - min + 1));
		NEXT[index] = now + Math.round(gap * quicken);
		return true;
	}

	private static float pitch(RandomSource random, float base) {
		return base * (1.0F + (random.nextFloat() * 2.0F - 1.0F) * JITTER);
	}

	private static void play(Minecraft minecraft, String stem, float volume, float pitch) {
		SoundEvent event = SoundEvent.createVariableRangeEvent(JaymosKingdoms.id("foe_theme." + theme + "." + stem));
		Stem instance = new Stem(event, volume, pitch);
		STEMS.add(instance);
		minecraft.getSoundManager().play(instance);
	}

	/** One stem, played once, at the master gain as it is each tick (so a fade reaches stems already sounding). */
	private static final class Stem extends AbstractTickableSoundInstance {
		private final float base;

		Stem(SoundEvent event, float base, float pitch) {
			super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.base = base;
			this.volume = base * gain;
			this.pitch = pitch;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		@Override
		public void tick() {
			this.volume = Math.max(0.0001F, this.base * gain * gain);
			if (gain <= 0.0F) {
				this.stop();
			}
		}
	}
}
