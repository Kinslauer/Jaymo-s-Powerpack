package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.251) Spoken lines. Andy (2026-10-27) chose a test first: the narrator reads the first story question and every spirit
 * speaks its three summon lines (and the Quiet One its one word). The clips are made with VoxCPM2 (Apache-2.0, voices
 * designed from a description, no real person cloned) by {@code tools/voices/make_voices.py}. A line is voiced when its
 * lang key is in {@link #VOICED}; its sound is {@code jaymoskingdoms:voice.<speaker>.<rest of the key>}, the speaker being
 * {@code narrator} for {@code story.jaymoskingdoms.*} and the spirit's id for {@code spirit.jaymoskingdoms.<id>.*}. The
 * text still shows (chat, the story page), so the sounds carry no subtitles.
 */
public final class Voices {
	/** The lang keys that have a voice. */
	static final List<String> VOICED = List.of(
		"story.jaymoskingdoms.born.title",
		"spirit.jaymoskingdoms.grimsby.summon.1", "spirit.jaymoskingdoms.grimsby.summon.2", "spirit.jaymoskingdoms.grimsby.summon.3",
		"spirit.jaymoskingdoms.varro.summon.1", "spirit.jaymoskingdoms.varro.summon.2", "spirit.jaymoskingdoms.varro.summon.3",
		"spirit.jaymoskingdoms.ambrose.summon.1", "spirit.jaymoskingdoms.ambrose.summon.2", "spirit.jaymoskingdoms.ambrose.summon.3",
		"spirit.jaymoskingdoms.thessaly.summon.1", "spirit.jaymoskingdoms.thessaly.summon.2", "spirit.jaymoskingdoms.thessaly.summon.3",
		"spirit.jaymoskingdoms.quiet.word.1");
	/** How loud a spirit is: it speaks at its keeper's shoulder. */
	static final float SPIRIT_VOLUME = 1.0F;

	private static final Map<String, Holder<SoundEvent>> SOUNDS = new HashMap<>();

	private Voices() {
	}

	public static void register() {
		for (String key : VOICED) {
			Identifier id = Identifier.fromNamespaceAndPath(JaymosKingdoms.MOD_ID, soundPath(key));
			SOUNDS.put(key, Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id)));
		}
	}

	/** {@code story.jaymoskingdoms.born.title} -> {@code voice.narrator.born.title}; {@code spirit.jaymoskingdoms.varro.summon.1} -> {@code voice.varro.summon.1}. */
	static String soundPath(String key) {
		if (key.startsWith("story.jaymoskingdoms.")) {
			return "voice.narrator." + key.substring("story.jaymoskingdoms.".length());
		}
		return "voice." + key.substring("spirit.jaymoskingdoms.".length());
	}

	/** The voice of the line {@code key}, if it has one. */
	public static @Nullable Holder<SoundEvent> of(String key) {
		return SOUNDS.get(key);
	}

	/** Speak {@code key} to {@code player} alone (a spirit's line: only its keeper hears it). */
	public static void speak(ServerPlayer player, String key) {
		Holder<SoundEvent> sound = of(key);
		if (sound != null) {
			player.connection.send(new ClientboundSoundPacket(sound, SoundSource.VOICE, player.getX(), player.getEyeY(), player.getZ(),
				SPIRIT_VOLUME, 1.0F, player.getRandom().nextLong()));
		}
	}
}
