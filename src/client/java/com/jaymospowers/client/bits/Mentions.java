package com.jaymospowers.client.bits;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * Chat mentions: a line that has {@code @<your name>} in it shows the mention bold and gold and pings you. It runs on what
 * arrives, on the receiving side, so it works with Jaymo's Kingdoms' proximity chat (which sends each hearer their own
 * version as a game message) and with plain chat (which is pinged but can't be recoloured). Your name is your username,
 * your display name (the character name when you made one: a name picked in MCA's creator shows there) and the first word of
 * each, so {@code @Aldric} finds Aldric Stone. Names are matched without regard to case.
 */
public final class Mentions {
	// ===== the knobs ================================================================================================
	/** The mention's colour (RGB), the ping's pitch, the least time between pings (ms), and the shortest first name that counts. */
	static final int COLOR = 0xFFD54A;
	static final float PING_PITCH = 1.5F;
	static final long PING_GAP_MS = 1000L;
	static final int MIN_FIRST_NAME = 3;
	// ================================================================================================================

	private static final Pattern NONE = Pattern.compile("(?!)");
	/** The pattern for the names it was built from (rebuilt when they change), and when the last ping sounded. */
	private static String builtFor = "";
	private static Pattern pattern = NONE;
	private static long pinged;

	private Mentions() {
	}

	public static void init() {
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : mark(message));
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, bound, received) -> {
			if (pattern().matcher(message.getString()).find()) {
				ping();
			}
		});
	}

	/** The pattern for {@code @name} of the local player, or one matching nothing when there is no player. */
	private static Pattern pattern() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return NONE;
		}
		Set<String> names = new LinkedHashSet<>();
		for (String full : new String[] {player.getName().getString(), player.getDisplayName().getString()}) {
			String name = full.strip();
			if (!name.isEmpty()) {
				names.add(name);
				String first = name.split("\\s+")[0];
				if (first.length() >= MIN_FIRST_NAME) {
					names.add(first);
				}
			}
		}
		List<String> longestFirst = new ArrayList<>(names);
		longestFirst.sort(Comparator.comparingInt(String::length).reversed());
		String key = String.join("\n", longestFirst);
		if (!key.equals(builtFor)) {
			builtFor = key;
			StringBuilder regex = new StringBuilder("(?<![\\w@])@(?:");
			for (int i = 0; i < longestFirst.size(); i++) {
				regex.append(i == 0 ? "" : "|").append(Pattern.quote(longestFirst.get(i)));
			}
			pattern = longestFirst.isEmpty() ? NONE : Pattern.compile(regex.append(")(?![\\w])").toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		}
		return pattern;
	}

	/** {@code message} with each mention of you made bold and gold (and a ping), or {@code message} itself with none. */
	private static Component mark(Component message) {
		Pattern pattern = pattern();
		if (pattern == NONE) {
			return message;
		}
		List<Style> styles = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		StringBuilder all = new StringBuilder();
		message.visit((style, text) -> {
			styles.add(style);
			texts.add(text);
			all.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		Matcher matcher = pattern.matcher(all);
		List<int[]> found = new ArrayList<>();
		while (matcher.find()) {
			found.add(new int[] {matcher.start(), matcher.end()});
		}
		if (found.isEmpty()) {
			return message;
		}
		ping();
		MutableComponent out = Component.empty();
		int at = 0;
		for (int i = 0; i < texts.size(); i++) {
			String text = texts.get(i);
			int from = 0;
			while (from < text.length()) {
				int here = at + from;
				int[] range = null;
				for (int[] f : found) {
					if (f[1] > here) {
						range = f;
						break;
					}
				}
				boolean inside = range != null && range[0] <= here;
				int end = range == null ? text.length() : Math.min(text.length(), (inside ? range[1] : range[0]) - at);
				if (end <= from) {
					end = text.length();
				}
				Style style = inside ? styles.get(i).withColor(COLOR).withBold(true) : styles.get(i);
				out.append(Component.literal(text.substring(from, end)).withStyle(style));
				from = end;
			}
			at += text.length();
		}
		return out;
	}

	private static void ping() {
		long now = Util.getMillis();
		if (now - pinged >= PING_GAP_MS) {
			pinged = now;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, PING_PITCH));
		}
	}
}
