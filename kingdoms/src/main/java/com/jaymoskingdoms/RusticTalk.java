package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plain folk's speech (Andy, 2026-11-27: "villagers to speak a more broken english like the goblins, but more grounded
 * in most cases, as literacy was rare"). Most townsfolk never learned their letters ({@link Dialects#rustic}), and
 * every line they say, whoever wrote it (our lines, MCA's talk, a guard's callouts), comes out in country speech: "is
 * not" is "ain't", "them" "'em", "something" "summat", "-ing" "-in'", "before" "afore", "yes" "aye", "we were" "we
 * was". Rougher than the townsfolk's betters, nowhere near a goblin's: the line always means what it meant. A line with
 * a hand-written country version ({@code dialect.jaymoskingdoms.rustic.<key>}) uses that instead. Names and the
 * {@code %s} holes are left alone, and the same line always comes out the same ({@link #speak} is pure).
 * <p>
 * Shares {@link GoblinTalk}'s way of cutting a line into words and putting it back together.
 */
public final class RusticTalk {
	/** One line in this many (by the line itself) ends on a country word. */
	static final int TIC_ONE_IN = 12;
	static final String[] TICS = {" Aye.", " Mind.", " An' that's the truth of it."};

	/** Two words that turn into one: the pair, lower case, a space between, and what it becomes. */
	private static final Map<String, String> PAIRS = Map.ofEntries(
		Map.entry("i am", "I'm"), Map.entry("you are", "you're"), Map.entry("we are", "we're"), Map.entry("they are", "they're"),
		Map.entry("it is", "it's"), Map.entry("that is", "that's"), Map.entry("there is", "there's"), Map.entry("what is", "what's"),
		Map.entry("do not", "don't"), Map.entry("does not", "don't"), Map.entry("did not", "didn't"), Map.entry("will not", "won't"),
		Map.entry("can not", "can't"), Map.entry("is not", "ain't"), Map.entry("are not", "ain't"), Map.entry("am not", "ain't"),
		Map.entry("have not", "ain't"), Map.entry("has not", "ain't"), Map.entry("we were", "we was"), Map.entry("they were", "they was"),
		Map.entry("you were", "you was"), Map.entry("he doesn't", "he don't"), Map.entry("she doesn't", "she don't"),
		Map.entry("it doesn't", "it don't"), Map.entry("don't know", "dunno"), Map.entry("have to", "got to"), Map.entry("has to", "got to"),
		Map.entry("going to", "goin' to"), Map.entry("a lot", "a heap"), Map.entry("thank you", "thank 'ee"),
		Map.entry("those people", "them folk"), Map.entry("these people", "these folk"), Map.entry("i think", "I reckon"));

	/** Single words: what each becomes. */
	private static final Map<String, String> WORDS = Map.ofEntries(
		Map.entry("isn't", "ain't"), Map.entry("aren't", "ain't"), Map.entry("haven't", "ain't"), Map.entry("hasn't", "ain't"),
		Map.entry("doesn't", "don't"), Map.entry("them", "'em"), Map.entry("those", "them"), Map.entry("because", "'cause"),
		Map.entry("about", "'bout"), Map.entry("around", "'round"), Map.entry("until", "'til"), Map.entry("before", "afore"),
		Map.entry("something", "summat"), Map.entry("nothing", "nothin'"), Map.entry("anything", "anythin'"),
		Map.entry("everything", "everythin'"), Map.entry("yes", "aye"), Map.entry("yeah", "aye"), Map.entry("old", "ol'"),
		Map.entry("little", "li'l"), Map.entry("very", "right"), Map.entry("really", "real"), Map.entry("certainly", "sure"),
		Map.entry("himself", "hisself"), Map.entry("themselves", "theirselves"), Map.entry("were", "was"), Map.entry("children", "young'uns"),
		Map.entry("money", "coin"), Map.entry("perhaps", "maybe"), Map.entry("probably", "like as not"), Map.entry("goodbye", "be seein' you"),
		Map.entry("hello", "'lo"), Map.entry("afraid", "feared"), Map.entry("frightened", "feared"),
		Map.entry("cannot", "can't"), Map.entry("understand", "follow"), Map.entry("extremely", "awful"), Map.entry("terrible", "awful"),
		Map.entry("dangerous", "bad"), Map.entry("difficult", "hard"), Map.entry("immediately", "right off"), Map.entry("somewhere", "somewheres"),
		Map.entry("anywhere", "anywheres"), Map.entry("nowhere", "nowheres"), Map.entry("yourself", "yerself"), Map.entry("your", "yer"),
		Map.entry("i've", "I"), Map.entry("you've", "you"), Map.entry("we've", "we"), Map.entry("they've", "they"), Map.entry("an", "a"),
		Map.entry("myself", "meself"));

	private RusticTalk() {
	}

	/** {@code line} as plain folk say it (a lang template: its {@code %s} holes and § colour codes are kept). */
	public static String speak(String line) {
		if (line == null || line.isEmpty()) {
			return line;
		}
		List<GoblinTalk.Token> tokens = GoblinTalk.tokens(line.replace('’', '\''));
		List<String> out = new ArrayList<>(tokens.size());
		boolean start = true;
		for (int i = 0; i < tokens.size(); i++) {
			GoblinTalk.Token t = tokens.get(i);
			if (!t.word()) {
				out.add(t.text());
				if (GoblinTalk.ends(t.text())) {
					start = true;
				} else if (!t.text().isBlank() && !t.text().trim().equals("\"")) {
					start = false;
				}
				continue;
			}
			boolean first = start;
			start = false;
			String before = i > 0 ? tokens.get(i - 1).text() : "";
			if (before.endsWith("§") || before.endsWith("'")) {
				out.add(t.text());
				continue;
			}
			boolean up = Character.isUpperCase(t.text().charAt(0)) && (first || !t.text().equals("I"));
			String lower = t.text().toLowerCase(Locale.ROOT);
			String said = null;
			if (i + 2 < tokens.size() && tokens.get(i + 1).text().equals(" ") && tokens.get(i + 2).word()) {
				said = PAIRS.get(lower + " " + tokens.get(i + 2).text().toLowerCase(Locale.ROOT));
				if (said != null) {
					i += 2;
				}
			}
			if (said == null) {
				said = WORDS.get(lower);
			}
			if (said == null) {
				said = dropG(lower);
			}
			// ("I" stays a capital wherever it stands)
			out.add(said.equals("i") ? "I" : GoblinTalk.cased(t.text(), said, up));
		}
		String spoken = GoblinTalk.join(out);
		long seed = line.hashCode() * 2654435761L;
		if (Math.floorMod(seed, TIC_ONE_IN) == 0 && GoblinTalk.endsSentence(spoken)) {
			spoken += TICS[(int)Math.floorMod(seed >> 8, TICS.length)];
		}
		return spoken;
	}

	/** "-ing" -> "-in'" ("goin'", "huntin'"), where what's before it is a word of its own ("king", "bring", "thing" stay). */
	private static String dropG(String lower) {
		if (lower.length() > 4 && lower.endsWith("ing") && !lower.contains("'")) {
			String stem = lower.substring(0, lower.length() - 3);
			for (int c = 0; c < stem.length(); c++) {
				if ("aeiouy".indexOf(stem.charAt(c)) >= 0) {
					return stem + "in'";
				}
			}
		}
		return lower;
	}
}
