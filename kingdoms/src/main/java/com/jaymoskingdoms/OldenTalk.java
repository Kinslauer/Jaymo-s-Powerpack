package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The old tongue (Andy, 2026-11-27: a companion "who strictly speaks like this", after the Spyder-Knight): every line
 * an old-tongued knight says ({@link OldKnights}), whoever wrote it (our lines, MCA's talk, the callouts in a fight),
 * comes out all thee and thou. "You are" is "thou art", "your" "thy", "yes" "aye", "it is" "'tis", "before" "ere",
 * "maybe" "perchance". A line with a hand-written old-tongue version ({@code dialect.jaymoskingdoms.olden.<key>}) uses
 * that instead ({@link Dialects#olden}). Names and the {@code %s} holes are left alone, the same line always comes out
 * the same ({@link #speak} is pure), and now and then one ends on an oath ({@link #TIC_ONE_IN}).
 * <p>
 * Shares {@link GoblinTalk}'s way of cutting a line into words and putting it back together.
 */
public final class OldenTalk {
	/** One line in this many (by the line itself) ends on an oath. */
	static final int TIC_ONE_IN = 6;
	static final String[] TICS = {" Forsooth.", " By my troth.", " Huzzah!", " So swear I.", " Upon mine honour."};

	/** Two words that turn into others: the pair, lower case, a space between, and what it becomes. */
	private static final Map<String, String> PAIRS = Map.ofEntries(
		Map.entry("you are", "thou art"), Map.entry("are you", "art thou"), Map.entry("you were", "thou wert"), Map.entry("were you", "wert thou"),
		Map.entry("you have", "thou hast"), Map.entry("have you", "hast thou"), Map.entry("you had", "thou hadst"), Map.entry("had you", "hadst thou"),
		Map.entry("you will", "thou wilt"), Map.entry("will you", "wilt thou"), Map.entry("you can", "thou canst"), Map.entry("can you", "canst thou"),
		Map.entry("you do", "thou dost"), Map.entry("do you", "dost thou"), Map.entry("you did", "thou didst"), Map.entry("did you", "didst thou"),
		Map.entry("you would", "thou wouldst"), Map.entry("would you", "wouldst thou"), Map.entry("you should", "thou shouldst"),
		Map.entry("should you", "shouldst thou"), Map.entry("you could", "thou couldst"), Map.entry("could you", "couldst thou"),
		Map.entry("you shall", "thou shalt"), Map.entry("shall you", "shalt thou"), Map.entry("you must", "thou must"),
		Map.entry("you know", "thou knowest"), Map.entry("you need", "thou needest"), Map.entry("you want", "thou wouldst have"),
		Map.entry("you see", "thou seest"), Map.entry("you think", "thou thinkest"), Map.entry("thank you", "I thank thee"),
		Map.entry("you won't", "thou wilt not"), Map.entry("you can't", "thou canst not"), Map.entry("you don't", "thou dost not"),
		Map.entry("you didn't", "thou didst not"), Map.entry("you aren't", "thou art not"), Map.entry("you haven't", "thou hast not"),
		Map.entry("you wouldn't", "thou wouldst not"), Map.entry("you shouldn't", "thou shouldst not"), Map.entry("you couldn't", "thou couldst not"),
		Map.entry("it is", "'tis"), Map.entry("it was", "'twas"), Map.entry("it will", "'twill"), Map.entry("it would", "'twould"),
		Map.entry("i will", "I shall"), Map.entry("we will", "we shall"), Map.entry("there is", "there be"), Map.entry("there are", "there be"),
		Map.entry("come here", "come hither"), Map.entry("go away", "begone"), Map.entry("over there", "yonder"), Map.entry("no one", "none"),
		Map.entry("of course", "verily"), Map.entry("right now", "forthwith"), Map.entry("a lot", "a great deal"));

	/** Single words: what each becomes. */
	private static final Map<String, String> WORDS = Map.ofEntries(
		Map.entry("you're", "thou art"), Map.entry("you've", "thou hast"), Map.entry("you'll", "thou wilt"), Map.entry("you'd", "thou wouldst"),
		Map.entry("it's", "'tis"), Map.entry("i'm", "I am"), Map.entry("i'll", "I shall"), Map.entry("i've", "I have"), Map.entry("i'd", "I would"),
		Map.entry("we're", "we are"), Map.entry("we'll", "we shall"), Map.entry("they're", "they are"), 
		Map.entry("there's", "there be"), Map.entry("what's", "what is"), Map.entry("he's", "he is"), Map.entry("she's", "she is"),
		Map.entry("let's", "let us"), Map.entry("here's", "here is"), Map.entry("where's", "where is"), Map.entry("who's", "who is"),
		Map.entry("don't", "do not"), Map.entry("doesn't", "doth not"), Map.entry("didn't", "did not"), Map.entry("won't", "will not"),
		Map.entry("can't", "cannot"), Map.entry("isn't", "is not"), Map.entry("aren't", "are not"), Map.entry("wasn't", "was not"),
		Map.entry("weren't", "were not"), Map.entry("haven't", "have not"), Map.entry("hasn't", "hath not"), Map.entry("shouldn't", "should not"),
		Map.entry("wouldn't", "would not"), Map.entry("couldn't", "could not"), Map.entry("ain't", "is not"),
		Map.entry("your", "thy"), Map.entry("yours", "thine"), Map.entry("yourself", "thyself"), Map.entry("yourselves", "yourselves"),
		Map.entry("yes", "aye"), Map.entry("yeah", "aye"), Map.entry("yep", "aye"), Map.entry("no", "nay"), Map.entry("nope", "nay"),
		Map.entry("hello", "well met"), Map.entry("hi", "well met"), Map.entry("hey", "hark"), Map.entry("greetings", "well met"),
		Map.entry("goodbye", "fare thee well"), Map.entry("bye", "fare thee well"), Map.entry("farewell", "fare thee well"),
		Map.entry("before", "ere"), Map.entry("maybe", "perchance"), Map.entry("perhaps", "perchance"), Map.entry("often", "oft"),
		Map.entry("okay", "very well"), Map.entry("ok", "very well"), Map.entry("really", "truly"), Map.entry("very", "most"),
		Map.entry("why", "wherefore"), Map.entry("has", "hath"), Map.entry("does", "doth"), Map.entry("says", "saith"),
		Map.entry("kid", "lad"), Map.entry("guy", "fellow"), Map.entry("guys", "fellows"), Map.entry("money", "coin"),
		Map.entry("sorry", "forgive me"), Map.entry("please", "I pray thee"), Map.entry("thanks", "my thanks"), Map.entry("nothing", "naught"),
		Map.entry("anything", "aught"), Map.entry("until", "till"), Map.entry("soon", "anon"), Map.entry("between", "betwixt"),
		Map.entry("among", "amongst"), Map.entry("always", "ever"), Map.entry("wow", "by my troth"), Map.entry("cool", "splendid"),
		Map.entry("awesome", "splendid"), Map.entry("kill", "slay"), Map.entry("killed", "slew"), Map.entry("enemy", "foe"),
		Map.entry("enemies", "foes"), Map.entry("idiot", "knave"), Map.entry("fool", "fool"), Map.entry("stupid", "witless"),
		Map.entry("listen", "hark"), Map.entry("look", "behold"), Map.entry("quickly", "apace"), Map.entry("hurry", "make haste"));

	/** Words after which "you" is the one doing it ("thou"), not the one it's done to ("thee"). */
	private static final Set<String> BEFORE_SUBJECT = Set.of("if", "and", "but", "when", "as", "so", "that", "unless", "because", "where",
		"while", "though", "or", "than", "what", "whom", "how");

	private OldenTalk() {
	}

	/** {@code line} as an old-tongued knight says it (a lang template: its {@code %s} holes and § colour codes are kept). */
	public static String speak(String line) {
		if (line == null || line.isEmpty()) {
			return line;
		}
		List<GoblinTalk.Token> tokens = GoblinTalk.tokens(line.replace('’', '\''));
		List<String> out = new ArrayList<>(tokens.size());
		boolean start = true;
		String lastWord = "";
		for (int i = 0; i < tokens.size(); i++) {
			GoblinTalk.Token t = tokens.get(i);
			if (!t.word()) {
				out.add(t.text());
				if (GoblinTalk.ends(t.text())) {
					start = true;
					lastWord = "";
				} else if (t.text().contains(",") || t.text().contains(";") || t.text().contains(":")) {
					// (after a comma "you" starts again too: "if it please you, you shall have it")
					lastWord = ",";
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
			if (said == null && lower.equals("you")) {
				said = first || lastWord.equals(",") || BEFORE_SUBJECT.contains(lastWord) ? "thou" : "thee";
			}
			if (said == null && lower.equals("my") && i + 2 < tokens.size() && tokens.get(i + 2).word()
				&& "aeiou".indexOf(Character.toLowerCase(tokens.get(i + 2).text().charAt(0))) >= 0) {
				said = "mine";
			}
			if (said == null && lower.equals("thy") && i + 2 < tokens.size() && tokens.get(i + 2).word()
				&& "aeiou".indexOf(Character.toLowerCase(tokens.get(i + 2).text().charAt(0))) >= 0) {
				said = "thine";
			}
			if (said == null) {
				said = WORDS.get(lower);
				if ("thy".equals(said) && i + 2 < tokens.size() && tokens.get(i + 2).word()
					&& "aeiou".indexOf(Character.toLowerCase(tokens.get(i + 2).text().charAt(0))) >= 0) {
					said = "thine";
				}
			}
			if (said == null) {
				said = lower;
			}
			lastWord = lower;
			out.add(said.equals("i") ? "I" : GoblinTalk.cased(t.text(), said, up));
		}
		String spoken = GoblinTalk.join(out);
		long seed = line.hashCode() * 2654435761L;
		if (Math.floorMod(seed, TIC_ONE_IN) == 0 && GoblinTalk.endsSentence(spoken)) {
			spoken += TICS[(int)Math.floorMod(seed >> 8, TICS.length)];
		}
		return spoken;
	}
}
