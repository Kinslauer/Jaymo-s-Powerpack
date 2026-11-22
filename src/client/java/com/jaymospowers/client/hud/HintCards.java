package com.jaymospowers.client.hud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Util;

/**
 * Andy (2026-11-20): "All hints in text should be reworked as tooltips in the new menu, top right. Hints like, you must kneel
 * to revive, or skill not unlocked". An action-bar line whose key is listed in {@code assets/jaymospowers/hint_keys.txt}
 * (how to do something, what it needs first, something not learned yet; Kingdoms' and Kitchen's too) comes up as a
 * {@link TipCard} in the top right instead, and so is kept on the menu's Notices page. Any other line stays on the bar.
 * A hint sent again and again (while a hold goes on, or each try) makes one card, not a stack of them.
 */
public final class HintCards {
	/** A hint already showing, waiting, or shown this recently (ms) isn't carded again. */
	static final long REPEAT_MS = 30_000L;

	private static final Set<String> KEYS = load();
	/** The text of each hint lately carded, and when. */
	private static final Map<String, Long> SHOWN = new HashMap<>();

	private HintCards() {
	}

	private static Set<String> load() {
		Set<String> keys = new HashSet<>();
		try (InputStream in = HintCards.class.getResourceAsStream("/assets/jaymospowers/hint_keys.txt")) {
			if (in == null) {
				return keys;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			for (String line; (line = reader.readLine()) != null;) {
				line = line.strip();
				if (!line.isEmpty() && !line.startsWith("#")) {
					keys.add(line);
				}
			}
		} catch (IOException e) {
			// (no list: every line stays on the bar)
		}
		return keys;
	}

	/** An action-bar line: true if it's a hint, carded here, so the bar doesn't show it. */
	public static boolean take(Component message) {
		if (!hint(message)) {
			return false;
		}
		String text = message.getString();
		long now = Util.getMillis();
		Long last = SHOWN.get(text);
		if ((last == null || now - last >= REPEAT_MS) && !TipCard.holds(text)) {
			TipCard.show(Component.translatable("tip.jaymospowers.hint.title"), message.copy().withStyle(s -> s.withColor((TextColor) null)));
		}
		SHOWN.put(text, now);
		if (SHOWN.size() > 64) {
			SHOWN.values().removeIf(at -> now - at >= REPEAT_MS);
		}
		return true;
	}

	/** Whether the line is one of the listed hints: its own key, or the one line inside an empty wrapper. */
	static boolean hint(Component message) {
		if (message.getContents() instanceof TranslatableContents said) {
			return KEYS.contains(said.getKey());
		}
		return message.getContents() == PlainTextContents.EMPTY && message.getSiblings().size() == 1 && hint(message.getSiblings().getFirst());
	}
}
