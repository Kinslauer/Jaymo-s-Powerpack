package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.330) Proximity chat. Andy (2026-10-29): "I also want to add proximity chat", pointing at Proximity Text Chat (all
 * rights reserved: the idea only, none of its code), with Simple Voice Chat, Voice Physics and Simple Voice Voice Changer
 * shipped in the pack for voice ({@link VoiceMasks}). Typed chat is words said aloud: only players near enough hear them.
 * <ul>
 * <li><b>Talk</b>: heard whole within {@link #TALK}, then words drop out ({@code ...}) and the line greys until
 * {@link #TALK_FADE}, past which nothing.</li>
 * <li><b>Whisper</b>: chat while sneaking ({@link #WHISPER}); the downed can only whisper.</li>
 * <li><b>Shout</b>: start the line with {@code !} ({@link #SHOUT}).</li>
 * <li><b>Act</b>: start it with {@code *} ("*waves" shows as "* Aldric waves"), seen as far as talk.</li>
 * <li>Walls muffle: with no line of sight the speaker counts as {@link #MUFFLED} times as far.</li>
 * <li>Names as the hearer knows them ({@link Introductions}): "Stranger" until introduced.</li>
 * <li>A werewolf in full beast form only growls, and a Great Ape roars, to everyone but their own kind.</li>
 * <li>Each hearer also sees their version as a bubble over the speaker ({@link Speech.Said}).</li>
 * <li>{@code /ooc <words>}: out of character, to everyone, for things that aren't said in the world.</li>
 * </ul>
 * The server log keeps every line whole, with the username.
 */
public final class ProximityChat {
	/** Ranges (blocks): heard whole up to the first, in part up to the second. */
	static final double TALK = 24.0, TALK_FADE = 40.0;
	static final double WHISPER = 4.0, WHISPER_FADE = 8.0;
	static final double SHOUT = 64.0, SHOUT_FADE = 96.0;
	/** Through a wall, the speaker counts as this many times as far. */
	static final double MUFFLED = 1.6;
	/** What a line starts with to shout, or to act. */
	static final String SHOUT_MARK = "!", ACT_MARK = "*";
	/** Line colours (RGB): talk, a far-off line at its faintest, a whisper, a shout, an act, out of character. */
	static final int TALK_COLOR = 0xFFFFFF, FAINT_COLOR = 0x8A8A8A, WHISPER_COLOR = 0xB8B8D0, SHOUT_COLOR = 0xFFE07A,
		ACT_COLOR = 0xD8B8F0, OOC_COLOR = 0x9AA6B2;

	enum Way { TALK, WHISPER, SHOUT, ACT }

	private ProximityChat() {
	}

	static void register() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			String text = message.signedContent().strip();
			// (0.7.464) the Grue keeps your words to wear later; and while it has you, yours don't come out
			if (!text.isEmpty() && com.jaymoskingdoms.spirits.Grue.heard(sender, text)) {
				speak(sender, text);
			}
			// never the vanilla broadcast to everyone
			return false;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
			Commands.literal("ooc").then(Commands.argument("words", StringArgumentType.greedyString()).executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				ooc(player, StringArgumentType.getString(c, "words"));
				return 1;
			}))));
	}

	/** (0.7.464) The Grue says {@code text} aloud with its host's mouth (as if they had). */
	public static void speakAs(ServerPlayer host, String text) {
		speak(host, text);
	}

	/** {@code speaker} says {@code text} aloud. */
	static void speak(ServerPlayer speaker, String text) {
		Way way = Way.TALK;
		if (text.startsWith(SHOUT_MARK) && text.length() > SHOUT_MARK.length()) {
			way = Way.SHOUT;
			text = text.substring(SHOUT_MARK.length()).strip();
		} else if (text.startsWith(ACT_MARK) && text.length() > ACT_MARK.length()) {
			way = Way.ACT;
			text = text.substring(ACT_MARK.length()).strip();
		} else if (speaker.isShiftKeyDown()) {
			way = Way.WHISPER;
		}
		if (Downed.isDowned(speaker) && way != Way.ACT) {
			// on the ground, bleeding: a whisper is all there is
			way = Way.WHISPER;
		}
		JaymosKingdoms.LOGGER.info("[chat] {} ({}) {}: {}", speaker.getDisplayName().getString(),
			speaker.getGameProfile().name(), way.name().toLowerCase(java.util.Locale.ROOT), text);

		double full = way == Way.WHISPER ? WHISPER : way == Way.SHOUT ? SHOUT : TALK;
		double fade = way == Way.WHISPER ? WHISPER_FADE : way == Way.SHOUT ? SHOUT_FADE : TALK_FADE;
		String beast = beastNoise(speaker);
		long seed = speaker.getUUID().getLeastSignificantBits() ^ text.hashCode() ^ speaker.level().getGameTime();
		ServerLevel level = speaker.level();
		int heard = 0;
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			if (p.level() != level) {
				continue;
			}
			double d = p == speaker ? 0.0 : p.distanceTo(speaker);
			if (d > fade) {
				continue;
			}
			if (p != speaker && !p.hasLineOfSight(speaker)) {
				d *= MUFFLED;
				if (d > fade) {
					continue;
				}
			}
			// how much of it carries: 1 up to full range, down to nothing at the fade
			double carry = d <= full ? 1.0 : 1.0 - (d - full) / (fade - full);
			String words = text;
			if (beast != null && p != speaker && !sameKind(speaker, p)) {
				words = beast;
			} else if (carry < 1.0 && way != Way.ACT) {
				words = muffle(text, carry, seed + p.getUUID().hashCode());
			}
			p.sendSystemMessage(line(speaker, p, way, words, carry));
			if (way != Way.ACT && ServerPlayNetworking.canSend(p, Speech.Said.TYPE)) {
				ServerPlayNetworking.send(p, new Speech.Said(speaker.getId(), Component.literal(words), color(way, carry)));
			}
			if (p != speaker) {
				heard++;
			}
		}
		if (heard == 0 && way != Way.ACT) {
			speaker.sendSystemMessage(Component.translatable("message.jaymoskingdoms.chat.unheard")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
		}
	}

	/** The chat line {@code to} reads. */
	private static Component line(ServerPlayer speaker, ServerPlayer to, Way way, String words, double carry) {
		Component name = Introductions.knows(to, speaker) ? speaker.getDisplayName() : Introductions.stranger();
		int color = color(way, carry);
		MutableComponent body = Component.literal(words);
		String key = switch (way) {
			case WHISPER -> "message.jaymoskingdoms.chat.whisper";
			case SHOUT -> "message.jaymoskingdoms.chat.shout";
			case ACT -> "message.jaymoskingdoms.chat.act";
			case TALK -> carry < 1.0 ? "message.jaymoskingdoms.chat.far" : "message.jaymoskingdoms.chat.talk";
		};
		MutableComponent line = Component.translatable(key, name, body).withColor(color);
		return way == Way.WHISPER || way == Way.ACT ? line.withStyle(ChatFormatting.ITALIC) : line;
	}

	private static int color(Way way, double carry) {
		int base = switch (way) {
			case WHISPER -> WHISPER_COLOR;
			case SHOUT -> SHOUT_COLOR;
			case ACT -> ACT_COLOR;
			case TALK -> TALK_COLOR;
		};
		return carry >= 1.0 ? base : blend(FAINT_COLOR, base, carry);
	}

	private static int blend(int from, int to, double t) {
		int r = (int)Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = (int)Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = (int)Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (r << 16) | (g << 8) | b;
	}

	/** {@code text} with words lost: each kept with chance {@code carry}, runs of lost ones shown as one "...". */
	static String muffle(String text, double carry, long seed) {
		RandomSource random = RandomSource.create(seed);
		String[] words = text.split("\\s+");
		List<String> out = new ArrayList<>();
		boolean lost = false;
		for (String w : words) {
			if (random.nextDouble() < carry) {
				out.add(w);
				lost = false;
			} else if (!lost) {
				out.add("...");
				lost = true;
			}
		}
		return String.join(" ", out);
	}

	/** What a beast says instead of words, or null if {@code speaker} still talks. */
	private static @Nullable String beastNoise(ServerPlayer speaker) {
		if (WerewolfPlayers.beastUp(speaker)) {
			return speaker.getRandom().nextBoolean() ? "*growls*" : "*snarls*";
		}
		if (PowersCompat.greatApe(speaker)) {
			return "*ROARS*";
		}
		return null;
	}

	/** A beast's own kind still makes out its words: werewolf to werewolf, ape to ape. */
	private static boolean sameKind(ServerPlayer speaker, ServerPlayer to) {
		return WerewolfPlayers.beastUp(speaker) ? WerewolfPlayers.beast(to) : PowersCompat.greatApe(to);
	}

	/** Out of character: everyone reads it, marked as not said in the world. */
	private static void ooc(ServerPlayer player, String words) {
		JaymosKingdoms.LOGGER.info("[ooc] {} ({}): {}", player.getDisplayName().getString(), player.getGameProfile().name(), words);
		for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
			Component name = Introductions.knows(p, player) ? player.getDisplayName() : Introductions.stranger();
			p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.chat.ooc", name, words).withColor(OOC_COLOR));
		}
	}
}
