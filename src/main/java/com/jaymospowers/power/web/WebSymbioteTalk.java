package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * It answers you. A bonded host whose bond has reached the Integrated stage (the point it narrates and whispers freely)
 * can talk to the symbiote by starting a chat line with {@link #PREFIX}: the line is not broadcast to anyone, and after a
 * short wait it answers in a whisper only its host sees (with the whisper's own screen effect). The reply is picked by
 * keyword, then by the state it reads: hunger, mood, trust, what is being fought or looked at, the grudges, fury, the big
 * suit, the hour, the host's name. Insults sour it and thanks warm it a little (each at most once per
 * {@link #REACT_GAP} ticks). The lines are {@code message.jaymospowers.web_symbiote.talk.*} ({@code lang/parts/web_talk.json}).
 */
public final class WebSymbioteTalk {
	// ================================================================================================================
	// Tuning
	/** A chat line starting with this is spoken to the symbiote. */
	public static final String PREFIX = "~";
	/** It listens from this bond (the Integrated stage). */
	static final int LISTEN_BOND = WebSymbioteMind.STAGE_AT[1];
	/** It answers this many ticks after (at least, at most), and not to a second line within {@code TALK_GAP} ticks of the first. */
	static final int DELAY_MIN = 15, DELAY_MAX = 40, TALK_GAP = 60;
	/** An insult or thanks moves it only this often (ticks). */
	static final long REACT_GAP = 6000L;
	/** What an insult or thanks does: stress and trust. */
	static final int INSULT_STRESS = 8, INSULT_TRUST = 1, THANKS_STRESS = 6, THANKS_TRUST = 1;
	/** Satiety bands; trust bands; fury bands. */
	static final int HUNGRY_BELOW = 45, TRUST_LOW = 30, TRUST_HIGH = 70, FURY_WARM = 20, FURY_HOT = 60;
	/** What it looks at: the furthest (blocks) and the least aligned (dot of look and direction). */
	static final double LOOK_RANGE = 32.0, LOOK_ALIGN = 0.97;
	/** A foe fought this recently (ticks since a hit given or taken) is "what we are fighting". */
	static final int FIGHT_RECENT = 120;
	static final int LINES = 3;

	private static final String[] INSULT = {"stupid", "idiot", "useless", "worthless", "hate you", "shut up", "ugly", "dumb", "disgusting", "parasite",
		"leech", "stink", "trash", "garbage", "shut it", "moron"};
	private static final String[] THANKS = {"thank", "good job", "well done", "nice one", "love you", "good boy", "good girl", "proud", "great job",
		"well played", "praise"};
	private static final String[] GRUDGE = {"who hurt", "grudge", "revenge", "avenge", "who did", "hurt us", "hurt me", "owe", "payback"};
	private static final String[] FURY = {"angry", "anger", "fury", "rage", "temper", "calm down", "furious"};
	private static final String[] SUIT = {"suit", "big one", "bigger", "let me out", "let you out", "hulk", "grow", "release", "free"};
	private static final String[] SLEEP = {"sleep", "night", "dark", "bed", "tired", "rest", "dream"};
	private static final String[] NAME = {"my name", "who am i", "call me", "what am i called", "name"};
	private static final String[] TRUST = {"trust", "like me", "love", "friend", "loyal", "stay", "do you care"};
	private static final String[] HUNGER = {"hungry", "hunger", "food", "eat", "feed", "starv", "full", "meal", "starving"};
	private static final String[] MOOD = {"how are you", "how do you feel", "feel", "mood", "you ok", "you okay", "alright", "doing", "how are we"};
	private static final String[] FOE = {"fighting", "fight", "looking at", "see that", "what is that", "what's that", "who is that", "that thing", "watch", "foe"};

	private record Pending(String text, long due) {
	}

	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TALK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_INSULT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_THANKS = new ConcurrentHashMap<>();

	private WebSymbioteTalk() {
	}

	static void register() {
		// (ahead of the other chat handlers: Jaymo's Kingdoms speaks every line aloud to the people near, and this one must not be heard)
		Identifier first = JaymosPowers.id("web_symbiote_talk");
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.addPhaseOrdering(first, Event.DEFAULT_PHASE);
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(first, (message, sender, params) -> !heard(sender, message.signedContent()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) {
				return;
			}
			for (Map.Entry<UUID, Pending> entry : PENDING.entrySet()) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null) {
					PENDING.remove(entry.getKey());
				} else if (player.level().getGameTime() >= entry.getValue().due()) {
					PENDING.remove(entry.getKey());
					if (player.isAlive() && WebSymbiote.isBonded(player)) {
						answer(player, entry.getValue().text());
					}
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			PENDING.remove(id);
			LAST_TALK.remove(id);
			LAST_INSULT.remove(id);
			LAST_THANKS.remove(id);
		});
	}

	/** A chat line: whether it was meant for the symbiote (and so is kept from the chat). */
	private static boolean heard(ServerPlayer sender, String raw) {
		String text = raw.strip();
		if (!text.startsWith(PREFIX) || !WebSymbiote.isBonded(sender)) {
			return false;
		}
		long now = sender.level().getGameTime();
		text = text.substring(PREFIX.length()).strip();
		Long last = LAST_TALK.get(sender.getUUID());
		if ((last != null && now - last < TALK_GAP) || PENDING.containsKey(sender.getUUID())) {
			// (still thinking about the last one)
			return true;
		}
		LAST_TALK.put(sender.getUUID(), now);
		if (WebSymbiote.state(sender).bond() < LISTEN_BOND) {
			PENDING.put(sender.getUUID(), new Pending(null, now + DELAY_MIN + sender.getRandom().nextInt(DELAY_MAX - DELAY_MIN + 1)));
			return true;
		}
		PENDING.put(sender.getUUID(), new Pending(text.toLowerCase(Locale.ROOT), now + DELAY_MIN + sender.getRandom().nextInt(DELAY_MAX - DELAY_MIN + 1)));
		return true;
	}

	private static boolean has(String text, String[] words) {
		for (String word : words) {
			if (text.contains(word)) {
				return true;
			}
		}
		return false;
	}

	private static String pick(ServerPlayer player, String key) {
		return key + "." + (1 + player.getRandom().nextInt(LINES));
	}

	private static void answer(ServerPlayer player, @Nullable String text) {
		if (text == null) {
			// (too little bond to follow what you say)
			WebSymbiote.whisper(player, pick(player, "talk.deaf"));
			return;
		}
		WebSymbioteState state = WebSymbiote.state(player);
		WebSymbioteMind.Mind mind = WebSymbioteMind.mind(player);
		long now = player.level().getGameTime();
		UUID id = player.getUUID();
		if (has(text, INSULT)) {
			Long last = LAST_INSULT.get(id);
			if (last == null || now - last >= REACT_GAP) {
				LAST_INSULT.put(id, now);
				WebSymbiote.addStress(player, INSULT_STRESS);
				WebSymbioteMind.addTrust(player, -INSULT_TRUST);
			}
			WebSymbiote.whisper(player, pick(player, "talk.insult"));
		} else if (has(text, THANKS)) {
			Long last = LAST_THANKS.get(id);
			if (last == null || now - last >= REACT_GAP) {
				LAST_THANKS.put(id, now);
				WebSymbiote.addStress(player, -THANKS_STRESS);
				WebSymbioteMind.addTrust(player, THANKS_TRUST);
			}
			WebSymbiote.whisper(player, pick(player, "talk.thanks"));
		} else if (has(text, GRUDGE)) {
			var entries = WebGrudges.book(player).entries();
			if (entries.isEmpty()) {
				WebSymbiote.whisper(player, pick(player, "talk.grudge.none"));
			} else {
				WebGrudges.Entry worst = entries.get(0);
				for (WebGrudges.Entry entry : entries) {
					if (entry.damage() > worst.damage()) {
						worst = entry;
					}
				}
				WebSymbiote.whisper(player, pick(player, worst.player() ? "talk.grudge.player" : "talk.grudge.mob"), worst.display());
			}
		} else if (has(text, FURY)) {
			int fury = WebFury.fury(player);
			WebSymbiote.whisper(player, pick(player, fury >= FURY_HOT ? "talk.fury.hot" : fury >= FURY_WARM ? "talk.fury.warm" : "talk.fury.calm"));
		} else if (has(text, SUIT)) {
			String kind = !state.has(WebSymbioteState.BULK) && state.bond() < WebSymbiote.BOND_MAX ? "talk.suit.not_yet"
				: WebSymbiote.bulked(player) ? "talk.suit.out" : "talk.suit.caged";
			WebSymbiote.whisper(player, pick(player, kind));
		} else if (has(text, SLEEP)) {
			WebSymbiote.whisper(player, pick(player, player.level().dimensionType().hasSkyLight() && player.level().isDarkOutside() ? "talk.sleep.night" : "talk.sleep.day"));
		} else if (has(text, NAME)) {
			WebSymbiote.whisper(player, pick(player, "talk.name"), player.getName());
		} else if (has(text, TRUST)) {
			WebSymbiote.whisper(player, pick(player, mind.trust() >= TRUST_HIGH ? "talk.trust.high" : mind.trust() >= TRUST_LOW ? "talk.trust.mid" : "talk.trust.low"));
		} else if (has(text, HUNGER)) {
			WebSymbiote.whisper(player, pick(player, state.satiety() <= WebSymbiote.STARVING ? "talk.hunger.starving"
				: state.satiety() < HUNGRY_BELOW ? "talk.hunger.hungry" : "talk.hunger.fed"));
		} else if (has(text, MOOD)) {
			WebSymbiote.whisper(player, pick(player, "talk.mood." + mind.moodNow().key()));
		} else if (has(text, FOE)) {
			foe(player);
		} else {
			WebSymbiote.whisper(player, "talk.other." + (1 + player.getRandom().nextInt(4)));
		}
	}

	/** What it says of what you fight, or look at (its kind named), or of nothing. */
	private static void foe(ServerPlayer player) {
		LivingEntity fighting = player.getLastHurtMob();
		if (fighting == null || !fighting.isAlive() || player.tickCount - player.getLastHurtMobTimestamp() > FIGHT_RECENT) {
			fighting = player.getLastHurtByMob();
			if (fighting != null && (!fighting.isAlive() || player.tickCount - player.getLastHurtByMobTimestamp() > FIGHT_RECENT)) {
				fighting = null;
			}
		}
		if (fighting != null) {
			WebSymbiote.whisper(player, pick(player, "talk.foe.fight"), fighting.getType().getDescription());
			return;
		}
		LivingEntity seen = looked(player);
		if (seen != null) {
			WebSymbiote.whisper(player, pick(player, "talk.foe.look"), seen.getType().getDescription());
		} else {
			WebSymbiote.whisper(player, pick(player, "talk.foe.none"));
		}
	}

	/** The creature most nearly dead ahead, within {@link #LOOK_RANGE}. */
	private static @Nullable LivingEntity looked(ServerPlayer player) {
		Vec3 eyes = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestDot = LOOK_ALIGN;
		for (LivingEntity other : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(LOOK_RANGE),
			e -> e != player && e.isAlive() && !e.isSpectator() && player.hasLineOfSight(e))) {
			Vec3 to = other.getBoundingBox().getCenter().subtract(eyes);
			double length = to.length();
			if (length < 1.0E-3 || length > LOOK_RANGE) {
				continue;
			}
			double dot = to.scale(1.0 / length).dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = other;
			}
		}
		return best;
	}

}
