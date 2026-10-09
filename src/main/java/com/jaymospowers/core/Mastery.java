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
 * one as the player builds mastery: by using the abilities they have, and by time spent with the power. A power
 * picked in creative, or given with {@code /power set}, comes with every ability.
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

	/** Every ability. */
	public static final int ALL = 4;
	/** Mastery needed to have {@code i} abilities unlocked (the starter at 0; then the 1st-4th in unlock order). */
	public static final int[] UNLOCK_AT = {0, 20, 60, 120, 200};
	/** One point per this many ticks of survival play with the power... */
	public static final int TICKS_PER_TIME_POINT = 1200;
	/** ...and one per ability use, at most once per this many ticks (so mashing a key earns nothing extra). */
	public static final int USE_CREDIT_SPACING = 100;

	private static final Map<UUID, Long> LAST_USE_CREDIT = new HashMap<>();

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
		return power != null && (starterOnly(player) && power.starterKey() == key || power.usableEarly(key));
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
	}

	/** Everything unlocked (creative pick, /power set, losing the power). */
	static void unlockAll(ServerPlayer player) {
		player.removeAttached(UNLOCKED);
		player.removeAttached(POINTS);
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
		if (unlocked(player) >= ALL || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		if (player.tickCount % TICKS_PER_TIME_POINT == 0 && player.tickCount > 0) {
			addPoints(player, 1);
		}
	}

	/** An unlocked ability (or the starter) was used: that's practice (rate-limited). Powers with passive starters call it too. */
	public static void used(ServerPlayer player) {
		if (unlocked(player) >= ALL || player.isCreative()) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_USE_CREDIT.get(player.getUUID());
		if (last != null && now - last < USE_CREDIT_SPACING) {
			return;
		}
		LAST_USE_CREDIT.put(player.getUUID(), now);
		addPoints(player, 1);
	}

	static void forget(Player player) {
		LAST_USE_CREDIT.remove(player.getUUID());
	}

	private static void addPoints(ServerPlayer player, int amount) {
		int points = points(player) + amount;
		player.setAttached(POINTS, points);
		int unlocked = unlocked(player);
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
		Component ability = Component.translatable("power." + power.id().getNamespace() + "." + power.id().getPath() + ".key." + key.id())
			.withStyle(style -> style.withColor(TextColor.fromRgb(power.color())));
		player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 15));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.jaymospowers.unlocked")));
		player.connection.send(new ClientboundSetSubtitleTextPacket(ability));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.unlocked_chat", Component.keybind("key.jaymospowers." + key.id()), ability)
			.withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
	}
}
