package com.jaymospowers.client.sound;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.ScreenShake;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

/**
 * (0.20.56) Andy: "i really like the screen shake and sound effects from [Create Big Cannons]". Its ideas, our own code
 * and sounds (its code is MIT, its sound files aren't licensed for reuse). Every blast you hear (any explosion, a cannon's
 * report, anything that plays the explosion sound, from any mod):
 * <ul>
 * <li><b>takes time to reach you.</b> Sound runs out at {@link #SPEED} blocks a tick, so far off you see the flash
 * first and the boom, and its shake, come a beat later.</li>
 * <li><b>is muffled by distance.</b> The air soaks up its high end ({@link #AIR_ABSORPTION}, OpenAL's air
 * absorption), so a far blast is a dull thud, and past {@link #FAR_FROM} blocks a long low rumble ({@code blast.far})
 * rolls in under it, heard much farther than the vanilla boom. Close by, a sharp crack and a patter of debris
 * ({@code blast.crack}) sits on top.</li>
 * <li><b>shakes you</b> when it reaches you: an explosion by its size ({@link ScreenShake#explosion}), any other blast
 * sound by how loud it was played.</li>
 * </ul>
 * Hooked in at {@code SoundEngine.play} ({@code BlastSoundEngineMixin}); explosions tell us their size first
 * ({@code ScreenShakeExplosionMixin}).
 */
public final class Blasts {
	/** How fast sound runs (blocks a tick: about 340 m/s). */
	static final double SPEED = 17.0;
	/** How hard the air dulls a blast's high end over distance (OpenAL's air absorption factor, 0-10). */
	static final float AIR_ABSORPTION = 4.0F;
	/** The near crack: heard this far (blocks). */
	static final double CRACK_REACH = 40.0;
	/** The far rumble: from this far out, heard this far for an ordinary blast (blocks, times its size, at most FAR_MAX). */
	static final double FAR_FROM = 20.0, FAR_REACH = 150.0, FAR_MAX = 260.0;
	/** A blast sound with no explosion behind it: degrees of shake for each unit of volume (at most SOUND_SHAKE_MAX), felt this many blocks per unit of volume. */
	static final float SOUND_SHAKE_PER_VOLUME = 0.8F, SOUND_SHAKE_MAX = 3.5F;
	static final double SOUND_SHAKE_REACH_PER_VOLUME = 12.0;
	/** Shakes sent by the server from farther than this (blocks) wait for their sound too. */
	static final double DELAY_SHAKES_FROM = 24.0;
	/** An explosion's sound is matched to it if it plays within this many blocks of it. */
	static final double SAME_BLAST = 1.0;

	/** The sounds that count as blasts. */
	static final Set<Identifier> BLASTS = Set.of(
		Identifier.withDefaultNamespace("entity.generic.explode"),
		Identifier.withDefaultNamespace("entity.dragon_fireball.explode"),
		JaymosPowers.id("blast.report"));
	static final Identifier CRACK = JaymosPowers.id("blast.crack"), FAR = JaymosPowers.id("blast.far"), WHISTLE = JaymosPowers.id("blast.whistle");

	/** Sounds we've already seen to (delayed, or a layer we added): let through as they are. */
	private static final Set<SoundInstance> SEEN = Collections.newSetFromMap(new WeakHashMap<>());
	private static final List<Later> LATER = new ArrayList<>();
	private static int clock;
	/** The explosion being handled right now (its sound is about to play), and whether its sound has been seen. */
	private static @Nullable Vec3 explosionAt;
	private static float explosionRadius;
	private static boolean explosionHeard;

	private record Later(int due, Runnable run) {
	}

