package com.jaymoskingdoms;

import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * What {@link Stats.Stat#PRESENCE} does, fifth edition's Charisma made ours. Three things:
 * <ul>
 * <li>{@link #talk}: the better odds a player has when they persuade, lie, threaten or haggle ({@link Persuasion},
 * {@link Approaches}). A quick mind does as well as a commanding voice at it: the better of Presence and Wits counts.</li>
 * <li>{@link #warmed}: strangers warm to a player faster. A villager who is not yet a friend (under
 * {@link Strangers#TRUSTED_HEARTS} hearts) gives a good deed more hearts the more Presence they have.</li>
 * <li>{@link #reach}: how far a player's orders carry to their companions ({@link Followers}).</li>
 * </ul>
 */
public final class Presence {
	/** On a chance of 0 to 1, per point of the better of Presence and Wits (100 points: +0.4). */
	public static final float TALK_PER_POINT = 0.004F;
	/** More hearts from a good deed per point of Presence (100 points: half again as many). */
	public static final float WARM_PER_POINT = 0.005F;
	/** More reach for orders per point of Presence (100 points: sixty in a hundred more). */
	public static final float ORDERS_PER_POINT = 0.006F;

	private static @Nullable MinecraftServer server;

	private Presence() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
	}

	/** The points that carry a word: the better of {@code player}'s Presence and Wits. */
	public static int skill(Player player) {
		return Math.max(0, Math.max(Stats.value(player, Stats.Stat.PRESENCE), Stats.value(player, Stats.Stat.WITS)) - Carousing.eyePenalty(player));
	}

	/** The extra chance (0 to {@link #TALK_PER_POINT} times 100) {@code player} brings to talking people round. */
	public static float talk(Player player) {
		return TALK_PER_POINT * skill(player);
	}

	/** {@code hearts} a villager is about to gain toward the player {@code who}, more if they are still a stranger with a good Presence. */
	public static int warmed(UUID who, int current, int hearts) {
		if (hearts <= 0 || current >= Strangers.TRUSTED_HEARTS || server == null) {
			return hearts;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(who);
		if (player == null) {
			return hearts;
		}
		float more = WARM_PER_POINT * Stats.value(player, Stats.Stat.PRESENCE);
		float exact = hearts * (1.0F + more);
		int whole = (int)exact;
		return whole + (player.getRandom().nextFloat() < exact - whole ? 1 : 0);
	}

	/** {@code base} blocks, stretched by {@code player}'s Presence: how far their orders carry. */
	public static double reach(Player player, double base) {
		return base * (1.0 + ORDERS_PER_POINT * Stats.value(player, Stats.Stat.PRESENCE));
	}
}
