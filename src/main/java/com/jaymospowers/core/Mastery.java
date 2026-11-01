package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Growing into a power. A power that awakens naturally starts with just its first ability (R); the rest unlock one by
 * one as the player builds mastery. Time spent with the power alone earns the next ability in about 3 in-game days;
 * using the abilities you have and meditating ({@link Meditate}) speed that up, to a day an ability at the most.
 * Training in someone else's Immense Field (Gravity's Z) pushes past that, to half a day; the field's own owner gets
 * that only while sparring inside it (GravityTrainingField). A power picked in creative, or given with
 * {@code /power set}, comes with every ability.
 */
public final class Mastery {
	/**
	 * How many abilities are unlocked (0-4). 0 is a fresh awakening: just the power's weak starter (see
	 * {@link Power#starterKey()}). Absent means all four (powers from creative, commands, older saves).
	 */
	public static final AttachmentType<Integer> UNLOCKED = AttachmentRegistry.create(JaymosPowers.id("unlocked"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** Mastery points earned so far. */
	public static final AttachmentType<Integer> POINTS = AttachmentRegistry.create(JaymosPowers.id("mastery"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/**
	 * (0.19.28) Rank points earned once every ability is learned: they climb the Mastery ranks ({@link Training#RANK_AT},
	 * Adept to Paragon), each a permanent stat jump.
	 */
	public static final AttachmentType<Integer> RANK_POINTS = AttachmentRegistry.create(JaymosPowers.id("mastery_rank"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** Every ability. */
	public static final int ALL = 4;
	/**
	 * Mastery needed to have {@code i} abilities unlocked (the starter at 0; then the 1st-4th in unlock order). (0.19.28)
	 * Each costs more than the last: 60, 90, 120, 150 (3, 4.5, 6 and 7.5 days from time alone; a third of that at the
	 * most with practice). Saves from the flat 60-an-ability scale keep what they learned and start at the new floor.
	 */
	public static final int[] UNLOCK_AT = {0, 60, 150, 270, 420};
	/** One point per this many ticks of survival play with the power: 60 points (an ability) in 3 in-game days. */
	public static final int TICKS_PER_TIME_POINT = 1200;
	/**
	 * Practice (ability use, meditating) earns points on top of time, but only as fast as time allows: each time point
	 * lets this many practice points through, so practice can at most triple the pace (a day an ability).
	 */
	public static final int PRACTICE_PER_TIME_POINT = 2;
	/** Practice allowance a player can bank (so a quiet spell can be caught up a little, not skipped). */
	public static final int PRACTICE_BANK = 6;
	/**
	 * Training in someone else's Immense Field: every practice point counts double, and the allowance refills by one more
	 * point per this many ticks (to six times the pace at the most: half a day an ability).
	 */
	public static final int FIELD_PRACTICE_MULTIPLIER = 2;
	public static final int FIELD_ALLOWANCE_TICKS = 400;
	/** One practice point per ability use, at most once per this many ticks (so mashing a key earns nothing extra). */
	public static final int USE_CREDIT_SPACING = 600;

	private static final Map<UUID, Long> LAST_USE_CREDIT = new HashMap<>();
	/** Practice points each player may still earn (see {@link #PRACTICE_PER_TIME_POINT}). */
	private static final Map<UUID, Integer> ALLOWANCE = new HashMap<>();

	private Mastery() {
	}

	public static int unlocked(Player player) {
		Integer unlocked = player.getAttached(UNLOCKED);
		return unlocked == null ? ALL : Mth.clamp(unlocked, 0, ALL);
	}

	/** True while the player has only the starter (a fresh awakening). */
	public static boolean starterOnly(Player player) {
		return unlocked(player) == 0;
	}

	/** Whether pressing {@code key} does anything: an unlocked ability, or the starter's key before anything unlocks. */
	public static boolean isUsable(Player player, PowerKey key) {
		if (isUnlocked(player, key)) {
			return true;
		}
		Power power = Powers.of(player);
		return power != null && (starterOnly(player) && power.starterKey() == key || power.usableEarly(player, key));
	}

	public static boolean isUnlocked(Player player, PowerKey key) {
		int unlocked = unlocked(player);
		if (unlocked >= ALL) {
			return true;
		}
		PowerKey[] order = order(player);
		for (int i = 0; i < unlocked && i < order.length; i++) {
			if (order[i] == key) {
				return true;
			}
		}
		return false;
	}

	/** The player's power's unlock order (key order if there's no power). */
	private static PowerKey[] order(Player player) {
		Power power = Powers.of(player);
		return power == null ? PowerKey.values() : power.unlockOrder();
	}

	public static int points(Player player) {
		Integer points = player.getAttached(POINTS);
		return points == null ? 0 : points;
	}

	/** (0.19.28) Rank points past the four abilities. */
	public static int rankPoints(Player player) {
		Integer points = player.getAttached(RANK_POINTS);
		return points == null ? 0 : points;
	}

	/** (0.19.28) The Mastery rank (0 none, 1 Adept ... 5 Paragon); always 0 until every ability is learned. */
	public static int rank(Player player) {
		return unlocked(player) < ALL ? 0 : Training.rankFor(rankPoints(player));
	}

	/** (0.19.28) Progress toward the next rank (0-1), or -1 at Paragon. Works on both sides. */
	public static float rankProgress(Player player) {
		int rank = rank(player);
		if (rank >= Training.RANKS) {
			return -1.0F;
		}
		int from = Training.RANK_AT[rank];
		int to = Training.RANK_AT[rank + 1];
		return Mth.clamp((rankPoints(player) - from) / (float)(to - from), 0.0F, 1.0F);
	}

	/** Nothing left to learn: every ability and the last rank. */
	private static boolean done(Player player) {
		return unlocked(player) >= ALL && rank(player) >= Training.RANKS;
	}

	/** Progress toward the next unlock (0-1), or -1 when everything is unlocked. Works on both sides. */
	public static float progress(Player player) {
		int unlocked = unlocked(player);
		if (unlocked >= ALL) {
			return -1.0F;
		}
		int from = UNLOCK_AT[unlocked];
		int to = UNLOCK_AT[unlocked + 1];
		return Mth.clamp((points(player) - from) / (float)(to - from), 0.0F, 1.0F);
	}

	/** A natural awakening: just the weak starter. */
	static void startFresh(ServerPlayer player) {
		player.setAttached(UNLOCKED, 0);
		player.setAttached(POINTS, 0);
		player.removeAttached(RANK_POINTS);
	}

	/** Everything unlocked (creative pick, /power set, losing the power). */
	static void unlockAll(ServerPlayer player) {
		player.removeAttached(UNLOCKED);
		player.removeAttached(POINTS);
		player.removeAttached(RANK_POINTS);
	}

	/** {@code /power level}: set how many abilities are unlocked, with the points to match. */
	public static void setLevel(ServerPlayer player, int abilities) {
		int level = Mth.clamp(abilities, 0, ALL);
		int before = unlocked(player);
		if (level >= ALL) {
			unlockAll(player);
		} else {
			player.setAttached(UNLOCKED, level);
			player.setAttached(POINTS, UNLOCK_AT[level]);
		}
		Power power = Powers.of(player);
		if (power != null && level > before) {
			power.onLevelUp(player, level);
		}
	}

	static void tick(ServerPlayer player) {
		if (done(player) || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		if (player.tickCount <= 0) {
			return;
		}
		if (player.tickCount % TICKS_PER_TIME_POINT == 0) {
			allow(player, PRACTICE_PER_TIME_POINT);
			addPoints(player, 1);
		}
		if (player.tickCount % FIELD_ALLOWANCE_TICKS == 0 && fieldBoosted(player)) {
			allow(player, 1);
		}
	}

	/** In someone else's Immense Field, or the owner sparring in his own (the field's gravity alone isn't enough for him). */
	public static boolean fieldBoosted(ServerPlayer player) {
		return com.jaymospowers.power.gravity.GravityTrainingField.boosts(player);
	}

	private static void allow(ServerPlayer player, int amount) {
		ALLOWANCE.merge(player.getUUID(), amount, (a, b) -> Math.min(PRACTICE_BANK, a + b));
	}

	/** Practice points, as far as the allowance goes (doubled in a field). */
	private static void practise(ServerPlayer player, int amount) {
		int wanted = amount * (fieldBoosted(player) ? FIELD_PRACTICE_MULTIPLIER : 1);
		int allowed = Math.min(wanted, ALLOWANCE.getOrDefault(player.getUUID(), 0));
		if (allowed <= 0) {
			return;
		}
		ALLOWANCE.put(player.getUUID(), ALLOWANCE.get(player.getUUID()) - allowed);
		addPoints(player, allowed);
	}

	/** An unlocked ability (or the starter) was used: that's practice (rate-limited). Powers with passive starters call it too. */
	public static void used(ServerPlayer player) {
		if (done(player) || player.isCreative()) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_USE_CREDIT.get(player.getUUID());
		if (last != null && now - last < USE_CREDIT_SPACING) {
			return;
		}
		LAST_USE_CREDIT.put(player.getUUID(), now);
		practise(player, 1);
	}

	/** Practice away from the abilities (meditating, sparring; see {@link Meditate}): {@code amount} points, as the allowance lets. */
	public static void practice(ServerPlayer player, int amount) {
		if (done(player) || player.isCreative() || Powers.of(player) == null) {
			return;
		}
		practise(player, amount);
	}

	/**
	 * (0.17.3) {@code amount} mastery points at once, past the practice allowance (a gifted villager's power taken by
	 * killing them, {@code core/gifted/GiftedAI}). Nothing once every ability is learned, in creative, or without a power.
	 */
	public static boolean grant(ServerPlayer player, int amount) {
		if (done(player) || player.isCreative() || Powers.of(player) == null) {
			return false;
		}
		addPoints(player, amount);
		return true;
	}

	/** (0.19.28) Meditating in a training spot ({@link Training#spot}): one more practice point allowed. */
	static void spotAllowance(ServerPlayer player) {
		allow(player, 1);
	}

	static void forget(Player player) {
		LAST_USE_CREDIT.remove(player.getUUID());
		Training.forget(player.getUUID());
		ALLOWANCE.remove(player.getUUID());
	}

	private static void addPoints(ServerPlayer player, int amount) {
		int unlocked = unlocked(player);
		if (unlocked >= ALL) {
			// (0.19.28) past the four abilities: the Mastery ranks
			int was = rank(player);
			player.setAttached(RANK_POINTS, Math.min(Training.RANK_AT[Training.RANKS], rankPoints(player) + amount));
			int now = rank(player);
			Power power = Powers.of(player);
			if (power != null) {
				for (int r = was + 1; r <= now; r++) {
					Training.announceRank(player, power, r);
				}
			}
			return;
		}
		// older saves earned abilities on a quicker scale: start them at the floor of what they already have
		int floor = unlocked < ALL ? UNLOCK_AT[unlocked] : 0;
		int points = Math.max(points(player), floor) + amount;
		player.setAttached(POINTS, points);
		if (unlocked < ALL && points >= UNLOCK_AT[unlocked + 1]) {
			player.setAttached(UNLOCKED, unlocked + 1);
			PowerKey[] order = order(player);
			announce(player, order[Math.min(unlocked, order.length - 1)]);
			Power power = Powers.of(player);
			if (power != null) {
				power.onLevelUp(player, unlocked + 1);
			}
		}
	}

	private static void announce(ServerPlayer player, PowerKey key) {
		Power power = Powers.of(player);
		if (power == null) {
			return;
		}
		// the title shows the ability's short name; the whole line is in chat, on hover
		String line = "power." + power.id().getNamespace() + "." + power.id().getPath() + ".key." + key.id();
		Component ability = Component.translatable(line + ".name").withStyle(style -> style.withColor(TextColor.fromRgb(power.color())));
		Component full = Component.translatable(line);
		// (0.19.28) the pack's milestone fanfare; the last ability is the grandest
		boolean last = unlocked(player) >= ALL;
		Component chat = Component.translatable("message.jaymospowers.unlocked_chat", Component.keybind("key.jaymospowers." + key.id()),
				ability.copy().withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(full))), Component.keybind("key.jaymospowers.menu"));
		if (last) {
			chat = chat.copy().append(" ").append(Component.translatable("training.jaymospowers.all_learned", power.name()));
		}
		Milestones.fanfare(player, Component.translatable(last ? "training.jaymospowers.mastered_title" : "message.jaymospowers.unlocked"), ability, chat,
			power.color(), last ? Milestones.GRAND : Milestones.MAJOR);
	}
}
