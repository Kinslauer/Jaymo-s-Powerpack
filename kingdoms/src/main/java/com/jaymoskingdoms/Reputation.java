package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Reputation, the first of the two systems: what every faction (kingdom, fiefdom, village, camp) thinks of a player, member or not (-1000 to 1000).
 * Kill a kingdom's people and it hates you; fight its monsters, fill its coffers, and it warms to you. A kingdom
 * at {@link #HOSTILE} or below has its guards attack you on sight. (The second system is each kingdom's own record
 * of its members: rank and standing, in {@link Faction.Member}.)
 */
public final class Reputation {
	public static final int MIN = -1000, MAX = 1000;
	/** Jaymo's Powers' Void Crown: while it's worn, every faction thinks the worst of you. */
	static final Identifier VOID_CROWN = Identifier.fromNamespaceAndPath("jaymospowers", "void_crown");
	/** At or under this, a kingdom's guards attack you on sight; at or over {@link #WELCOME} it will take your pledge. */
	public static final int HOSTILE = -100, WELCOME = 0;

	/** Per player: kingdom id → reputation. Saved, kept through death. */
	static final AttachmentType<Map<String, Integer>> REPUTATION = AttachmentRegistry.create(JaymosKingdoms.id("reputation"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	private Reputation() {
	}

	/**
	 * What {@code kingdom} thinks of {@code player} now: at its lowest while they wear the Void Crown (Jaymo's Powers);
	 * with Minecraft Comes Alive, warmed (or cooled) by the hearts its people have for them ({@link McaCompat}).
	 */
	public static int get(ServerPlayer player, int kingdom) {
		String persona = Identities.persona(player, kingdom);
		if (persona != null) {
			// (0.7.62) a masked stranger: what they think of the costume (and no hearts: nobody knows them)
			return voidCrowned(player) ? MIN : Identities.reputation(player.level().getServer(), persona, kingdom);
		}
		return voidCrowned(player) ? MIN : Mth.clamp(stored(player, kingdom) + McaCompat.heartsReputation(player, kingdom), MIN, MAX);
	}

	/** The reputation earned, whatever's worn (the crown hides it but doesn't wipe it). */
	private static int stored(ServerPlayer player, int kingdom) {
		Map<String, Integer> map = player.getAttached(REPUTATION);
		return map == null ? 0 : map.getOrDefault(Integer.toString(kingdom), 0);
	}

	/** Jaymo's Powers' Void Crown on their head: looked up by id, so neither mod needs the other. */
	static boolean voidCrowned(ServerPlayer player) {
		return VOID_CROWN.equals(BuiltInRegistries.ITEM.getKey(player.getItemBySlot(EquipmentSlot.HEAD).getItem()));
	}

	/** Shifts {@code player}'s reputation with {@code kingdom} by {@code by}; tells them when it crosses a threshold. */
	public static void change(ServerPlayer player, Faction kingdom, int by) {
		if (by == 0) {
			return;
		}
		// (0.5.12) as its ruler's temper takes it
		by = Tempers.reputation(kingdom, by);
		// (0.7.98) and a purist ruler's, by your blood
		by = Purists.reputation(kingdom, player, by);
		String persona = Identities.persona(player, kingdom.id());
		if (persona != null) {
			// (0.7.62) done in a mask they don't see through: it's the costume's name that's made (or ruined)
			Identities.changeReputation(player, persona, kingdom, by);
			return;
		}
		// (0.6.6) a settlement that survived a raid you brought barely warms to you again
		by = Omens.gain(player, kingdom, by);
		int before = stored(player, kingdom.id());
		int after = Mth.clamp(before + by, MIN, by > 0 ? Math.max(before, Omens.ceiling(player, kingdom)) : MAX);
		Map<String, Integer> map = new HashMap<>(player.getAttachedOrElse(REPUTATION, Map.of()));
		map.put(Integer.toString(kingdom.id()), after);
		player.setAttached(REPUTATION, Map.copyOf(map));
		if (before > HOSTILE && after <= HOSTILE) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.reputation.hostile", kingdom.name()).withStyle(ChatFormatting.RED));
		} else if (before <= HOSTILE && after > HOSTILE) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.reputation.forgiven", kingdom.name()).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** (0.5.1) All the good name {@code player} had with {@code kingdom} gone (a bad one stays). */
	public static void wipe(ServerPlayer player, Faction kingdom) {
		int stored = stored(player, kingdom.id());
		if (stored > 0) {
			change(player, kingdom, -stored);
		}
	}

	/** A word for a reputation. */
	public static String word(int rep) {
		return rep <= -500 ? "hated" : rep <= HOSTILE ? "hostile" : rep < 100 ? "neutral" : rep < 400 ? "friendly" : rep < 800 ? "honoured" : "revered";
	}
}
