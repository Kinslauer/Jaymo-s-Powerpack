package com.jaymoskingdoms.client.music;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PlaceThemes;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The music of the places, from the Medieval Music pack's tracks ({@code sounds/music/*.ogg} in the pack, reached
 * through {@code place_music.<track>} in sounds.json). The server says which place you are in ({@link PlaceThemes}); each
 * place has its own tracks ({@link #TRACKS}). Like the game's own music, a track comes now and then, not all the time:
 * after one ends (played through, or faded out as you left its place) the next waits as long as the game would wait
 * between songs ({@link #rest}: 10 to 20 minutes, shorter on the Music Frequency option's Frequent and Constant), and
 * none starts while the game's own music is playing. Leaving the place (or the server saying nothing for
 * {@link #SILENCE} ticks) fades a track out over {@link #FADE_OUT} ticks.
 * <ul>
 * <li>Only when the pack's file is there: without the pack the sound events resolve to nothing, so nothing is played,
 * and the game's own music is left alone.</li>
 * <li>Every track is {@link SoundSource#MUSIC}, so the Music slider rules it. While one sounds, the game's own music is
 * stopped every {@link #DUCK_EVERY} ticks.</li>
 * <li>A great foe's theme ({@link FoeMusicClient}) and a scene's music ({@link SceneMusic}) win: this fades out under
 * either.</li>
 * <li>The festival's square plays its tracks louder close to the fire ({@link #FIRE_LOUDER}).</li>
 * </ul>
 */
public final class PlaceMusic {
	static final int FADE_IN = 80, FADE_OUT = 60, DUCK_EVERY = 20, SILENCE = PlaceThemes.REPEAT_TICKS * 2 + 20;
	/** The rest between one track and the next (ticks): the game's own between songs (Musics.GAME), capped by the Music Frequency option. */
	static final int REST_MIN = 12000, REST_MAX = 24000;
	/** The rest on the Music Frequency option's Constant (ticks), and the first track's wait after joining (both as the game's own music). */
	static final int REST_CONSTANT = 100, FIRST_WAIT = 100;
	/** Loudness of a track, and the festival's (and its fire's) share of it. */
	static final float VOLUME = 1.0F, FESTIVAL = 1.2F, FIRE_LOUDER = 1.6F;

	/** The pack's tracks (file names under {@code music/}) each place plays. */
	static final Map<String, String[]> TRACKS = Map.of(
		"town", new String[] {"townsong", "greenlands"},
		"tavern", new String[] {"now_we_feast", "celebration"},
		"festival", new String[] {"celebration", "bonfire"},
		"festival_fire", new String[] {"celebration", "bonfire"},
		"marked", new String[] {"marked"},
		"grundar", new String[] {"grundar"},
		"road", new String[] {"now_we_ride", "the_road_home"},
		"war", new String[] {"behind_the_sword", "might_magic"});

	/** The place you are told you are in ("" none), when you were told, and the tick clock. */
	private static String place = "";
	private static long lastTold;
	private static long age;
	private static long nextAt;
	private static @Nullable Track current;
	private static String lastTrack = "";

	private PlaceMusic() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PlaceThemes.Play.TYPE, (payload, context) -> {
			lastTold = age;
			place = payload.place();
		});
		ClientTickEvents.END_CLIENT_TICK.register(PlaceMusic::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> hush());
	}

	private static void hush() {
		if (current != null) {
			current.end();
		}
		current = null;
		place = "";
		nextAt = age + FIRST_WAIT;
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.isPaused()) {
			return;
		}
		if (minecraft.player == null || minecraft.level == null) {
			if (current != null || !place.isEmpty()) {
				hush();
			}
			return;
		}
		age++;
		if (!place.isEmpty() && age - lastTold > SILENCE) {
			place = "";
		}
		// a track that ran out on its own is never told to stop by the sound engine: it is no longer active (not just after it starts)
		boolean over = current != null && (current.isStopped() || age - current.born > 40 && !minecraft.getSoundManager().isActive(current));
		if (current != null && (over || current.faded())) {
			nextAt = age + rest(minecraft);
			current.end();
			current = null;
		}
		if (current != null) {
			// a track of another place that is still sounding as you leave it fades out; the place's own runs on
			current.wanted = group(place).equals(current.group) && !FoeMusicClient.sounding() && SceneMusic.cover() <= 0.0F;
			if (age % DUCK_EVERY == 0 && minecraft.getMusicManager() != null) {
				minecraft.getMusicManager().stopPlaying();
			}
			return;
		}
		String[] tracks = TRACKS.get(place);
		if (tracks == null || age < nextAt || FoeMusicClient.sounding() || SceneMusic.cover() > 0.0F) {
			return;
		}
		if (minecraft.getMusicManager() != null && minecraft.getMusicManager().getCurrentMusicTranslationKey() != null) {
			// the game's own song is on: this one waits its turn, as if that song had been ours
			nextAt = age + rest(minecraft);
			return;
		}
		start(minecraft, tracks);
	}

	/** How long to rest before the next track (ticks): what the game waits between its own songs on the Music Frequency option. */
	private static int rest(Minecraft minecraft) {
		MusicManager.MusicFrequency frequency = minecraft.options.musicFrequency().get();
		if (frequency == MusicManager.MusicFrequency.CONSTANT) {
			return REST_CONSTANT;
		}
		int cap = frequency == MusicManager.MusicFrequency.FREQUENT ? 10 * 1200 : 20 * 1200;
		int min = Math.min(REST_MIN, cap), max = Math.min(REST_MAX, cap);
		return min + minecraft.level.getRandom().nextInt(max - min + 1);
	}

	/** The festival's square and its fire share their tracks. */
	private static String group(String place) {
		return place.startsWith("festival") ? "festival" : place;
	}

	/** How loud a track is where you are now. */
	private static float loudness() {
		return VOLUME * (place.equals("festival_fire") ? FIRE_LOUDER : place.startsWith("festival") ? FESTIVAL : 1.0F);
	}

	private static void start(Minecraft minecraft, String[] tracks) {
		RandomSource random = minecraft.level.getRandom();
		String pick;
		do {
			pick = tracks[random.nextInt(tracks.length)];
		} while (pick.equals(lastTrack) && tracks.length > 1);
		// without the pack there is no file: nothing is played, and the game's own music goes on
		if (minecraft.getResourceManager().getResource(Identifier.withDefaultNamespace("sounds/music/" + pick + ".ogg")).isEmpty()) {
			nextAt = age + REST_MAX;
			return;
		}
		lastTrack = pick;
		current = new Track(group(place), JaymosKingdoms.id("place_music." + pick));
		minecraft.getSoundManager().play(current);
		if (minecraft.getMusicManager() != null) {
			minecraft.getMusicManager().stopPlaying();
		}
	}

	/** One track, faded in as it starts and out when it is no longer wanted. */
	private static final class Track extends AbstractTickableSoundInstance {
		final String group;
		boolean wanted = true;
		float gain;
		final long born = age;

		Track(String group, Identifier event) {
			super(SoundEvent.createVariableRangeEvent(event), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.group = group;
			this.volume = 0.0001F;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		void end() {
			this.stop();
		}

		boolean faded() {
			return !this.wanted && this.gain <= 0.0F;
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			this.gain = Mth.clamp(this.gain + (this.wanted ? 1.0F / FADE_IN : -1.0F / FADE_OUT), 0.0F, 1.0F);
			this.volume = Math.max(0.0001F, loudness() * this.gain * this.gain);
		}
	}
}
