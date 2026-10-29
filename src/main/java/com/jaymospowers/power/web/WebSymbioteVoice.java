package com.jaymospowers.power.web;

import com.google.gson.stream.JsonReader;
import com.jaymospowers.JaymosPowers;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.183) Andy: "Can you add a snarling visceral voice to the symbiotes?" Its combat calls are spoken aloud: every
 * {@code say.*} line (Andy picked these first; its moods' {@code mood.say.*} variants are still text only), made with
 * VoxCPM2 (a voice designed from a description, no real person cloned) by {@code tools/voices/make_voices.py} and
 * roughened there. A line is voiced when Powers' {@code sounds.json} lists
 * {@code jaymospowers:voice.symbiote.<rest of its lang key>}, so the script can make the clips a few at a time. Only the
 * host hears it, at their head, a touch lower for the black strain and quicker for the red.
 */
public final class WebSymbioteVoice {
	/** Each strain's pitch: black lowest (the deepest snarl), red wilder and quicker, white nearest the clip. */
	static final float BLACK_PITCH = 0.88F, RED_PITCH = 1.06F, WHITE_PITCH = 0.97F;
	/** How loud it is, and how much each line's pitch wanders. */
	static final float VOLUME = 1.0F, PITCH_SPREAD = 0.04F;

	private static final String PREFIX = "voice.symbiote.";
	private static @Nullable Set<String> voiced;

	private WebSymbioteVoice() {
	}

	/** Say {@code line} (the lang key's rest after {@code message.jaymospowers.web_symbiote.}) aloud, if it has a clip. */
	static void speak(ServerPlayer player, String line) {
		if (!voiced().contains(line)) {
			return;
		}
		Identifier id = JaymosPowers.id(PREFIX + line);
		Holder<SoundEvent> sound = Holder.direct(SoundEvent.createVariableRangeEvent(id));
		float pitch = switch (WebSymbiote.state(player).kind()) {
			case RED -> RED_PITCH;
			case WHITE -> WHITE_PITCH;
			default -> BLACK_PITCH;
		} + (player.getRandom().nextFloat() - 0.5F) * PITCH_SPREAD;
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.VOICE, player.getX(), player.getEyeY(), player.getZ(),
			VOLUME, pitch, player.getRandom().nextLong()));
	}

	/** The lines with a clip (read once, from the built {@code sounds.json}). */
	private static Set<String> voiced() {
		Set<String> known = voiced;
		if (known != null) {
			return known;
		}
		known = new HashSet<>();
		try (InputStream in = WebSymbioteVoice.class.getResourceAsStream("/assets/jaymospowers/sounds.json")) {
			if (in != null) {
				JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				reader.beginObject();
				while (reader.hasNext()) {
					String name = reader.nextName();
					reader.skipValue();
					if (name.startsWith(PREFIX)) {
						known.add(name.substring(PREFIX.length()));
					}
				}
			}
		} catch (Exception e) {
			JaymosPowers.LOGGER.warn("Couldn't read the symbiote's voiced lines", e);
		}
		voiced = known;
		return known;
	}
}
