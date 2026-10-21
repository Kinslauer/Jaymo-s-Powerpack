package com.jaymospowers.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every power's sustained sound, one system, so nothing just loops: a sound is a stack of {@link Layer}s (a body, an
 * accent, a rare flourish), each picking one of its variants at a jittered volume and pitch every time it plays, and
 * {@link #due} spaces the plays at uneven intervals instead of every Nth tick. The surroundings answer back: a loud sound
 * in a cave or indoors (no sky light) comes back as a quieter, lower echo a few ticks later. The flier's own side of a
 * flight is a client loop that follows speed (see {@code client.sound.PowerLoop}); {@link #play} with an {@code except}
 * leaves that player out so they don't hear both.
 */
public final class Soundscape {
	/** An echo is this share of the sound's volume, this much lower, after this many ticks. */
	public static final float ECHO_SHARE = 0.35F;
	public static final float ECHO_PITCH = 0.92F;
	public static final int ECHO_DELAY_MIN = 3, ECHO_DELAY_MAX = 7;
	/** Only sounds at least this loud echo (a puff of smoke doesn't, a blast does). */
	public static final float ECHO_MIN_VOLUME = 0.8F;
	/** Sky light at or under this counts as enclosed. */
	public static final int ENCLOSED_SKY_LIGHT = 2;
	/** At most this many echoes wait at once. */
	public static final int MAX_PENDING = 256;
	/** {@link #due} timers untouched this long are dropped. */
	public static final int STALE_TICKS = 1200;

	private static final Map<Long, Long> NEXT = new HashMap<>();
	private static final Map<Long, Long> NEXT_CLIENT = new HashMap<>();
	private static final List<Echo> PENDING = new ArrayList<>();
	private static long pruneAt;

	private Soundscape() {
	}

	/**
	 * One layer of a sound: a pool of variants (one is picked each play), a volume and pitch range, and the chance it
	 * plays at all (accents and flourishes sit under 1).
	 */
	public record Layer(SoundEvent[] pool, float volumeMin, float volumeMax, float pitchMin, float pitchMax, float chance) {
		public static Layer of(float volumeMin, float volumeMax, float pitchMin, float pitchMax, SoundEvent... pool) {
			return new Layer(pool, volumeMin, volumeMax, pitchMin, pitchMax, 1.0F);
		}

		/** The same layer, playing only this share of the time. */
		public Layer chance(float chance) {
			return new Layer(this.pool, this.volumeMin, this.volumeMax, this.pitchMin, this.pitchMax, chance);
		}

		/** The same layer, its volume scaled (by how hard something is going, say). */
		public Layer louder(float factor) {
			return new Layer(this.pool, this.volumeMin * factor, this.volumeMax * factor, this.pitchMin, this.pitchMax, this.chance);
		}

		/** The same layer, its pitch scaled. */
		public Layer pitched(float factor) {
			return new Layer(this.pool, this.volumeMin, this.volumeMax, this.pitchMin * factor, this.pitchMax * factor, this.chance);
		}

		public SoundEvent pick(RandomSource random) {
			return this.pool[random.nextInt(this.pool.length)];
		}

		public float volume(RandomSource random) {
			return this.volumeMin + random.nextFloat() * (this.volumeMax - this.volumeMin);
		}

		public float pitch(RandomSource random) {
			return this.pitchMin + random.nextFloat() * (this.pitchMax - this.pitchMin);
		}
	}

	private record Echo(ServerLevel level, @Nullable Entity except, Vec3 at, SoundEvent sound, SoundSource source, float volume, float pitch,
		long when) {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) {
				return;
			}
			Iterator<Echo> it = PENDING.iterator();
			while (it.hasNext()) {
				Echo e = it.next();
				if (e.level.getGameTime() >= e.when) {
					it.remove();
					e.level.playSound(e.except, e.at.x, e.at.y, e.at.z, e.sound, e.source, e.volume, e.pitch);
				}
			}
		});
	}

	/**
	 * Whether {@code owner}'s {@code key} sound is due now: true at once the first time, then again after a random
	 * {@code min}..{@code max} ticks, so a held sound breathes instead of ticking like a clock.
	 */
	public static boolean due(Entity owner, String key, int min, int max) {
		return due(owner.level(), owner.getId(), key, min, max);
	}

	/** {@link #due} for something that isn't an entity (a field, a storm): {@code owner} is any number standing for it. */
	public static boolean due(Level level, long owner, String key, int min, int max) {
		Map<Long, Long> next = level.isClientSide() ? NEXT_CLIENT : NEXT;
		long now = level.getGameTime();
		if (now >= pruneAt) {
			pruneAt = now + STALE_TICKS;
			next.values().removeIf(t -> t < now - STALE_TICKS || t > now + STALE_TICKS * 4L);
		}
		long id = owner * 31L + key.hashCode();
		Long at = next.get(id);
		if (at != null && now < at && at - now <= max) {
			return false;
		}
		next.put(id, now + min + level.getRandom().nextInt(Math.max(1, max - min + 1)));
		return true;
	}

	/** Plays every layer at {@code at}, for everyone near. */
	public static void play(Level level, Vec3 at, SoundSource source, Layer... layers) {
		play(level, null, at, source, layers);
	}

	/**
	 * Plays every layer at {@code at} for everyone near but {@code except} (who hears their own version client-side), each
	 * layer one of its variants at a jittered volume and pitch; loud ones echo when enclosed.
	 */
	public static void play(Level level, @Nullable Entity except, Vec3 at, SoundSource source, Layer... layers) {
		RandomSource random = level.getRandom();
		boolean enclosed = level instanceof ServerLevel && enclosed(level, at);
		for (Layer layer : layers) {
			if (layer.chance < 1.0F && random.nextFloat() >= layer.chance) {
				continue;
			}
			SoundEvent sound = layer.pick(random);
			float volume = layer.volume(random);
			float pitch = layer.pitch(random);
			level.playSound(except, at.x, at.y, at.z, sound, source, volume, pitch);
			if (enclosed && volume >= ECHO_MIN_VOLUME && PENDING.size() < MAX_PENDING) {
				int delay = ECHO_DELAY_MIN + random.nextInt(ECHO_DELAY_MAX - ECHO_DELAY_MIN + 1);
				PENDING.add(new Echo((ServerLevel)level, except, at, sound, source, volume * ECHO_SHARE, pitch * ECHO_PITCH,
					level.getGameTime() + delay));
			}
		}
	}

	/** Whether {@code at} is shut in (a cave, a cellar, deep indoors): little or no sky light reaches it. */
	public static boolean enclosed(Level level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		return level.isLoaded(pos) && level.getBrightness(LightLayer.SKY, pos) <= ENCLOSED_SKY_LIGHT;
	}
}
