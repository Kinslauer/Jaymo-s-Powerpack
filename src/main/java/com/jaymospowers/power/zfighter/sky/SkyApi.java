package com.jaymospowers.power.zfighter.sky;

import com.jaymospowers.power.zfighter.ZFighterPodTrip;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * (0.18.52) What Jaymo's Kingdoms asks of the star charts, by reflection (all static, all public, simple types, so a change
 * here shows up as a missing method on Kingdoms' side rather than a crash): who sells which chart, the sale itself, and who
 * has not yet flown.
 */
public final class SkyApi {
	private SkyApi() {
	}

	/** The next {@code count} charts {@code player} hasn't read, cheapest trip first, as world indices. */
	public static List<Integer> unread(ServerPlayer player, int count) {
		return Charts.nextUnread(player, count);
	}

	/** How many places a world is down the cost order (0: the Pale Moon), which sets what a chart costs. */
	public static int rank(int world) {
		return Math.max(0, Charts.byCost().indexOf(world));
	}

	/** The chart of {@code world} for {@code player}'s hands: false if there is no such chart or it is already read. */
	public static boolean sell(ServerPlayer player, int world) {
		if (world < 0 || world >= Charts.WORLDS || Charts.read(player, world)) {
			return false;
		}
		ItemStack chart = Charts.stack(world);
		if (!player.getInventory().add(chart)) {
			player.drop(chart, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		return true;
	}

	/** The world's name as a component, for the seller's words. */
	public static Component name(int world) {
		return Component.translatable("space.jaymospowers.dest." + ZFighterSpace.DESTS[world].key());
	}

	/** How many trips {@code player} has flown in a pod (0: before they had one). */
	public static int flights(ServerPlayer player) {
		return player.getAttachedOrElse(ZFighterPodTrip.FLIGHTS, 0);
	}
}
