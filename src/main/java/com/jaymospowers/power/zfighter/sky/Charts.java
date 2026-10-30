package com.jaymospowers.power.zfighter.sky;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterPodTrip;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.ZFighterSpaceMissions;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * (0.18.52, Andy's SevTech picks: "Reading the sky") Which star charts a player has read. A world is on the star map, and a
 * flight the pod will make, only once its chart is read. One number per player: bit {@code w} is the chart of the pod
 * destination {@code w} ({@link ZFighterSpace#MOON} to {@link ZFighterSpace#VERDANT}; Home needs none and the future is not
 * chartable), {@link #COUNTED} says the worlds a player had reached before charts existed were credited, and {@link #BENCH}
 * says the Star-Sight Bench gave its chart. Synced to its owner, so the sky and the star map can read it.
 */
public final class Charts {
	/** How many worlds have a chart: the pod's destinations from the Pale Moon to the Verdant World. */
	public static final int WORLDS = 6;
	public static final int ALL = (1 << WORLDS) - 1;
	/** Bits above the worlds: the first-load credit was done, and the bench has given its chart. */
	static final int COUNTED = 1 << 6, BENCH = 1 << 7;

	public static final AttachmentType<Integer> CHARTS = AttachmentRegistry.create(JaymosPowers.id("zfighter_sky_charts"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private Charts() {
	}

	public static int bits(Player player) {
		return player.getAttachedOrElse(CHARTS, 0);
	}

	/** The worlds {@code player} has read the charts of, as a bitmask. */
	public static int readMask(Player player) {
		return bits(player) & ALL;
	}

	public static boolean read(Player player, int world) {
		return world >= 0 && world < WORLDS && (bits(player) & 1 << world) != 0;
	}

	/** Marks {@code world} read; true if it was new. */
	public static boolean mark(ServerPlayer player, int world) {
		if (world < 0 || world >= WORLDS || read(player, world)) {
			return false;
		}
		player.setAttached(CHARTS, bits(player) | 1 << world);
		return true;
	}

	/** Whether the bench has given its chart to {@code player}. */
	static boolean benchGiven(ServerPlayer player) {
		return (bits(player) & BENCH) != 0;
	}

	static void benchDone(ServerPlayer player) {
		player.setAttached(CHARTS, bits(player) | BENCH);
	}

	/** The chart of {@code world} as an item. */
	public static ItemStack stack(int world) {
		ItemStack stack = new ItemStack(Sky.STAR_CHART);
		String key = ZFighterSpace.DESTS[world].key();
		stack.set(Sky.CHART_WORLD, key);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(key), List.of()));
		return stack;
	}

	/** Which world {@code stack} is the chart of, or -1. */
	public static int worldOf(ItemStack stack) {
		String key = stack.get(Sky.CHART_WORLD);
		if (key == null) {
			return -1;
		}
		for (int i = 0; i < WORLDS; i++) {
			if (ZFighterSpace.DESTS[i].key().equals(key)) {
				return i;
			}
		}
		return -1;
	}

	/** The worlds in the order the star map's costs rise (the Moon first, the Maw last): the order charts are sold in. */
	public static List<Integer> byCost() {
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < WORLDS; i++) {
			order.add(i);
		}
		order.sort((a, b) -> Integer.compare(ZFighterSpace.COSTS[a], ZFighterSpace.COSTS[b]));
		return order;
	}

	/** The first {@code count} worlds {@code player} hasn't read, cheapest trip first. */
	public static List<Integer> nextUnread(Player player, int count) {
		List<Integer> next = new ArrayList<>();
		for (int world : byCost()) {
			if (next.size() >= count) {
				break;
			}
			if (!read(player, world)) {
				next.add(world);
			}
		}
		return next;
	}

	/** The page of a chart, as the book shows it (lang {@code book.jaymospowers.star_chart.<world>.<n>}). */
	public static Component page(int world) {
		MutableComponent page = Component.empty();
		String key = ZFighterSpace.DESTS[world].key();
		for (int i = 1; i <= Sky.PAGE_LINES; i++) {
			if (i > 1) {
				page.append(Component.literal("\n\n"));
			}
			page.append(Component.translatable("book.jaymospowers.star_chart." + key + "." + i));
		}
		return page;
	}

	// ---------------------------------------------------------------- worlds reached before charts

	/**
	 * Once, when a player is first seen: the worlds he had already reached count as read, so nobody loses one. They are the one he
	 * stands in, the ones he has a mission in or has settled, and, if he has flown at all, the Pale Moon (the pod's first
	 * trips went there). From now on a landing marks its world read ({@link Sky}).
	 */
	static void credit(ServerPlayer player) {
		int bits = bits(player);
		if ((bits & COUNTED) != 0) {
			return;
		}
		int add = COUNTED;
		int here = indexHere(player);
		if (here >= 0) {
			add |= 1 << here;
		}
		ZFighterSpaceMissions.Data data = ZFighterSpaceMissions.of(player);
		for (int world = 0; world < WORLDS; world++) {
			int planet = ZFighterSpaceMissions.planetAt(world);
			if (planet >= 0 && (data.of(planet) != 0 || data.settled(planet))) {
				add |= 1 << world;
			}
		}
		if (player.getAttachedOrElse(ZFighterPodTrip.FLIGHTS, 0) > 0) {
			add |= 1 << ZFighterSpace.MOON;
		}
		player.setAttached(CHARTS, bits | add);
	}

	/** The chartable world {@code player} stands in, or -1. */
	static int indexHere(Player player) {
		for (int i = 0; i < WORLDS; i++) {
			if (ZFighterSpace.DESTS[i].level() == player.level().dimension()) {
				return i;
			}
		}
		return -1;
	}
}
