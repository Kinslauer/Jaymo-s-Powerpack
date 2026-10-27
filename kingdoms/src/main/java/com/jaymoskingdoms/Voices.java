package com.jaymoskingdoms;

import java.util.ArrayList;
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
 * lang key is in {@link #voiced()}; its sound is {@code jaymoskingdoms:voice.<speaker>.<rest of the key>}, the speaker being
 * {@code narrator} for {@code story.jaymoskingdoms.*} and the spirit's id for {@code spirit.jaymoskingdoms.<id>.*}. The
 * text shows anyway (chat, the story page; a question's grey line is only heard since 0.7.256), so the sounds carry no
 * subtitles.
 */
public final class Voices {
	/** The spirits' voiced lines. */
	static final List<String> SPIRIT_LINES = List.of(
		"spirit.jaymoskingdoms.grimsby.summon.1", "spirit.jaymoskingdoms.grimsby.summon.2", "spirit.jaymoskingdoms.grimsby.summon.3",
		"spirit.jaymoskingdoms.varro.summon.1", "spirit.jaymoskingdoms.varro.summon.2", "spirit.jaymoskingdoms.varro.summon.3",
		"spirit.jaymoskingdoms.ambrose.summon.1", "spirit.jaymoskingdoms.ambrose.summon.2", "spirit.jaymoskingdoms.ambrose.summon.3",
		"spirit.jaymoskingdoms.thessaly.summon.1", "spirit.jaymoskingdoms.thessaly.summon.2", "spirit.jaymoskingdoms.thessaly.summon.3",
		"spirit.jaymoskingdoms.quiet.word.1");
	/** (0.7.256) The story's closing line, read after the whole tale (its text is gui.jaymoskingdoms.story.summary_sub). */
	public static final String STORY_END = "story.jaymoskingdoms.summary.end";

	/** How loud a spirit is: it speaks at its keeper's shoulder. */
	static final float SPIRIT_VOLUME = 1.0F;

	private static final Map<String, Holder<SoundEvent>> SOUNDS = new HashMap<>();

	private Voices() {
	}

	/**
	 * (0.7.256) Every voiced lang key. Andy: "voice the rest of the intro, remove the grey text from showing but make sure
	 * the voice still reads it. and make it read out the entire story at the end." So the narrator has each question
	 * ({@code <q>.title}: the title and the line under it), each realm page ({@code realm.<answer>}: "Which realm?" and its
	 * line), each answer's tale, and {@link #STORY_END}. {@code tools/voices/make_voices.py} makes a clip for each by the
	 * same rule.
	 */
	static List<String> voiced() {
		List<String> out = new ArrayList<>(SPIRIT_LINES);
		for (LifeStory.Question q : LifeStory.Question.values()) {
			out.add("story.jaymoskingdoms." + q.key() + ".title");
			for (LifeStory.Answer a : q.answers()) {
				out.add("story.jaymoskingdoms." + q.key() + "." + a.key() + ".tale");
				if (a.realm()) {
					out.add("story.jaymoskingdoms.realm." + a.key());
				}
			}
		}
		out.add(STORY_END);
		return out;
	}

	public static void register() {
		for (String key : voiced()) {
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