	private Blasts() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null) {
				LATER.clear();
				return;
			}
			if (minecraft.isPaused()) {
				return;
			}
			clock++;
			for (int i = 0; i < LATER.size(); i++) {
				Later l = LATER.get(i);
				if (l.due <= clock) {
					LATER.remove(i--);
					l.run.run();
				}
			}
		});
	}

	/** Runs {@code run} after {@code ticks} (now if 0). */
	public static void later(int ticks, Runnable run) {
		if (ticks <= 0) {
			run.run();
		} else {
			LATER.add(new Later(clock + ticks, run));
		}
	}

	/** How many ticks a sound from {@code at} takes to reach the listener. */
	public static int delayFrom(Vec3 at) {
		Vec3 ear = ear();
		return ear == null ? 0 : (int)(ear.distanceTo(at) / SPEED);
	}

	/** A shake sent by the server from {@code from}: a far one waits for its sound. */
	public static int shakeDelay(@Nullable Vec3 from) {
		Vec3 ear = ear();
		if (from == null || ear == null || ear.distanceTo(from) < DELAY_SHAKES_FROM) {
			return 0;
		}
		return (int)(ear.distanceTo(from) / SPEED);
	}

	private static @Nullable Vec3 ear() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.gameRenderer == null ? null : minecraft.gameRenderer.mainCamera().position();
	}

	// ---- explosions (ScreenShakeExplosionMixin) ----------------------------------------------------------------------

	public static void explosionComing(Vec3 at, float radius) {
		explosionAt = at;
		explosionRadius = radius;
		explosionHeard = false;
	}

	/** The explosion's packet is done: if its sound never played (a silent one), it still shakes, when it would have been heard. */
	public static void explosionDone() {
		Vec3 at = explosionAt;
		float radius = explosionRadius;
		if (at != null && !explosionHeard) {
			later(delayFrom(at), () -> ScreenShake.explosion(at, radius));
		}
		explosionAt = null;
	}

	// ---- every sound (BlastSoundEngineMixin) -------------------------------------------------------------------------

	/** Whether this sound has its high end dulled by distance (a blast, or one of our layers). */
	public static boolean muffled(SoundInstance sound) {
		Identifier id = sound.getIdentifier();
		return BLASTS.contains(id) || id.equals(CRACK) || id.equals(FAR) || id.equals(WHISTLE);
	}

	/** Called as a sound's channel is set up: a blast's high end is dulled by the air. */
	public static void absorb(int source) {
		try {
			AL10.alSourcef(source, EXTEfx.AL_AIR_ABSORPTION_FACTOR, AIR_ABSORPTION);
			AL10.alGetError();
		} catch (RuntimeException | LinkageError ignored) {
			// no EFX here: blasts aren't muffled
		}
	}

	/**
	 * A sound is about to play, heard at {@code ear}. A blast is held back until it would reach you (true: the engine
	 * plays it later, not now), its layers are added, and its shake is set to arrive with it.
	 */
	public static boolean onPlay(SoundInstance sound, Vec3 ear) {
		if (SEEN.remove(sound) || sound.isRelative() || sound.getAttenuation() != SoundInstance.Attenuation.LINEAR
			|| !BLASTS.contains(sound.getIdentifier())) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		// (0.20.64) resolved first, as the engine does: getVolume() reads the resolved Sound, and this runs before the
		// engine resolves it, so every explosion sound threw and disconnected you ("Failed to handle packet
		// ClientboundExplodePacket"). getOrResolve keeps the variant it picks, so the engine plays the same one.
		if (minecraft.level == null || sound.getOrResolve(minecraft.getSoundManager()) == null) {
			return false;
		}
		Vec3 at = new Vec3(sound.getX(), sound.getY(), sound.getZ());
		double d = ear.distanceTo(at);
		int delay = (int)(d / SPEED);
		float volume = sound.getVolume();
		// an explosion's own sound: we know how big it was
		float radius = -1.0F;
		Vec3 explosion = explosionAt;
		if (explosion != null && !explosionHeard && explosion.distanceToSqr(at) < SAME_BLAST * SAME_BLAST) {
			explosionHeard = true;
			radius = explosionRadius;
		}
		float size = radius > 0.0F ? Mth.clamp(radius / 4.0F, 0.5F, 2.0F) : Mth.clamp(volume / 3.0F, 0.4F, 1.5F);
		RandomSource random = minecraft.level.getRandom();
		if (d < CRACK_REACH) {
			layer(CRACK, sound.getSource(), (float)(CRACK_REACH / 16.0), (0.85F + random.nextFloat() * 0.25F) / (float)Math.sqrt(size), at, delay, random);
		}
		if (d > FAR_FROM) {
			double reach = Math.min(FAR_MAX, FAR_REACH * size);
			if (d < reach) {
				layer(FAR, sound.getSource(), (float)(reach / 16.0), (0.8F + random.nextFloat() * 0.2F) / (float)Math.sqrt(size), at, delay, random);
			}
		}
		// its shake, when it gets here
		if (radius > 0.0F) {
			float r = radius;
			later(delay, () -> ScreenShake.explosion(at, r));
		} else {
			double reach = SOUND_SHAKE_REACH_PER_VOLUME * volume;
			if (d < reach) {
				float strength = Math.min(SOUND_SHAKE_MAX, SOUND_SHAKE_PER_VOLUME * volume)
					* (float)Math.pow(1.0 - d / reach, com.jaymospowers.core.Shakes.FALLOFF_POWER);
				later(delay, () -> ScreenShake.add(strength, com.jaymospowers.core.Shakes.ticksFor(strength), at));
			}
		}
		if (delay >= 1) {
			SEEN.add(sound);
			minecraft.getSoundManager().playDelayed(sound, delay);
			return true;
		}
		return false;
	}

	/** One of our layers, at the blast, reaching {@code volume * 16} blocks, after {@code delay} ticks. */
	private static void layer(Identifier id, SoundSource source, float volume, float pitch, Vec3 at, int delay, RandomSource random) {
		SoundInstance sound = new SimpleSoundInstance(id, source, volume, Mth.clamp(pitch, 0.5F, 2.0F),
			RandomSource.create(random.nextLong()), false, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false);
		SEEN.add(sound);
		if (delay >= 1) {
			Minecraft.getInstance().getSoundManager().playDelayed(sound, delay);
		} else {
			Minecraft.getInstance().getSoundManager().play(sound);
		}
	}
}
