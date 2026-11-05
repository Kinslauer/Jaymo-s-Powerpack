package com.jaymoskingdoms;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;

/**
 * (0.7.569) Andy: "voice lines from npcs are too loud and layer like the old intro did." A villager's spoken lines (MCA's
 * hello, yes, no, cheer... and our lines in their tongue) now go one at a time: a villager says nothing new until its last
 * line has finished and {@link #SPEAKER_GAP} has passed, and a player hears one villager's line at a time (another that
 * starts while one is still playing to them is not sent to them). Lines are played quieter too ({@link #TONGUE_VOLUME},
 * {@link #WORDS_VOLUME}).
 */
public final class VoiceGate {
	/** How loud a line in a villager's tongue is played (the recorded clips are much louder than MCA's and vanilla's). */
	static final float TONGUE_VOLUME = 0.4F;
	/** How loud MCA's own words and the vanilla grunt in their place are played. */
	static final float WORDS_VOLUME = 0.7F;
	/** Quiet after a villager's line before it may say another (ticks). */
	static final int SPEAKER_GAP = 20 * 4;
	/** How long a line we can't measure (MCA's own clips) is taken to last (ticks). */
	static final int DEFAULT_LENGTH = 20 * 3;

	/** Villager -> server tick it may speak again. */
	private static final Map<UUID, Integer> SPEAKER_FREE = new HashMap<>();
	/** Player -> server tick the line they're hearing ends. */
	private static final Map<UUID, Integer> LISTENER_FREE = new HashMap<>();
	/** Sound id -> its clip's length in ticks (ours only; measured from the .ogg). */
	private static final Map<Identifier, Integer> LENGTHS = new HashMap<>();

	private VoiceGate() {
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LISTENER_FREE.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SPEAKER_FREE.clear();
			LISTENER_FREE.clear();
		});
	}

	/** Whether {@code speaker} is still saying a line, or resting after one. */
	static boolean busy(Entity speaker, int now) {
		Integer free = SPEAKER_FREE.get(speaker.getUUID());
		if (free != null && now >= free) {
			SPEAKER_FREE.remove(speaker.getUUID());
			return false;
		}
		return free != null;
	}

	/** {@code speaker} starts a line {@code length} ticks long. */
	static void spoke(Entity speaker, int now, int length) {
		SPEAKER_FREE.put(speaker.getUUID(), now + length + SPEAKER_GAP);
	}

	/** Whether {@code player} is still hearing another villager's line. */
	static boolean hearing(ServerPlayer player, int now) {
		Integer free = LISTENER_FREE.get(player.getUUID());
		return free != null && now < free;
	}

	/** {@code player} starts hearing a line {@code length} ticks long. */
	static void heard(ServerPlayer player, int now, int length) {
		LISTENER_FREE.put(player.getUUID(), now + length);
	}

	/** How long {@code sound} lasts in ticks: measured for our own clips, {@link #DEFAULT_LENGTH} for anything else. */
	static int length(Holder<SoundEvent> sound) {
		Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(sound.value());
		if (id == null || !id.getNamespace().equals(JaymosKingdoms.MOD_ID)) {
			return DEFAULT_LENGTH;
		}
		return LENGTHS.computeIfAbsent(id, VoiceGate::measure);
	}

	/** {@code jaymoskingdoms:voice.tongue.common.male.greet} -> the length of {@code sounds/voice/tongue/common_male_greet.ogg}. */
	private static int measure(Identifier id) {
		String path = id.getPath();
		if (!path.startsWith("voice.tongue.")) {
			return DEFAULT_LENGTH;
		}
		String file = "/assets/jaymoskingdoms/sounds/voice/tongue/" + path.substring("voice.tongue.".length()).replace('.', '_') + ".ogg";
		try (InputStream in = VoiceGate.class.getResourceAsStream(file)) {
			if (in == null) {
				return DEFAULT_LENGTH;
			}
			double seconds = oggSeconds(in.readAllBytes());
			return seconds > 0 ? (int) Math.ceil(seconds * 20) : DEFAULT_LENGTH;
		} catch (IOException e) {
			return DEFAULT_LENGTH;
		}
	}

	/** An Ogg Vorbis file's length: the last page's granule position (samples) over the sample rate in the ident header. */
	static double oggSeconds(byte[] b) {
		int rate = 0;
		for (int i = 0; i + 16 < b.length && i < 4096; i++) {
			if (b[i] == 1 && b[i + 1] == 'v' && b[i + 2] == 'o' && b[i + 3] == 'r' && b[i + 4] == 'b' && b[i + 5] == 'i' && b[i + 6] == 's') {
				// packet type, "vorbis", version (4), channels (1), then the rate (4, little-endian)
				rate = le(b, i + 12, 4).intValue();
				break;
			}
		}
		if (rate <= 0) {
			return -1;
		}
		for (int i = b.length - 14; i >= 0; i--) {
			if (b[i] == 'O' && b[i + 1] == 'g' && b[i + 2] == 'g' && b[i + 3] == 'S') {
				long granule = le(b, i + 6, 8);
				return granule > 0 ? (double) granule / rate : -1;
			}
		}
		return -1;
	}

	private static Long le(byte[] b, int at, int n) {
		long v = 0;
		for (int k = n - 1; k >= 0; k--) {
			v = (v << 8) | (b[at + k] & 0xFF);
		}
		return v;
	}
}
