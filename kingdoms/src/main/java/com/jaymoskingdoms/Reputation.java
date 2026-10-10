package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Reputation, the first of the two systems: what every faction (kingdom, fiefdom, village, camp) thinks of a player, member or not (-1000 to 1000).
 * Kill a kingdom's people and it hates you; fight its monsters, fill its coffers, and it warms to you. A kingdom
 * at {@link #HOSTILE} or below has its guards attack you on sight. (The second system is each kingdom's own record
 * of its members: rank and standing, in {@link Faction.Member}.)
 */
public final class Reputation {
	public static final int MIN = -1000, MAX = 1000;
	/** At or under this, a kingdom's guards attack you on sight; at or over {@link #WELCOME} it will take your pledge. */
	public static final int HOSTILE = -100, WELCOME = 0;

	/** Per player: kingdom id → reputation. Saved, kept through death. */
	static final AttachmentType<Map<String, Integer>> REPUTATION = AttachmentRegistry.create(JaymosKingdoms.id("reputation"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	private Reputation() {
	}

	public static int get(ServerPlayer player, int kingdom) {
		Map<String, Integer> map = player.getAttached(REPUTATION);
		return map == null ? 0 : map.getOrDefault(Integer.toString(kingdom), 0);
	}

	/** Shifts {@code player}'s reputation with {@code kingdom} by {@code by}; tells them when it crosses a threshold. */
	public static void change(ServerPlayer player, Faction kingdom, int by) {
		if (by == 0) {
			return;
		}
		int before = get(player, kingdom.id());
		int after = Mth.clamp(before + by, MIN, MAX);
		Map<String, Integer> map = new HashMap<>(player.getAttachedOrElse(REPUTATION, Map.of()));
		map.put(Integer.toString(kingdom.id()), after);
		player.setAttached(REPUTATION, Map.copyOf(map));
		if (before > HOSTILE && after <= HOSTILE) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.reputation.hostile", kingdom.name()).withStyle(ChatFormatting.RED));
		} else if (before <= HOSTILE && after > HOSTILE) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.reputation.forgiven", kingdom.name()).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** A word for a reputation. */
	public static String word(int rep) {
		return rep <= -500 ? "hated" : rep <= HOSTILE ? "hostile" : rep < 100 ? "neutral" : rep < 400 ? "friendly" : rep < 800 ? "honoured" : "revered";
	}
}
