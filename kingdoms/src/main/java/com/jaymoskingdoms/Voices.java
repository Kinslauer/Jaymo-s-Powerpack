package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
 * text shows anyway (chat, the story page; a question's grey line is only heard since 0.7.269), so the sounds carry no
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
	/** (0.7.269) The story's closing line, read after the whole tale (its text is gui.jaymoskingdoms.story.summary_sub). */
	public static final String STORY_END = "story.jaymoskingdoms.summary.end";
	/**
	 * (0.7.342) Andy: "redo the intro lines that dont already have lines". The pages the narrator was still silent on: who
	 * you are (the ask and its grey line, now only heard), the skip-the-story ask, and the hold screen's lines.
	 */
	public static final List<String> INTRO_LINES = List.of(
		"story.jaymoskingdoms.gender.title", "story.jaymoskingdoms.gender.desc",
		"gui.jaymoskingdoms.story.skip.ask", "gui.jaymoskingdoms.story.skip.desc",
		"gui.jaymoskingdoms.story.hold.title", "gui.jaymoskingdoms.story.hold.realm", "gui.jaymoskingdoms.story.hold.harbour",
		"gui.jaymoskingdoms.story.hold.future", "gui.jaymoskingdoms.story.hold.arriving");

	/** How loud a spirit is: it speaks at its keeper's shoulder. */
	static final float SPIRIT_VOLUME = 1.0F;

	/**
	 * (0.7.280) Server to the keeper: a spirit says the line {@code key}. Andy: "the voices will fade if you are moving while
	 * they activate. they should emit from behind you." A sound packet plays where it was sent from and stays there, so the
	 * client plays this one itself, from the spirit behind you, following you ({@code SpiritVoiceSound}).
	 */
	public record Spoken(String key) implements CustomPacketPayload {
		public static final Type<Spoken> TYPE = new Type<>(JaymosKingdoms.id("spirit_voice"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Spoken> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeUtf(p.key), buf -> new Spoken(buf.readUtf()));

		@Override
		public Type<Spoken> type() {
			return TYPE;
		}
	}

	/**
	 * (0.7.348) Andy: "a distinct greeting, farewell, celebration, and scream in every kingdom/biome's language when you
	 * dont speak it." What a villager says in their own tongue to someone who doesn't share it, each said by a man's voice
	 * and a woman's: MCA's hello, cheer and scream ({@link Languages#voice}), and a goodbye when you leave their talk menu
	 * ({@link Languages#farewell}). The words are in the lang file as {@code tongue.jaymoskingdoms.<tongue>.<line>}; the
	 * sound is {@code voice.tongue.<tongue>.<male|female>.<line>}, registered only once its clip is in the jar (the script
	 * lists only made clips in sounds.json too), so a line not yet recorded is a grunt, as before.
	 */
	public static final List<String> TONGUE_LINES = List.of("greet", "farewell", "celebrate", "scream");

	private static final Map<String, Holder<SoundEvent>> SOUNDS = new HashMap<>();
	/** (0.7.348) {@code <tongue>.<male|female>.<line>} -> its sound. */
	private static final Map<String, Holder<SoundEvent>> TONGUES = new HashMap<>();

	private Voices() {
	}

	/**
	 * (0.7.269) Every voiced lang key. Andy: "voice the rest of the intro, remove the grey text from showing but make sure
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
		out.addAll(INTRO_LINES);
		return out;
	}

	public static void register() {
		for (String key : voiced()) {
			Identifier id = Identifier.fromNamespaceAndPath(JaymosKingdoms.MOD_ID, soundPath(key));
			SOUNDS.put(key, Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id)));
		}
		for (Languages.Tongue t : Languages.Tongue.values()) {
			for (String gender : new String[] {"male", "female"}) {
				for (String line : TONGUE_LINES) {
					String rest = t.id() + "." + gender + "." + line;
					if (Voices.class.getResource("/assets/jaymoskingdoms/sounds/voice/tongue/" + rest.replace('.', '_') + ".ogg") == null) {
						// (not recorded yet: a grunt is heard instead, as before)
						continue;
					}
					Identifier id = JaymosKingdoms.id("voice.tongue." + rest);
					TONGUES.put(rest, Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id)));
				}
			}
		}
		PayloadTypeRegistry.clientboundPlay().register(Spoken.TYPE, Spoken.CODEC);
	}

	/** (0.7.348) {@code line} ({@link #TONGUE_LINES}) said in {@code tongue} by a woman or a man; null for a line it has none of. */
	public static @Nullable Holder<SoundEvent> tongue(Languages.Tongue tongue, boolean female, String line) {
		return TONGUES.get(tongue.id() + "." + (female ? "female" : "male") + "." + line);
	}

	/**
	 * {@code story.jaymoskingdoms.born.title} -> {@code voice.narrator.born.title}; {@code gui.jaymoskingdoms.story.skip.ask}
	 * -> {@code voice.narrator.skip.ask}; {@code spirit.jaymoskingdoms.varro.summon.1} -> {@code voice.varro.summon.1}.
	 */
	static String soundPath(String key) {
		if (key.startsWith("story.jaymoskingdoms.")) {
			return "voice.narrator." + key.substring("story.jaymoskingdoms.".length());
		}
		if (key.startsWith("gui.jaymoskingdoms.story.")) {
			return "voice.narrator." + key.substring("gui.jaymoskingdoms.story.".length());
		}
		return "voice." + key.substring("spirit.jaymoskingdoms.".length());
	}

	/** The voice of the line {@code key}, if it has one. */
	public static @Nullable Holder<SoundEvent> of(String key) {
		return SOUNDS.get(key);
	}

	/** Speak {@code key} to {@code player} alone (a spirit's line: only its keeper hears it), from behind them and following them. */
	public static void speak(ServerPlayer player, String key) {
		Holder<SoundEvent> sound = of(key);
		if (sound == null) {
			return;
		}
		if (ServerPlayNetworking.canSend(player, Spoken.TYPE)) {
			ServerPlayNetworking.send(player, new Spoken(key));
		} else {
			player.connection.send(new ClientboundSoundPacket(sound, SoundSource.VOICE, player.getX(), player.getEyeY(), player.getZ(),
				SPIRIT_VOLUME, 1.0F, player.getRandom().nextLong()));
		}
	}
}
