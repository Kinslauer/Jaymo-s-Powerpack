package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-27, a guard's "Make sure to watch out for these cursed creepers" on the talk screen): "there should
 * always be an option to address what they say here. either the first on the tree or a button". Whatever a villager
 * opens the talk with (MCA's greetings and time-of-day remarks, or one of ours), the first spoke on the talk wheel answers
 * that line: a warning gets "Seen any about? Where?", a word on the weather gets one back, a question gets answered,
 * a sigh gets asked after. Your words are on the wheel ({@code talkwheel.jaymoskingdoms.jaymoskingdoms_reply.<topic>},
 * worded by your blood or class where a line is written for it), theirs come back in their own voice (personality, as
 * {@link RefusalHints#tone}, or cold for one who is wary of you or bears you ill), and the talk goes back to the wheel ({@link Conversations#again}).
 *
 * <p>Which reply fits is read from the line on the client ({@link #topicOf}): first the topics registered for particular
 * lines ({@link #register}: a feature with lines of its own, say a cry of pain, adds a topic that matches its keys and
 * answers as it likes), then the line's words (monsters, weather, work, sleep, food), then its shape (a question, an
 * {@code *action*}, a greeting), and anything else gets "Is that so?". The topic goes to the server as the MCA answer
 * {@link #ANSWER_PREFIX}{@code <topic>} and is checked against the known topics there.
 */
public final class Replies {
	/** The MCA {@code main} answer for a reply: this, then the topic. */
	public static final String ANSWER_PREFIX = "jaymoskingdoms_reply.";
	/** Built-in topics. */
	public static final String REBUFF = "rebuff", DANGER = "danger", WEATHER = "weather", WORK = "work", REST = "rest", FOOD = "food",
		QUESTION = "question", GESTURE = "gesture", GREET = "greet", GENERAL = "general";
	/** How far a villager looks for the monster they warned you of (blocks). */
	static final double DANGER_RANGE = 48.0;
	/** Hearts for hearing them out, at most once a day for each of you. */
	static final int LISTEN_HEARTS = 1;
	/** A day, in ticks (for the hearts above). */
	static final long DAY = 24000L;

	/**
	 * A kind of line and how it's answered. {@code keys}: matched against the line's translation key (the one MCA picked,
	 * e.g. {@code guard.dialogue.main.night/4}, else the key sent); {@code words}: against its text in lower case;
	 * {@code answer}: the villager's reply, or null for {@code talk_reply.jaymoskingdoms.<name>.<tone>}.
	 */
	public record Topic(String name, Predicate<String> keys, Predicate<String> words,
		@Nullable BiFunction<ServerPlayer, LivingEntity, Component> answer) {
	}

	private static final List<Topic> TOPICS = new ArrayList<>();
	/** "villager/player" → the day they were last heard out. */
	private static final Map<String, Long> HEARD = new HashMap<>();

	static {
		register(new Topic(REBUFF, k -> k.contains("greet.negative"), words("hmph", "ugh", "meh", "go away", "leave me alone", "bother someone else",
			"what now", "you again", "misfortune", "what do you want", "what is it", "stares you down", "*grunts"), null));
		register(new Topic(DANGER, k -> false, words("creeper", "zombie", "skeleton", "spider", "monster", "undead", "pillager", "illager", "raid",
			"witch", "phantom", "wolves", "bandit", "enderman", "endermen", "slime", "beast", "drowned", "careful", "watch out", "be safe",
			"stay safe", "dangerous", "lock your door", "lock the door", "eerie", "creepy crawl", "stick close to the village", "stray too far"),
			Replies::danger));
		register(new Topic(WEATHER, k -> false, words("rain", "storm", "thunder", "weather", "sunny", "sunshine", "the sun", "sunset", "sunrise",
			"snow", "chilly", "cold out", "breeze", "windy", "the wind", "the sky", "clouds", "fog", "starlit", "stars", "lovely day",
			"beautiful day", "nice day", "fine day", "fine morning", "beautiful morning", "lovely morning"), Replies::weather));
		register(new Topic(REST, k -> false, words("tired", "sleep", "bed", "yawn", "exhausted", "drained", "long day", "resting", "dream", "nap ",
			"weary", "collapse", "snore", "blankets", "night owl"), null));
		register(new Topic(FOOD, k -> false, words("hungry", "starving", "eat", "food", "bread", "cake", "dinner", "breakfast", "supper", "lunch",
			"stew", "cook", "culinary", "meal", "pie ", "soup", "meat", "ingredients"), null));
		register(new Topic(WORK, k -> false, words("work", "busy", "job", "chores", "harvest", "crops", "field", "farm", "forge", "furnace",
			"anvil", "sheep", "wool", "fishing", "fish ", "customer", "shift", "patrol", "on duty", "guard duty", "my post", "trade", "shop", "sell",
			"tools", "armor", "armour", "materials"), null));
		register(new Topic(GREET, k -> false, words("hello", "hey", "hi ", "hi,", "hi!", "hi.", "greetings", "good morning", "morning", "evening",
			"good to see", "howdy", "yo,", "yo!", "sup", "welcome", "pleasure to", "lay eyes", "back again"), null));
	}

	private Replies() {
	}

	/**
	 * Adds a topic, checked before those added after it (so a feature's own lines go ahead of the words above when it
	 * registers in its {@code register()}). Registered on both sides: the client picks it, the server answers it.
	 */
	public static synchronized void register(Topic topic) {
		TOPICS.removeIf(t -> t.name().equals(topic.name()));
		// a feature's topic goes ahead of the built-in word topics
		int at = 0;
		while (at < TOPICS.size() && !builtIn(TOPICS.get(at).name())) {
			at++;
		}
		TOPICS.add(builtIn(topic.name()) ? TOPICS.size() : at, topic);
	}

	private static boolean builtIn(String name) {
		return switch (name) {
			case REBUFF, DANGER, WEATHER, WORK, REST, FOOD, GREET -> true;
			default -> false;
		};
	}

	/** A test for any of {@code words} starting a word in a line's lower-cased text ("creeper" finds "creepers", not "rain" in "drained"). */
	public static Predicate<String> words(String... words) {
		StringBuilder alt = new StringBuilder();
		for (String w : words) {
			if (!alt.isEmpty()) {
				alt.append('|');
			}
			alt.append("(?<![a-z])").append(Pattern.quote(w));
		}
		Pattern p = Pattern.compile(alt.toString());
		return text -> p.matcher(text).find();
	}

	/** Whether {@code answer} is a reply (an MCA {@code main} answer). */
	public static boolean is(@Nullable String answer) {
		return answer != null && answer.startsWith(ANSWER_PREFIX);
	}

	/** The MCA answer for replying to a line of {@code topic}. */
	public static String answer(String topic) {
		return ANSWER_PREFIX + topic;
	}

	/**
	 * The topic of a line a villager said: {@code key} its translation key (null if it had none), {@code text} the words as
	 * shown. Never null: anything unknown is {@link #GENERAL}.
	 */
	public static String topicOf(@Nullable String key, String text) {
		String k = key == null ? "" : key;
		String t = text.toLowerCase(Locale.ROOT).trim();
		List<Topic> topics;
		synchronized (Replies.class) {
			topics = List.copyOf(TOPICS);
		}
		for (Topic topic : topics) {
			if (topic.keys().test(k)) {
				return topic.name();
			}
		}
		Topic greet = null;
		for (Topic topic : topics) {
			if (GREET.equals(topic.name())) {
				// a hello is the last thing a line is taken for: "Hey! How's your day going?" wants an answer
				greet = topic;
			} else if (topic.words().test(t)) {
				return topic.name();
			}
		}
		if (t.endsWith("?") || t.endsWith("?\"")) {
			return QUESTION;
		}
		if (t.startsWith("*")) {
			return GESTURE;
		}
		if ((greet != null && greet.words().test(t)) || k.contains("dialogue.greet")) {
			return GREET;
		}
		return GENERAL;
	}

	/** Whether {@code topic} is one the server can answer. */
	static boolean known(String topic) {
		return switch (topic) {
			case QUESTION, GESTURE, GENERAL -> true;
			default -> find(topic) != null;
		};
	}

	private static synchronized @Nullable Topic find(String name) {
		for (Topic t : TOPICS) {
			if (t.name().equals(name)) {
				return t;
			}
		}
		return null;
	}

	/**
	 * {@code player} picked the reply {@code answer} to what {@code who} said: their answer back, and hearts for hearing
	 * them out (once a day, not from one who is cold to you).
	 */
	public static Component reply(ServerPlayer player, LivingEntity who, String answer) {
		String topic = answer.substring(ANSWER_PREFIX.length());
		if (!known(topic)) {
			topic = GENERAL;
		}
		boolean cold = cold(player, who);
		if (!cold) {
			long day = who.level().getGameTime() / DAY;
			String pair = who.getUUID() + "/" + player.getUUID();
			Long last = HEARD.get(pair);
			if (last == null || last != day) {
				HEARD.put(pair, day);
				McaCompat.hearts(who, player, LISTEN_HEARTS);
			}
		}
		Topic t = find(topic);
		if (t != null && t.answer() != null) {
			return t.answer().apply(player, who);
		}
		if (QUESTION.equals(topic)) {
			return question(player, who);
		}
		return Component.translatable("talk_reply.jaymoskingdoms." + topic + "." + tone(player, who), Recollections.nameTo(player, who));
	}

	/** How {@code who} words it to {@code player}: cold to one they're wary of or dislike, else by their personality. */
	static String tone(ServerPlayer player, LivingEntity who) {
		return cold(player, who) ? "cold" : RefusalHints.tone(who);
	}

	private static boolean cold(ServerPlayer player, LivingEntity who) {
		if (who instanceof Villager v) {
			return Introductions.wary(player, v) || McaCompat.heartsOf(v, player) < RefusalHints.DISLIKE_HEARTS;
		}
		return false;
	}

	/** A warning answered "Seen any? Where?": the nearest monster about and which way, or none just now. */
	private static Component danger(ServerPlayer player, LivingEntity who) {
		String tone = tone(player, who);
		Component name = Recollections.nameTo(player, who);
		LivingEntity nearest = null;
		double best = DANGER_RANGE * DANGER_RANGE;
		for (LivingEntity e : who.level().getEntitiesOfClass(LivingEntity.class, who.getBoundingBox().inflate(DANGER_RANGE),
			e -> e instanceof Enemy && e.isAlive())) {
			double d = e.distanceToSqr(who);
			if (d < best) {
				best = d;
				nearest = e;
			}
		}
		if (nearest == null) {
			return Component.translatable("talk_reply.jaymoskingdoms.danger.none." + tone, name);
		}
		return Component.translatable("talk_reply.jaymoskingdoms.danger.seen." + tone, name, nearest.getType().getDescription(),
			Sightings.direction(who.blockPosition(), nearest.blockPosition()));
	}

	/** A word on the weather answered: what the sky is doing now. */
	private static Component weather(ServerPlayer player, LivingEntity who) {
		String sky = !(who.level() instanceof ServerLevel level) ? "clear"
			: level.isThundering() ? "storm" : level.isRaining() ? "rain" : "clear";
		return Component.translatable("talk_reply.jaymoskingdoms.weather." + sky + "." + tone(player, who), Recollections.nameTo(player, who));
	}

	/** Their question answered ("Well enough. And you?"): how they are, by their mood. */
	private static Component question(ServerPlayer player, LivingEntity who) {
		int mood = who instanceof Villager v ? McaCompat.mood(v) : 0;
		String how = mood >= 5 ? "good" : mood <= -5 ? "bad" : "fair";
		return Component.translatable("talk_reply.jaymoskingdoms.question." + how + "." + tone(player, who), Recollections.nameTo(player, who));
	}
}
