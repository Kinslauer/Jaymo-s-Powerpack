package com.jaymoskingdoms.client;

import com.jaymoskingdoms.IllagerTongue;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * An illager's line as one who knows some of their tongue hears it ({@link IllagerTongue#partial}): the server sends the
 * line with how much of the tongue you know, and here the words you know are kept (the same words every time, more as
 * you learn: {@link IllagerTongue#knowsWord}) and the rest written as the illagers write them, in the glyphs of Alex's
 * Caves' Cave Tablets ({@link IllagerTongue#glyphs}), greyed. Knowing none of it, the whole line is glyphs. Used by the
 * speech bubbles and the talk card.
 */
public final class IllagerWords {
	/** The words you don't know: their colour. */
	static final int UNKNOWN = 0xA0A890;

	private IllagerWords() {
	}

	/** {@code text}, part-translated if it is such a line; anything else unchanged. */
	public static Component resolve(Component text) {
		if (!(text.getContents() instanceof TranslatableContents t) || !t.getKey().equals(IllagerTongue.PARTIAL) || t.getArgs().length < 2) {
			return text;
		}
		Object[] args = t.getArgs();
		int points;
		try {
			points = Integer.parseInt(String.valueOf(args[0] instanceof Component c ? c.getString() : args[0]));
		} catch (NumberFormatException e) {
			return text;
		}
		String words = args[1] instanceof Component c ? c.getString() : String.valueOf(args[1]);
		MutableComponent out = Component.empty();
		String[] tokens = words.split(" ");
		for (int i = 0; i < tokens.length; i++) {
			String token = tokens[i];
			if (i > 0) {
				out.append(" ");
			}
			if (IllagerTongue.knowsWord(token, points)) {
				out.append(token);
				continue;
			}
			// (the word in the tablets' glyphs, as the Spelunkery Table shows a word not yet made out; its punctuation kept)
			out.append(IllagerTongue.glyphs(token).withColor(UNKNOWN));
		}
		return out.withStyle(text.getStyle());
	}
}
