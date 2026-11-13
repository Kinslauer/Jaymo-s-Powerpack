package com.jaymospowers.client.sound;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Music under our letterboxed scenes (the ultimate's film and the beam battle's). Andy, of the films: "it should fade in
 * with the letterboxing and out as it ends. lingering for 5s", and "any of the discs can play for the other scenes too".
 * A scene tells this, every tick, whether its bars are up ({@link #scene}); the music fades in as they come in, plays
 * through, and once they start to go it fades out, gone about five seconds after the bars are.
 * <ul>
 * <li><b>What plays:</b> a scene may name its own track; otherwise a music disc is drawn at random ({@link #DISCS},
 * never the same one twice running). Disc 11 is left out: it is footsteps and breathing, not music.</li>
 * <li>Everything plays as {@link SoundSource#MUSIC}, so the Music slider rules it, and the game's own music is stopped
 * every {@link #DUCK_EVERY} ticks while ours sounds (it comes back by itself afterwards). Jaymo's Kingdoms' great foes'
 * themes go quiet under it ({@link #cover}).</li>
 * <li>A scene that starts while another's music is still lingering takes over: the old track fades out over
 * {@link #HAND_OVER} ticks. The same scene again (a second entrance straight after the first) keeps its track playing and
 * brings it back up.</li>
 * <li>Jaymo's Kingdoms has the same player for its own films; the two hand over through a system property
 * ({@link #OWNER}) so their tracks never play over each other, and each says how loud it is ({@link #COVER}).</li>
 * </ul>
 */
public final class SceneMusic {
	/** Fade in (ticks: the bars take about this long to come in), and the fade out from when the bars start to go (ticks: they
	 * take about 15 to leave, then 5 seconds more). A track losing the stage to another scene's fades out over {@link #HAND_OVER}. */
	static final int FADE_IN = 15, LINGER = 115, HAND_OVER = 20;
	/** How often the game's own music is stopped while ours sounds (ticks). */
	static final int DUCK_EVERY = 20;
	/** Loudness of a disc and of a scene's own track (the own tracks' loudness is set in sounds.json). */
	static final float DISC_VOLUME = 0.55F, TRACK_VOLUME = 1.0F;
	/** The discs a scene with no track of its own draws from (the game's {@code music_disc.<name>} sounds). */
	static final String[] DISCS = {"13", "5", "blocks", "bounce", "cat", "chirp", "creator", "creator_music_box", "far", "lava_chicken",
		"mall", "mellohi", "otherside", "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward"};
	/** Which mod's scene has the stage (shared with Jaymo's Kingdoms); each mod's loudness is {@code COVER + "." + mod id}. */
	static final String OWNER = "jaymos.sceneMusic.owner", COVER = "jaymos.sceneMusic.cover";
	static final String MOD = "jaymospowers", OTHER = "jaymoskingdoms";

	/** The scene the music is for, whether its bars are still up, and its track. */
	private static @Nullable String scene;
	private static boolean held;
	private static @Nullable Track current;
	/** Tracks fading out after losing the stage. */
	private static final List<Track> FADING = new ArrayList<>();
	private static String lastDisc = "";
	private static int age;
	private static float coverTold = -1.0F;

	private SceneMusic() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SceneMusic::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> hush(client));
	}

	/** Called every tick by a scene: {@code on} while its bars are up. It gets a random disc. */
	public static void scene(String key, boolean on) {
		scene(key, on, null);
	}

	/** Called every tick by a scene: {@code on} while its bars are up; {@code track} is its own music (null: a random disc). */
	public static void scene(String key, boolean on, @Nullable Identifier track) {
		if (on) {
			if (!held || !key.equals(scene)) {
				begin(key, track);
			}
		} else if (held && key.equals(scene)) {
			// the bars are going: the long fade begins
			held = false;
		}
	}

	/** How loud our scene music is now, 0..1, ours or Jaymo's Kingdoms' (what sounds under it is turned down by this much). */
	public static float cover() {
		float own = current == null ? 0.0F : current.level();
		for (Track track : FADING) {
			own = Math.max(own, track.level());
		}
		float other = 0.0F;
		String told = System.getProperty(COVER + "." + OTHER);
		if (told != null) {
			try {
				other = Float.parseFloat(told);
			} catch (NumberFormatException ignored) {
				other = 0.0F;
			}
		}
		return Mth.clamp(Math.max(own, other), 0.0F, 1.0F);
	}

	private static void begin(String key, @Nullable Identifier track) {
		Minecraft minecraft = Minecraft.getInstance();
		System.setProperty(OWNER, MOD);
		boolean same = key.equals(scene) && current != null && !current.isStopped()
			&& (track == null || track.equals(current.track));
		scene = key;
		held = true;
		if (same) {
			// the same scene again while its music lingers: keep it and bring it back up
			return;
		}
		if (current != null) {
			FADING.add(current);
		}
		Identifier id = track;
		float volume = TRACK_VOLUME;
		if (id == null) {
			id = Identifier.withDefaultNamespace("music_disc." + disc(minecraft.level == null ? RandomSource.create() : minecraft.level.getRandom()));
			volume = DISC_VOLUME;
		}
		current = new Track(id, volume);
		minecraft.getSoundManager().play(current);
		stopGameMusic(minecraft);
	}

	private static String disc(RandomSource random) {
		String pick;
		do {
			pick = DISCS[random.nextInt(DISCS.length)];
		} while (pick.equals(lastDisc) && DISCS.length > 1);
		lastDisc = pick;
		return pick;
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.isPaused()) {
			return;
		}
		if (minecraft.player == null || minecraft.level == null) {
			if (current != null || !FADING.isEmpty()) {
				hush(minecraft);
			}
			return;
		}
		age++;
		if (current != null) {
			// Jaymo's Kingdoms' scene took the stage
			if (!MOD.equals(System.getProperty(OWNER))) {
				FADING.add(current);
				current = null;
				scene = null;
				held = false;
			} else {
				current.at = held ? Math.min(1.0F, current.at + 1.0F / FADE_IN) : current.at - 1.0F / LINGER;
				if (current.at <= 0.0F || current.isStopped()) {
					current.end();
					current = null;
					scene = null;
				}
			}
		}
		FADING.removeIf(track -> {
			track.at -= 1.0F / HAND_OVER;
			if (track.at <= 0.0F || track.isStopped()) {
				track.end();
				return true;
			}
			return false;
		});
		boolean sounding = current != null || !FADING.isEmpty();
		if (sounding && age % DUCK_EVERY == 0) {
			stopGameMusic(minecraft);
		}
		tellCover();
	}

	/** Lets Jaymo's Kingdoms know how loud we are (only when it changes). */
	private static void tellCover() {
		float own = current == null ? 0.0F : current.level();
		for (Track track : FADING) {
			own = Math.max(own, track.level());
		}
		if (Math.abs(own - coverTold) >= 0.01F || own == 0.0F && coverTold != 0.0F) {
			coverTold = own;
			System.setProperty(COVER + "." + MOD, Float.toString(own));
		}
	}

	private static void stopGameMusic(Minecraft minecraft) {
		if (minecraft.getMusicManager() != null) {
			minecraft.getMusicManager().stopPlaying();
		}
	}

	private static void hush(Minecraft minecraft) {
		if (current != null) {
			current.end();
		}
		for (Track track : FADING) {
			track.end();
		}
		FADING.clear();
		current = null;
		scene = null;
		held = false;
		tellCover();
	}

	/** One scene's music, at its fade's level each tick (an equal-power curve: it comes up quickly and lingers going down). */
	private static final class Track extends AbstractTickableSoundInstance {
		final Identifier track;
		final float base;
		/** Where it is in its fade, 0..1. */
		float at;

		Track(Identifier track, float base) {
			super(SoundEvent.createVariableRangeEvent(track), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.track = track;
			this.base = base;
			this.volume = 0.0001F;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		float level() {
			float s = Mth.sin(Mth.clamp(this.at, 0.0F, 1.0F) * Mth.HALF_PI);
			return s * s;
		}

		void end() {
			this.stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			this.volume = Math.max(0.0001F, this.base * level());
		}
	}
}
