package com.jaymoskingdoms;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;

/**
 * (0.5.22) Treasuries that rise and fall. A kingdom starts with a purse of its own ({@link #START_MIN} to
 * {@link #START_MAX} emeralds), and it grows with trade and victory and shrinks with war:
 * <ul>
 * <li><b>Trade.</b> A player's trade with a villager on a realm's land pays the realm a toll ({@link #traded}), and a
 * fiefdom's caravan pays its kingdom market dues on what it brings ({@link #dues}).</li>
 * <li><b>War.</b> A war party is paid before it marches ({@link #wages}); a soldier who kills one of the enemy's folk
 * plunders its treasury for his own realm, more for a leader ({@link #plunder}). The loser of a war still pays its
 * tribute ({@link Wars}).</li>
 * </ul>
 * Daily taxes and the tyrant's tithe still come in ({@link Production}, {@link Tempers}).
 */
public final class Treasuries {
	/** A new kingdom's purse (emeralds, inclusive). */
	static final int START_MIN = 10, START_MAX = 700;
	/** A trade's toll: this share (percent) of the emeralds in it, and at least this much. */
	static final int TOLL_PERCENT = 25, TOLL_LEAST = 1;
	/** A caravan's market dues: one emerald for every this many goods delivered. */
	static final int DUES_PER = 32;
	/** A soldier's wage for one march (emeralds). */
	static final int WAGE = 2;
	/** Plunder for a fallen enemy, and for a fallen leader (emeralds, at most what its realm has). */
	static final int PLUNDER = 3, PLUNDER_LEADER = 40;

	private Treasuries() {
	}

	/** A new kingdom's purse. */
	static long start(RandomSource random) {
		return START_MIN + random.nextInt(START_MAX - START_MIN + 1);
	}

	/** {@code villager} has just made a trade with a player: the realm whose land it's on takes its toll. */
	public static void traded(AbstractVillager villager, MerchantOffer offer) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		Faction owner = Banners.owner(server, ChunkPos.containing(villager.blockPosition()));
		if (owner == null || owner.fallen() || !owner.type().isRealm()) {
			return;
		}
		int emeralds = emeralds(offer.getCostA()) + emeralds(offer.getCostB()) + emeralds(offer.getResult());
		long toll = Math.max(TOLL_LEAST, emeralds * TOLL_PERCENT / 100);
		Factions.update(server, owner.id(), f -> f.withTreasury(f.treasury() + toll));
	}

	private static int emeralds(ItemStack stack) {
		return stack.is(Items.EMERALD) ? stack.getCount() : stack.is(Items.EMERALD_BLOCK) ? stack.getCount() * 9 : 0;
	}

	/** A caravan has delivered {@code goods} to {@code lord}: its market dues. */
	static void dues(MinecraftServer server, Faction lord, int goods) {
		long dues = goods / DUES_PER;
		if (dues > 0) {
			Factions.update(server, lord.id(), f -> f.withTreasury(f.treasury() + dues));
		}
	}

	/** {@code from} pays a war party of {@code soldiers} for its march (as much as it has). */
	static void wages(MinecraftServer server, Faction from, int soldiers) {
		Factions.update(server, from.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - (long)soldiers * WAGE)));
	}

	/** {@code killer} has slain one of {@code victim}'s folk at war: plunder from the victim's realm to the killer's. */
	static void plunder(MinecraftServer server, Folk killer, Folk victim) {
		Faction loser = Factions.get(server, victim.factionId());
		Faction winner = Factions.get(server, killer.factionId());
		if (loser == null || winner == null || loser.id() == winner.id()) {
			return;
		}
		long taken = Math.min(loser.treasury(), victim.leader() ? PLUNDER_LEADER : PLUNDER);
		if (taken <= 0) {
			return;
		}
		Factions.update(server, loser.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - taken)));
		Factions.update(server, winner.id(), f -> f.withTreasury(f.treasury() + taken));
	}
}
