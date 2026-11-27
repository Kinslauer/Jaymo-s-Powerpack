package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-25): "if a chat option is refused, you should get a hint as to why". When a talk option is turned down
 * (MCA's chat, joke, story, flirt, hug, kiss, greeting or apology going badly, or one of ours: a grudge, a wary stranger,
 * too few hearts, a bad name in town, a hand left hanging), the villager follows the refusal with a short line, in a
 * bubble, that names the real cause ({@link Cause}), worded by their personality ({@link #tone}). One who doesn't share
 * your tongue can't tell you, so they show you instead: a gesture you can follow in any tongue ({@link Speech#sayPlainly}).
 * No chat text: bubbles only.
 */
public final class RefusalHints {
	/** Why they said no. */
	public enum Cause {
		/** They've talked enough today (MCA's interaction fatigue). */
		TIRED,
		/** You're not close enough yet (too few hearts, but none against you). */
		HEARTS,
		/** They don't like you (hearts below nothing). */
		DISLIKE,
		/** A bad mood, not you. */
		MOOD,
		/** Their personality isn't one for it ({@link #natureLine}). */
		NATURE,
		/** You've said or asked it before. */
		REPEAT,
		/** A tale they don't believe: you've not done what you claim. */
		DOUBT,
		/** Nothing against you: it just didn't land (MCA's dice). */
		LUCK,
		/** Something you did that they remember ({@link Recollections}): the worst of it, in their words. */
		GRUDGE,
		/** No warmth for you ({@link Bearings#warmth}). */
		COLD,
		/** No trust in you ({@link Bearings#trust}). */
		DISTRUST,
		/** They mind your blood ({@link Prejudice#minds}). */
		BLOOD,
		/** Your name is bad in their town ({@link Reputation}); the line names it. */
		REPUTE,
		/** A child told not to talk to strangers. */
		YOUNG;

		String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Two hints to the same player this close together (ticks) are one: a refusal that goes through two checks says why once. */
	static final int GAP = 20;
	/** Hearts below this count as liking you less than a stranger ({@link Cause#DISLIKE}). */
	static final int DISLIKE_HEARTS = 0;
	/** Hearts below this (and not below {@link #DISLIKE_HEARTS}) are "we hardly know each other" ({@link Cause#HEARTS}). */
	static final int CLOSE_HEARTS = 25;

	/** player → the game time of their last hint. */
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private RefusalHints() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}

	/**
	 * {@code who} turned {@code player} down for {@code cause}: they say why (in their tongue, if {@code player} shares it),
	 * or show it. {@code town} names the town for {@link Cause#REPUTE}.
	 */
	public static void hint(ServerPlayer player, LivingEntity who, Cause cause, @Nullable Component town) {
		if (!(who.level() instanceof ServerLevel level) || !who.isAlive()) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now >= last && now - last < GAP) {
			return;
		}
		LAST.put(player.getUUID(), now);
		if (!Languages.understands(player, who)) {
			Speech.sayPlainly(level, who, player, gesture(cause), Speech.WHITE);
			return;
		}
		Speech.tell(level, who, player, line(player, who, cause, town), Speech.WHITE);
	}

	/** {@link #hint}, for a refusal that comes down to how {@code who} stands with {@code player} ({@link #standing}). */
	public static void hintStanding(ServerPlayer player, LivingEntity who) {
		Cause c = standing(player, who);
		hint(player, who, c == null ? Cause.DISLIKE : c, town(player, who));
	}

	/** {@link #hint} for one who turned down a hand or a word because they {@linkplain Introductions#wary mind you}. */
	public static void hintWary(ServerPlayer player, Villager who) {
		Cause c = wary(player, who);
		hint(player, who, c, town(player, who));
	}

	/** {@link #hint} with {@code who}'s town named. */
	public static void hint(ServerPlayer player, LivingEntity who, Cause cause) {
		hint(player, who, cause, cause == Cause.REPUTE ? town(player, who) : null);
	}

	// ---- why ------------------------------------------------------------------------------------------------------

	/**
	 * Why {@code who} won't have it of {@code player}, by what stands between them, in the order {@link Recollections#refuses}
	 * and {@link Introductions#wary} weigh it: a grudge, no warmth, no trust, your blood, your name in their town, few
	 * hearts. Null when nothing stands between you.
	 */
	public static @Nullable Cause standing(ServerPlayer player, LivingEntity who) {
		if (Recollections.leaning(who, player) <= Recollections.REFUSE) {
			return Cause.GRUDGE;
		}
		if (Bearings.warmth(who, player) <= Bearings.REFUSE_WARMTH) {
			return Cause.COLD;
		}
		if (Bearings.trust(who, player) <= Bearings.REFUSE_TRUST) {
			return Cause.DISTRUST;
		}
		if (Prejudice.minds(who, player) > 0.0F) {
			return Cause.BLOOD;
		}
		if (who.level() instanceof ServerLevel level && who instanceof net.minecraft.world.entity.Mob mob) {
			Faction f = Approaches.home(level, mob);
			if (f != null && Reputation.get(player, f.id()) < Reputation.WELCOME) {
				return Cause.REPUTE;
			}
		}
		if (who instanceof Villager v) {
			int hearts = McaCompat.heartsOf(v, player);
			if (hearts < DISLIKE_HEARTS) {
				return Cause.DISLIKE;
			}
			if (hearts < CLOSE_HEARTS) {
				return Cause.HEARTS;
			}
		}
		return null;
	}

	/** Why {@code who} is {@linkplain Introductions#wary wary} of {@code player}, as that check finds it. */
	static Cause wary(ServerPlayer player, Villager who) {
		if (Recollections.leaning(who, player) <= Recollections.COLD) {
			return Cause.GRUDGE;
		}
		if (Prejudice.minds(who, player) > 0.0F) {
			return Cause.BLOOD;
		}
		Faction f = Approaches.home(player.level(), who);
		if (f != null && Reputation.get(player, f.id()) < Reputation.WELCOME) {
			return Cause.REPUTE;
		}
		return McaCompat.heartsOf(who, player) < DISLIKE_HEARTS ? Cause.DISLIKE : Cause.HEARTS;
	}

	/** {@link Cause#DISLIKE} or {@link Cause#HEARTS}, by {@code who}'s hearts for {@code player}. */
	public static Cause hearts(ServerPlayer player, LivingEntity who) {
		return who instanceof Villager v && McaCompat.heartsOf(v, player) < DISLIKE_HEARTS ? Cause.DISLIKE : Cause.HEARTS;
	}

	/** Why a hand held out to {@code who} was left hanging ({@link Handshakes}), by what {@link Introductions#takesHand} weighs. */
	static Cause handRefused(ServerPlayer player, Villager who) {
		Cause c = standing(player, who);
		if (c == Cause.GRUDGE || c == Cause.COLD || c == Cause.DISTRUST) {
			return c;
		}
		int hearts = McaCompat.heartsOf(who, player);
		if (hearts < DISLIKE_HEARTS) {
			return Cause.DISLIKE;
		}
		if (McaCompat.mood(who) < 0) {
			return Cause.MOOD;
		}
		if (McaCompat.temper(who) < 0) {
			return Cause.NATURE;
		}
		return hearts < CLOSE_HEARTS ? Cause.HEARTS : Cause.LUCK;
	}

	// ---- what they say -----------------------------------------------------------------------------------------------

	/** How {@code who} words a no: gentle, sharp, timid, odd, or a child's; sharp for anyone without MCA's personality. */
	static String tone(LivingEntity who) {
		if (who.isBaby()) {
			return "child";
		}
		String p = who instanceof Villager v ? McaCompat.personality(v) : "";
		return switch (p) {
			case "friendly", "upbeat", "peaceful", "relaxed", "extroverted", "playful", "flirty" -> "gentle";
			case "introverted", "anxious", "sensitive" -> "timid";
			case "odd" -> "odd";
			default -> "sharp";
		};
	}

	private static Component line(ServerPlayer player, LivingEntity who, Cause cause, @Nullable Component town) {
		Component name = Recollections.nameTo(player, who);
		if (cause == Cause.GRUDGE) {
			// the worst they remember of you, in the words they'd bring it up with
			Component worst = Recollections.worstLine(who, player, who.getRandom());
			if (worst != null) {
				return worst;
			}
		}
		if (cause == Cause.NATURE) {
			return natureLine(who, name);
		}
		Component where = town != null ? town : Component.translatable("refusal.jaymoskingdoms.here");
		return Component.translatable("refusal.jaymoskingdoms." + cause.key() + "." + tone(who), name, where);
	}

	/** {@link Cause#NATURE}: a line for {@code who}'s own personality. */
	private static Component natureLine(LivingEntity who, Component name) {
		if (who.isBaby()) {
			return Component.translatable("refusal.jaymoskingdoms.nature.child", name);
		}
		String p = who instanceof Villager v ? McaCompat.personality(v) : "";
		return Component.translatable("refusal.jaymoskingdoms.nature." + (NATURES.contains(p) ? p : "plain"), name);
	}

	/** MCA's personalities that have a {@link Cause#NATURE} line of their own. */
	private static final java.util.Set<String> NATURES = java.util.Set.of("friendly", "flirty", "playful", "gloomy", "sensitive",
		"greedy", "odd", "crabby", "extroverted", "introverted", "relaxed", "anxious", "peaceful", "upbeat");

	/** What a villager without your tongue shows you for {@code cause}. */
	static Component gesture(Cause cause) {
		return Component.translatable("refusal.jaymoskingdoms.gesture." + cause.key());
	}

	/** {@code who}'s town (their settlement), for {@link Cause#REPUTE}; null if they have none. */
	private static @Nullable Component town(ServerPlayer player, LivingEntity who) {
		if (who.level() instanceof ServerLevel level && who instanceof net.minecraft.world.entity.Mob mob) {
			Faction f = Approaches.home(level, mob);
			return f == null ? null : Component.literal(f.name());
		}
		return null;
	}

	// ---- MCA's own lines, to one who doesn't share the tongue -------------------------------------------------------------

	/**
	 * ({@code McaMessageMixin}) {@code speaker} said MCA's line {@code key} to {@code player}, who heard only words they don't
	 * know. If it was a no that MCA words with its reason (a gift they don't want, a ring when they're wed, "we already talked
	 * a lot today"), they show it too.
	 */
	public static void foreignLine(ServerPlayer player, LivingEntity speaker, String key) {
		if (!(speaker.level() instanceof ServerLevel level)) {
			return;
		}
		String g = gestureFor(key);
		if (g == null) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now >= last && now - last < GAP) {
			return;
		}
		LAST.put(player.getUUID(), now);
		Speech.sayPlainly(level, speaker, player, Component.translatable("refusal.jaymoskingdoms.gesture." + g), Speech.WHITE);
	}

	/** The gesture for MCA's refusal line {@code key} ({@code gift.meats.fail/2}, {@code interaction.relationship.fail.married/1}...), or null. */
	static @Nullable String gestureFor(String key) {
		int slash = key.indexOf('/');
		String k = slash < 0 ? key : key.substring(0, slash);
		if (k.endsWith("dialogue.busy")) {
			return "tired";
		}
		if (k.contains("gift.") && (k.endsWith(".fail") || k.contains(".fail."))) {
			return "gift";
		}
		if (k.contains(".fail.") && (k.contains("relationship") || k.contains("marry") || k.contains("engage") || k.contains("matchmaker"))) {
			if (k.endsWith(".married") || k.endsWith(".engaged") || k.endsWith(".marriedtogiver")) {
				return "wed";
			}
			if (k.endsWith(".playermarried")) {
				return "wed_you";
			}
			if (k.endsWith(".isparent")) {
				return "kin";
			}
			if (k.endsWith(".isbaby")) {
				return "young";
			}
			if (k.endsWith(".lowhearts")) {
				return "hearts";
			}
			if (k.endsWith(".incompatible")) {
				return "nature";
			}
		}
		return null;
	}
}
