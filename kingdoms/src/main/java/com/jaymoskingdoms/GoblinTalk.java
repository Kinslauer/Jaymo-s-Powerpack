package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Andy (2026-11-19): "the goblins also dont seem to have their own language, they should speak very broken but
 * intelligible english, unlike orcs who you really cant tell". Every line a goblin says, whatever wrote it (our lines,
 * MCA's talk, a camp's callouts), is turned into goblin English here: "I am" becomes "me", "the" "da", "this" "dis",
 * "is" and "are" and "a" fall out, "-ing" loses its "g", "you're" is just "you", "we" is "us", money is "shinies".
 * Plain words only change: names and the {@code %s} holes a line's names go into are left alone, so the line is always
 * still clear. The same line always comes out the same ({@link #speak} is pure), and now and then one ends on a
 * goblin's giggle ({@link #TIC_ONE_IN}).
 *
 * <p>The server turns a goblin's words into a line the client runs through this ({@link Dialects#broken}), so it is
 * the client's own language file (MCA's too) that gets broken.
 */
public final class GoblinTalk {
	/** One line in this many (by the line itself) ends on a giggle or a mutter. */
	static final int TIC_ONE_IN = 5;
	static final String[] TICS = {" Hee hee.", " Heh.", " Yes yes.", " Hehe!", " Hnn."};

	/** Two words that turn into one (or none): the pair, lower case, a space between, and what it becomes. */
	private static final Map<String, String> PAIRS = Map.ofEntries(
		Map.entry("i am", "me"), Map.entry("isn't it", "yes"), Map.entry("aren't you", "yes"), Map.entry("i was", "me was"), Map.entry("i will", "me gonna"), Map.entry("i have", "me got"),
		Map.entry("i had", "me had"), Map.entry("i would", "me would"),
		Map.entry("you are", "you"), Map.entry("we are", "us"), Map.entry("they are", "dey"), Map.entry("he is", "him"),
		Map.entry("she is", "her"), Map.entry("it is", "it"), Map.entry("that is", "dat"), Map.entry("this is", "dis"),
		Map.entry("there is", "dere"), Map.entry("there are", "dere"), Map.entry("what is", "wot"), Map.entry("what are", "wot"),
		Map.entry("who is", "who"), Map.entry("who are", "who"), Map.entry("where is", "where"), Map.entry("where are", "where"),
		Map.entry("here is", "here"), Map.entry("how is", "how"), Map.entry("how are", "how"),
		Map.entry("going to", "gonna"), Map.entry("want to", "want"), Map.entry("have to", "gotta"), Map.entry("has to", "gotta"),
		Map.entry("got to", "gotta"), Map.entry("need to", "gotta"), Map.entry("thank you", "fanks"), Map.entry("a lot", "lots"),
		Map.entry("kind of", "kinda"), Map.entry("will not", "no"), Map.entry("do not", "no"), Map.entry("does not", "no"),
		Map.entry("did not", "no"), Map.entry("can not", "no can"), Map.entry("is not", "not"), Map.entry("are not", "not"),
		Map.entry("am not", "not"), Map.entry("was not", "not"), Map.entry("were not", "not"));

	/** Single words: what each becomes ("" drops it). */
	private static final Map<String, String> WORDS = Map.ofEntries(
		// (what they are)
		Map.entry("i'm", "me"), Map.entry("i've", "me got"), Map.entry("i'll", "me gonna"), Map.entry("i'd", "me would"),
		Map.entry("you're", "you"), Map.entry("we're", "us"), Map.entry("they're", "dey"), Map.entry("it's", "it"),
		Map.entry("that's", "dat"), Map.entry("there's", "dere"), Map.entry("what's", "wot"), Map.entry("who's", "who"),
		Map.entry("he's", "him"), Map.entry("she's", "her"), Map.entry("here's", "here"), Map.entry("let's", "us"),
		Map.entry("where's", "where"), Map.entry("how's", "how"),
		Map.entry("you'll", "you gonna"), Map.entry("we'll", "us gonna"), Map.entry("they'll", "dey gonna"),
		Map.entry("he'll", "him gonna"), Map.entry("she'll", "her gonna"), Map.entry("it'll", "it gonna"),
		Map.entry("you've", "you got"), Map.entry("we've", "us got"), Map.entry("they've", "dey got"), Map.entry("you'd", "you would"),
		// (no)
		Map.entry("don't", "no"), Map.entry("doesn't", "no"), Map.entry("didn't", "no"), Map.entry("won't", "no"),
		Map.entry("can't", "no can"), Map.entry("cannot", "no can"), Map.entry("isn't", "not"), Map.entry("aren't", "not"),
		Map.entry("wasn't", "not"), Map.entry("weren't", "not"), Map.entry("ain't", "not"), Map.entry("haven't", "no got"),
		Map.entry("hasn't", "no got"), Map.entry("shouldn't", "no should"), Map.entry("wouldn't", "no would"),
		Map.entry("couldn't", "no could"),
		// (who)
		Map.entry("i", "me"), Map.entry("my", "me"), Map.entry("myself", "meself"), Map.entry("we", "us"), Map.entry("our", "us"),
		Map.entry("he", "him"), Map.entry("his", "him"), Map.entry("she", "her"), Map.entry("they", "dey"), Map.entry("them", "dem"),
		Map.entry("their", "dey"), Map.entry("theirs", "deirs"), Map.entry("themselves", "demselves"), Map.entry("your", "yer"),
		Map.entry("yours", "yers"), Map.entry("yourself", "yerself"),
		// (the little words that fall out)
		Map.entry("am", ""), Map.entry("is", ""), Map.entry("are", ""), Map.entry("a", ""), Map.entry("an", ""),
		Map.entry("were", "was"), Map.entry("has", "got"), Map.entry("have", "got"), Map.entry("will", "gonna"),
		// (th)
		Map.entry("the", "da"), Map.entry("this", "dis"), Map.entry("that", "dat"), Map.entry("these", "dese"),
		Map.entry("those", "dose"), Map.entry("there", "dere"), Map.entry("then", "den"), Map.entry("than", "dan"),
		Map.entry("with", "wif"), Map.entry("without", "wifout"), Map.entry("nothing", "nuffin"), Map.entry("something", "somefin"),
		Map.entry("anything", "anyfin"), Map.entry("everything", "everyfin"), Map.entry("think", "fink"), Map.entry("thinks", "finks"),
		Map.entry("thing", "fing"), Map.entry("things", "fings"), Map.entry("thank", "fank"), Map.entry("thanks", "fanks"),
		// (goblin words)
		Map.entry("what", "wot"), Map.entry("because", "cuz"), Map.entry("about", "'bout"), Map.entry("little", "lil"),
		Map.entry("very", "real"), Map.entry("really", "real"), Map.entry("money", "shinies"), Map.entry("treasure", "shinies"),
		Map.entry("valuables", "shinies"), Map.entry("hello", "'ello"), Map.entry("greetings", "'ello"), Map.entry("goodbye", "bye-bye"),
		Map.entry("farewell", "bye-bye"), Map.entry("yes", "yes yes"), Map.entry("please", "pleeease"), Map.entry("sorry", "sorry sorry"));

	/** "do you", "did he": the do falls out before these. */
	private static final java.util.Set<String> PRONOUNS = java.util.Set.of("you", "i", "we", "they", "he", "she", "it");

	private GoblinTalk() {
	}

	private record Token(String text, boolean word) {
	}

	/** {@code line} as a goblin says it (a lang template: its {@code %s} holes and § colour codes are kept as they are). */
	public static String speak(String line) {
		if (line == null || line.isEmpty()) {
			return line;
		}
		List<Token> tokens = tokens(line.replace('\u2019', '\''));
		List<String> out = new ArrayList<>(tokens.size());
		// (a capital stays where a sentence starts: on the word said there, or on the next if that one fell out)
		boolean start = true, capital = false;
		for (int i = 0; i < tokens.size(); i++) {
			Token t = tokens.get(i);
			if (!t.word) {
				out.add(t.text);
				if (ends(t.text)) {
					start = true;
				} else if (!t.text.isBlank() && !t.text.trim().equals("\"")) {
					start = false;
				}
				continue;
			}
			boolean first = start;
			start = false;
			String before = i > 0 ? tokens.get(i - 1).text : "";
			if (before.endsWith("\u00a7") || before.endsWith("'")) {
				// (a colour code's letter; a word cut short already, 'is, 'em)
				out.add(t.text);
				continue;
			}
			boolean up = capital || Character.isUpperCase(t.text.charAt(0)) && (first || !t.text.equals("I"));
			capital = false;
			String lower = t.text.toLowerCase(Locale.ROOT);
			String said = null;
			// (two words that go together, with only a space between)
			if (i + 2 < tokens.size() && tokens.get(i + 1).text.equals(" ") && tokens.get(i + 2).word) {
				String next = tokens.get(i + 2).text.toLowerCase(Locale.ROOT);
				said = PAIRS.get(lower + " " + next);
				if (said != null) {
					i += 2;
				} else if ((lower.equals("do") || lower.equals("does") || lower.equals("did")) && PRONOUNS.contains(next)) {
					// ("do you want" -> "you want": the do falls out, the pronoun after it is said as usual)
					said = "";
				}
			}
			if (said == null) {
				said = WORDS.get(lower);
			}
			if (said == null) {
				said = dropG(lower);
			}
			if (said.isEmpty()) {
				out.add("\u0000");
				capital = up && first;
				start = first;
				continue;
			}
			out.add(cased(t.text, said, up));
		}
		String spoken = join(out);
		long seed = line.hashCode() * 2654435761L;
		if (Math.floorMod(seed, TIC_ONE_IN) == 0 && endsSentence(spoken)) {
			spoken += TICS[(int)Math.floorMod(seed >> 8, TICS.length)];
		}
		return spoken;
	}

	/** A gap that ends a sentence ("! ", ". "; not "..." trailing off, nor a comma). */
	private static boolean ends(String gap) {
		String t = gap.strip();
		return !t.isEmpty() && !t.contains("...") && (t.endsWith(".") || t.endsWith("!") || t.endsWith("?") || t.endsWith(".\"")
			|| t.endsWith("!\"") || t.endsWith("?\""));
	}

	/** "-ing" -> "-in" ("runnin", "leakin"), where what's before it is a word of its own ("king", "bring" stay). */
	private static String dropG(String lower) {
		if (lower.length() > 4 && lower.endsWith("ing") && !lower.contains("'")) {
			String stem = lower.substring(0, lower.length() - 3);
			for (int c = 0; c < stem.length(); c++) {
				if ("aeiouy".indexOf(stem.charAt(c)) >= 0) {
					return lower.substring(0, lower.length() - 1);
				}
			}
		}
		return lower;
	}

	/** {@code said} in {@code original}'s case: SHOUTED, with a capital ({@code up}) or plain. */
	private static String cased(String original, String said, boolean up) {
		if (said.equals(original.toLowerCase(Locale.ROOT)) && (up || !Character.isUpperCase(original.charAt(0)) || !original.equals("I"))) {
			return up && !Character.isUpperCase(original.charAt(0)) ? Character.toUpperCase(said.charAt(0)) + said.substring(1) : original;
		}
		if (original.length() > 1 && original.equals(original.toUpperCase(Locale.ROOT))) {
			return said.toUpperCase(Locale.ROOT);
		}
		if (!up) {
			return said;
		}
		int c = said.charAt(0) == '\'' && said.length() > 1 ? 1 : 0;
		return said.substring(0, c) + Character.toUpperCase(said.charAt(c)) + said.substring(c + 1);
	}

	/** The words and what lies between them; a {@code %s} hole is never a word. */
	private static List<Token> tokens(String line) {
		List<Token> out = new ArrayList<>();
		StringBuilder gap = new StringBuilder();
		int i = 0;
		while (i < line.length()) {
			char c = line.charAt(i);
			if (c == '%') {
				int j = i + 1;
				while (j < line.length() && (Character.isDigit(line.charAt(j)) || line.charAt(j) == '$')) {
					j++;
				}
				if (j < line.length()) {
					j++;
				}
				gap.append(line, i, j);
				i = j;
				continue;
			}
			if (Character.isLetter(c)) {
				int j = i;
				while (j < line.length() && (Character.isLetter(line.charAt(j))
					|| (line.charAt(j) == '\'' && j + 1 < line.length() && Character.isLetter(line.charAt(j + 1)) && j > i))) {
					j++;
				}
				if (!gap.isEmpty()) {
					out.add(new Token(gap.toString(), false));
					gap.setLength(0);
				}
				out.add(new Token(line.substring(i, j), true));
				i = j;
				continue;
			}
			gap.append(c);
			i++;
		}
		if (!gap.isEmpty()) {
			out.add(new Token(gap.toString(), false));
		}
		return out;
	}

	/** Puts the line back together; a dropped word (\0) takes one space with it. */
	private static String join(List<String> parts) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			String p = parts.get(i);
			if (!p.equals("\u0000")) {
				b.append(p);
				continue;
			}
			// (the space after it goes, or if none follows, the one before)
			if (i + 1 < parts.size() && parts.get(i + 1).startsWith(" ")) {
				parts.set(i + 1, parts.get(i + 1).substring(1));
			} else if (b.length() > 0 && b.charAt(b.length() - 1) == ' ') {
				b.setLength(b.length() - 1);
			}
		}
		return b.toString();
	}

	private static boolean endsSentence(String s) {
		String t = s.stripTrailing();
		return !t.isEmpty() && ".!?".indexOf(t.charAt(t.length() - 1)) >= 0 && !t.endsWith("...");
	}
}
