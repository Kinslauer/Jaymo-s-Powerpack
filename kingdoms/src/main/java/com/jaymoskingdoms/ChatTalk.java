package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Talking to villagers through chat (Andy's Buder's Lanterns pick, ideas only). A line said aloud ({@link ProximityChat}) goes to
 * a villager when it starts with their name (within {@link #NAME_RANGE}), or when the speaker is looking at one (within
 * {@link #LOOK_RANGE}), or, for {@link #KEEP_TICKS} after, to the one they were already talking to: short replies keep it going.
 * The villager turns to them and answers over their head a moment later, from a small set of topics ({@link Topic}) filled from
 * what Kingdoms already knows: how they bear toward you, what they remember of you, their gossip, a rumour, their work and
 * their realm. When MCA's own chat AI is switched on, it answers instead and this keeps quiet.
 */
final class ChatTalk {
	// ---- tuning ----
	/** How far a line starting with a villager's name reaches them; how near one looked at must be (blocks). */
	static final double NAME_RANGE = 12.0, LOOK_RANGE = 8.0;
	/** How true a look counts (cosine of the angle off their middle). */
	static final double LOOK_COS = 0.96;
	/** How long a conversation stays open after the last line (ticks); how long they take to answer. */
	static final long KEEP_TICKS = 600L;
	static final int ANSWER_TICKS = 20;
	/** How far their answer carries (blocks). */
	static final double ANSWER_RANGE = 12.0;
	/** How many lines each answer has. */
	static final int LINES = 3;

	/** What a line is about, by the words in it (first match wins). */
	enum Topic {
		BYE("bye", "goodbye", "farewell", "see you", "later"),
		THANKS("thanks", "thank you", "cheers"),
		NAME("your name", "who are you", "who're you"),
		MOOD("how are you", "how're you", "how goes", "you well", "you alright"),
		WORK("your work", "your job", "what do you do", "work", "job"),
		TRADE("trade", "buy", "sell", "wares", "price"),
		REALM("realm", "king", "queen", "ruler", "lord", "where am i", "this place", "village", "town"),
		NEWS("news", "gossip", "rumour", "rumor", "heard", "anything new"),
		ME("remember me", "know me", "about me", "think of me"),
		HELLO("hello", "hi", "hey", "greetings", "good morning", "good evening", "morning", "evening");

		final String[] words;

		Topic(String... words) {
			this.words = words;
		}
	}

	private record Talk(UUID villager, long until) {
	}

	private record Answer(ServerPlayer player, Villager villager, Topic topic, long at) {
	}

	private static final Map<UUID, Talk> TALKS = new HashMap<>();
	private static final List<Answer> ANSWERS = new ArrayList<>();

	private ChatTalk() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ChatTalk::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TALKS.clear();
			ANSWERS.clear();
		});
	}

	/** (From {@link ProximityChat}) {@code speaker} said {@code text} aloud: to a villager, maybe, who answers. */
	static void heard(ServerPlayer speaker, String text) {
		if (text.isBlank() || text.startsWith(ProximityChat.ACT_MARK) || McaCompat.chatAI()) {
			return;
		}
		ServerLevel level = speaker.level();
		long now = level.getGameTime();
		String lower = text.toLowerCase(Locale.ROOT).strip();
		Villager to = named(speaker, lower);
		if (to == null) {
			to = lookedAt(speaker);
		}
		if (to == null) {
			Talk talk = TALKS.get(speaker.getUUID());
			if (talk != null && now < talk.until() && level.getEntity(talk.villager()) instanceof Villager v && v.isAlive()
				&& v.distanceTo(speaker) <= NAME_RANGE) {
				to = v;
			}
		}
		if (to == null || to.isSleeping() || to.isBaby()) {
			return;
		}
		Topic topic = topic(lower);
		TALKS.put(speaker.getUUID(), new Talk(to.getUUID(), now + KEEP_TICKS));
		if (topic == Topic.BYE) {
			TALKS.remove(speaker.getUUID());
		}
		to.getNavigation().stop();
		to.getLookControl().setLookAt(speaker);
		ANSWERS.add(new Answer(speaker, to, topic, now + ANSWER_TICKS));
	}

	/** The villager near enough whose first name starts the line. */
	private static @Nullable Villager named(ServerPlayer speaker, String lower) {
		for (Villager v : speaker.level().getEntitiesOfClass(Villager.class, speaker.getBoundingBox().inflate(NAME_RANGE),
			v -> v.isAlive() && v.distanceTo(speaker) <= NAME_RANGE)) {
			String first = v.getName().getString().split(" ")[0].toLowerCase(Locale.ROOT);
			if (first.length() >= 2 && (lower.equals(first) || lower.startsWith(first + " ") || lower.startsWith(first + ",") || lower.startsWith(first + "!"))) {
				return v;
			}
		}
		return null;
	}

	/** The villager {@code speaker} is looking right at, near enough and in plain sight. */
	private static @Nullable Villager lookedAt(ServerPlayer speaker) {
		Vec3 eye = speaker.getEyePosition(), look = speaker.getViewVector(1.0F);
		Villager best = null;
		double bestCos = LOOK_COS;
		for (Villager v : speaker.level().getEntitiesOfClass(Villager.class, speaker.getBoundingBox().inflate(LOOK_RANGE),
			v -> v.isAlive() && v.distanceTo(speaker) <= LOOK_RANGE && speaker.hasLineOfSight(v))) {
			Vec3 to = v.getEyePosition().subtract(eye).normalize();
			double cos = to.dot(look);
			if (cos > bestCos) {
				bestCos = cos;
				best = v;
			}
		}
		return best;
	}

	/** What {@code lower} is about, or null: talk they don't follow ({@link #heardWhat}). */
	static @Nullable Topic topic(String lower) {
		String padded = " " + lower.replaceAll("[^a-z' ]", " ") + " ";
		for (Topic t : Topic.values()) {
			for (String w : t.words) {
				if (padded.contains(" " + w + " ")) {
					return t;
				}
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (ANSWERS.isEmpty()) {
			return;
		}
		for (Iterator<Answer> it = ANSWERS.iterator(); it.hasNext();) {
			Answer a = it.next();
			if (a.villager().level().getGameTime() < a.at()) {
				continue;
			}
			it.remove();
			if (a.villager().isAlive() && !a.player().isRemoved() && a.villager().level() instanceof ServerLevel level) {
				a.villager().getLookControl().setLookAt(a.player());
				Speech.say(level, a.villager(), answer(a.player(), a.villager(), a.topic(), level.getRandom()), Speech.WHITE, ANSWER_RANGE);
			}
		}
	}

	/** {@code v}'s answer to {@code player} on {@code topic}, as they bear toward them. */
	static Component answer(ServerPlayer player, Villager v, @Nullable Topic topic, RandomSource random) {
		String k = "talk.jaymoskingdoms.chat.";
		Component name = Recollections.nameTo(player, v);
		String manner = Bearings.manner(v, player);
		boolean cold = manner.equals("cold") || manner.equals("wary") || manner.equals("scornful");
		if (topic == null) {
			return heardWhat(random);
		}
		switch (topic) {
			case NEWS -> {
				Component said = Suspicion.gossip(v, player, random);
				if (said == null) {
					said = Recollections.gossipLine(v, player, random);
				}
				if (said != null && !cold) {
					return said;
				}
			}
			case ME -> {
				Component said = Recollections.remark(v, player, random);
				if (said != null) {
					return said;
				}
				return Component.translatable(k + "me_nothing." + random.nextInt(LINES), name);
			}
			case NAME -> {
				return Component.translatable(k + "name." + (cold ? "cold" : "warm") + "." + random.nextInt(LINES), v.getName());
			}
			case WORK -> {
				Component job = Component.translatable("entity.minecraft.villager." + v.getVillagerData().profession().unwrapKey()
					.map(key -> key.identifier().getPath()).orElse("none"));
				return Component.translatable(k + "work." + random.nextInt(LINES), job);
			}
			case REALM -> {
				Faction f = Bearings.realm(v);
				return f == null ? Component.translatable(k + "realm_none." + random.nextInt(LINES))
					: Component.translatable(k + "realm." + random.nextInt(LINES), f.name());
			}
			default -> {
			}
		}
		return Component.translatable(k + topic.name().toLowerCase(Locale.ROOT) + "." + (cold ? "cold" : "warm") + "." + random.nextInt(LINES), name);
	}

	/** Talk they don't follow. */
	private static Component heardWhat(RandomSource random) {
		return Component.translatable("talk.jaymoskingdoms.chat.what." + random.nextInt(LINES));
	}
}
