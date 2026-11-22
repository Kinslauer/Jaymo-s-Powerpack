package com.jaymoskingdoms.client.music;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.music.Skirmishes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The music of an ordinary fight ({@link Skirmishes} says when one is on): one of the Medieval Music pack's war tracks
 * ({@link #TRACKS}) fades in over {@link #FADE_IN} ticks as the fight starts and out over {@link #FADE_OUT} after it ends.
 * If the fight flares up again while the track is fading, it comes back up.
 * <ul>
 * <li>One track per fight: a track that runs out (or reaches {@link PlaceMusic#LONGEST}) is not followed by another until
 * the fight is over. After it, the next fight's music waits {@link #COOLDOWN} ticks.</li>
 * <li>A great foe's theme ({@link FoeMusicClient}) owns the fight it is in: no fight music starts for it, nor in the calm
 * after it. A scene's music ({@link SceneMusic}) silences this too.</li>
 * <li>{@link SoundSource#MUSIC}, so the Music slider rules it; the game's own music is stopped every {@link #DUCK_EVERY}
 * ticks while it sounds, and the music of the places ({@link PlaceMusic}) fades under it and rests after it.</li>
 * <li>Without the pack there is no file, and nothing plays.</li>
 * </ul>
 */
public final class SkirmishMusic {
	static final int FADE_IN = 40, FADE_OUT = 60, DUCK_EVERY = 20, SILENCE = Skirmishes.REPEAT_TICKS * 2 + 20;
	/** How long after one fight's music ends before another fight's may start (ticks). */
	static final int COOLDOWN = 600;
	static final float VOLUME = 1.0F;
	static final String[] TRACKS = {"behind_the_sword", "might_magic"};

	private static boolean on;
	private static long lastTold;
	private static long age;
	private static long readyAt;
	/** This fight has had its music (or belonged to a great foe): no more until it is over. */
	private static boolean spent;
	private static @Nullable Track current;
	private static String lastTrack = "";

	private SkirmishMusic() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Skirmishes.Play.TYPE, (payload, context) -> {
			lastTold = age;
			on = payload.on();
		});
		ClientTickEvents.END_CLIENT_TICK.register(SkirmishMusic::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> hush());
	}

	/** A fight's music sounds now. */
	public static boolean sounding() {
		return current != null;
	}

	private static void hush() {
		if (current != null) {
			current.stopNow();
		}
		current = null;
		on = false;
		spent = false;
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.isPaused()) {
			return;
		}
		if (minecraft.player == null || minecraft.level == null) {
			if (current != null || on) {
				hush();
			}
			return;
		}
		age++;
		if (on && age - lastTold > SILENCE) {
			on = false;
		}
		if (!on) {
			spent = false;
		} else if (current == null && FoeMusicClient.sounding()) {
			spent = true;
		}
		boolean over = current != null && (current.isStopped() || age - current.born > 40 && !minecraft.getSoundManager().isActive(current));
		if (current != null && (over || current.faded())) {
			current.stopNow();
			current = null;
			readyAt = age + COOLDOWN;
			spent = on;
		}
		if (current != null) {
			current.wanted = on && !FoeMusicClient.sounding() && SceneMusic.cover() <= 0.0F && age - current.born < PlaceMusic.LONGEST;
			current.fadeTicks = age - current.born < PlaceMusic.LONGEST ? FADE_OUT : PlaceMusic.LONGEST_FADE;
			if (age % DUCK_EVERY == 0 && minecraft.getMusicManager() != null) {
				minecraft.getMusicManager().stopPlaying();
			}
			return;
		}
		if (on && !spent && age >= readyAt && !FoeMusicClient.sounding() && SceneMusic.cover() <= 0.0F) {
			start(minecraft);
		}
	}

	private static void start(Minecraft minecraft) {
		String pick;
		do {
			pick = TRACKS[minecraft.level.getRandom().nextInt(TRACKS.length)];
		} while (pick.equals(lastTrack));
		if (minecraft.getResourceManager().getResource(Identifier.withDefaultNamespace("sounds/music/" + pick + ".ogg")).isEmpty()) {
			spent = true;
			return;
		}
		lastTrack = pick;
		current = new Track(JaymosKingdoms.id("place_music." + pick));
		minecraft.getSoundManager().play(current);
		if (minecraft.getMusicManager() != null) {
			minecraft.getMusicManager().stopPlaying();
		}
	}

	/** One track, faded in as it starts and out when it is no longer wanted. */
	private static final class Track extends AbstractTickableSoundInstance {
		boolean wanted = true;
		int fadeTicks = FADE_OUT;
		float gain;
		final long born = age;

		Track(Identifier event) {
			super(SoundEvent.createVariableRangeEvent(event), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.volume = 0.0001F;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		boolean faded() {
			return !this.wanted && this.gain <= 0.0F;
		}

		void stopNow() {
			this.stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			this.gain = Mth.clamp(this.gain + (this.wanted ? 1.0F / FADE_IN : -1.0F / this.fadeTicks), 0.0F, 1.0F);
			this.volume = Math.max(0.0001F, VOLUME * this.gain * this.gain);
		}
	}
}
