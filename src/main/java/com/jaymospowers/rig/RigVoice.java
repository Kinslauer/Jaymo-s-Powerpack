package com.jaymospowers.rig;

import com.google.gson.stream.JsonReader;
import com.jaymospowers.JaymosPowers;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-19), on the Google voice samples: they can be "the rig's voice". The Mind's lines are spoken aloud, each
 * personality in a Google voice of its own ({@code tools/voices/google_voices.py --rig}), heard only by the wearer. A line
 * is voiced when Powers' {@code sounds.json} lists {@code jaymospowers:voice.rig.<personality>.<line>}; lines with a
 * number or a name in them (the charge report, a lock, a scan) stay text with the chime. One line at a time: while the
 * last is still being said, the next is only shown.
 */
public final class RigVoice {
	/** How loud the Mind speaks. */
	static final float VOLUME = 0.9F;
	/** Quiet after a line before the Mind says another aloud (ticks). */
	static final int GAP = 10;

	private static final String PREFIX = "voice.rig.";
	/** Voiced line -> its length in ticks. */
	private static @Nullable Map<String, Integer> voiced;
	/** Wearer -> game time the Mind may speak aloud again. */
	private static final Map<UUID, Long> FREE = new HashMap<>();

	private RigVoice() {
	}

	/** Say {@code key} in {@code personality}'s voice to {@code player}; false if it has no clip or the Mind is still talking. */
	static boolean speak(ServerPlayer player, String personality, String key) {
		Integer length = voiced().get(personality + "." + key);
		long now = player.level().getGameTime();
		if (length == null || now < FREE.getOrDefault(player.getUUID(), Long.MIN_VALUE)) {
			return false;
		}
		FREE.put(player.getUUID(), now + length + GAP);
		Identifier id = JaymosPowers.id(PREFIX + personality + "." + key);
		Holder<SoundEvent> sound = Holder.direct(SoundEvent.createVariableRangeEvent(id));
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.VOICE, player.getX(), player.getEyeY(), player.getZ(),
			VOLUME, 1.0F, player.getRandom().nextLong()));
		return true;
	}

	static void forget(UUID player) {
		FREE.remove(player);
	}

	/** The lines with a clip and how long each is (read once, from the built {@code sounds.json} and the clips). */
	private static Map<String, Integer> voiced() {
		Map<String, Integer> known = voiced;
		if (known != null) {
			return known;
		}
		known = new HashMap<>();
		try (InputStream in = RigVoice.class.getResourceAsStream("/assets/jaymospowers/sounds.json")) {
			if (in != null) {
				JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				reader.beginObject();
				while (reader.hasNext()) {
					String name = reader.nextName();
					reader.skipValue();
					if (name.startsWith(PREFIX)) {
						String line = name.substring(PREFIX.length());
						known.put(line, length("/assets/jaymospowers/sounds/voice/rig/" + line.replace('.', '_') + ".ogg"));
					}
				}
			}
		} catch (Exception e) {
			JaymosPowers.LOGGER.warn("Couldn't read the Rig's voiced lines", e);
		}
		voiced = known;
		return known;
	}

	/** A clip's length in ticks (3 s if it can't be read): its last Ogg page's sample count over the Vorbis rate. */
	private static int length(String file) {
		try (InputStream in = RigVoice.class.getResourceAsStream(file)) {
			if (in != null) {
				byte[] b = in.readAllBytes();
				int rate = 0;
				for (int i = 0; i + 16 < b.length && i < 4096; i++) {
					if (b[i] == 1 && b[i + 1] == 'v' && b[i + 2] == 'o' && b[i + 3] == 'r' && b[i + 4] == 'b' && b[i + 5] == 'i' && b[i + 6] == 's') {
						rate = (int) le(b, i + 12, 4);
						break;
					}
				}
				for (int i = b.length - 14; rate > 0 && i >= 0; i--) {
					if (b[i] == 'O' && b[i + 1] == 'g' && b[i + 2] == 'g' && b[i + 3] == 'S') {
						return (int) Math.ceil(le(b, i + 6, 8) * 20.0 / rate);
					}
				}
			}
		} catch (Exception ignored) {
			// (the default below)
		}
		return 60;
	}

	private static long le(byte[] b, int at, int n) {
		long v = 0;
		for (int k = n - 1; k >= 0; k--) {
			v = (v << 8) | (b[at + k] & 0xFF);
		}
		return v;
	}
}
