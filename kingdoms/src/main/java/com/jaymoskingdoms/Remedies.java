package com.jaymoskingdoms;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.102) Medieval medicine, Kingdoms' side of Jaymo's Powers' Wounds phase 4 (Andy, 2026-10-20: "I dont want any
 * modern medicine"). <b>Clerics are barber-surgeons</b>: a grown cleric sells linen bandages, splints, smelling salts and
 * leeches (Powers') and strong spirits (Kitchen's) for emeralds, beside their usual wares; setting your bones for
 * emeralds is still Powers' ({@code Fractures}). <b>Taverns and festivals sell drink</b>: a grown villager standing in one
 * of a realm's taverns, or raising a toast at its festival, sells Kitchen's ale and strong spirits; once they've left,
 * they don't.
 * <p>
 * Kingdoms never compiles against Powers or Kitchen: their items are looked up by id each time, and a ware whose mod
 * isn't loaded is simply not sold. The wares are added when a player right-clicks the villager, just
 * before the trading screen opens, so no villager's trades are touched unless someone comes to trade.
 */
final class Remedies {
	/** One ware: its item's id, its price (emeralds), how many come for it, and how many times it's sold before a restock. */
	record Ware(String id, int emeralds, int count, int uses) {
	}

	/** (0.7.102) What a barber-surgeon (a cleric) sells. */
	static final List<Ware> SURGEON = List.of(
		new Ware("jaymospowers:linen_bandage", 2, 3, 12),
		new Ware("jaymospowers:splint", 3, 1, 8),
		new Ware("jaymospowers:smelling_salts", 6, 1, 4),
		new Ware("jaymospowers:leeches", 4, 1, 6),
		new Ware("jaymoskitchen:strong_spirits", 4, 1, 8));
	/** (0.7.102) What a tavern's keeper or a festival's toaster sells. */
	static final List<Ware> DRINK = List.of(
		new Ware("jaymoskitchen:ale", 1, 2, 16),
		new Ware("jaymoskitchen:strong_spirits", 3, 1, 8));
	/** The trading experience a cleric earns per sale (a tavern's keeper or a toaster, who may have no trade of their own, earns none). */
	static final int SURGEON_XP = 2, DRINK_XP = 0;
	/** How the price climbs with demand (the game's own trades use 0.05). */
	static final float PRICE_MULTIPLIER = 0.05F;
	/** How far outside a tavern's walls its keeper may stand (blocks): the doorstep counts. */
	static final double TAVERN_REACH = 1.0;

	private Remedies() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			// (only fills the shelves: the trading screen, Powers' bone-setting and everything else carry on)
			if (hand == InteractionHand.MAIN_HAND && !world.isClientSide() && world instanceof ServerLevel level && entity instanceof Villager v
				&& v.isAlive() && !v.isBaby() && !Downed.isDowned(v) && !player.isSpectator() && v.getTradingPlayer() == null) {
				// (not while someone else is at their trades: their screen would no longer match)
				stock(level, v);
			}
			return InteractionResult.PASS;
		});
	}

	/** {@code v}'s wares by what they are and where they stand: added the first time they should sell them, and kept after. */
	static void stock(ServerLevel level, Villager v) {
		boolean surgeon = v.getVillagerData().profession().is(VillagerProfession.CLERIC);
		boolean drink = Festivals.toasting(v) || inTavern(level, v);
		Map<String, Ware> wanted = new LinkedHashMap<>();
		if (drink) {
			for (Ware w : DRINK) {
				wanted.put(w.id(), w);
			}
		}
		if (surgeon) {
			// (a cleric's own price for the spirits)
			for (Ware w : SURGEON) {
				wanted.put(w.id(), w);
			}
		}
		Map<String, Ware> all = new LinkedHashMap<>();
		for (Ware w : DRINK) {
			all.put(w.id(), w);
		}
		for (Ware w : SURGEON) {
			all.put(w.id(), w);
		}
		MerchantOffers offers = v.getOffers();
		for (String id : all.keySet()) {
			Item item = item(id);
			if (item == null) {
				// (its mod isn't here)
				continue;
			}
			boolean sold = false;
			for (MerchantOffer offer : offers) {
				if (ours(offer, item)) {
					sold = true;
					break;
				}
			}
			Ware w = wanted.get(id);
			if (w != null && !sold) {
				offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, w.emeralds()), new ItemStack(item, w.count()), w.uses(),
					surgeon ? SURGEON_XP : DRINK_XP, PRICE_MULTIPLIER));
			}
			// (an offer, once made, stays: taking it away and adding it back would restock it for free)
		}
	}

	/** One of ours: {@code item} for emeralds. */
	private static boolean ours(MerchantOffer offer, Item item) {
		return offer.getResult().is(item) && offer.getCostA().is(Items.EMERALD);
	}

	/** The item by its id, or null if no mod here has it. */
	private static @Nullable Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
		return item == null || item == Items.AIR ? null : item;
	}

	/** {@code v} stands in (or at the door of) a standing tavern of the realm whose land it is. */
	static boolean inTavern(ServerLevel level, Villager v) {
		MinecraftServer server = level.getServer();
		if (level != Factions.level(server)) {
			return false;
		}
		Faction owner = Banners.owner(server, ChunkPos.containing(v.blockPosition()));
		if (owner == null || owner.fallen()) {
			return false;
		}
		for (Works.Built b : Works.of(server, owner.id()).built()) {
			if (b.entry().equals(Planner.TAVERN) && b.ruined() == 0
				&& new AABB(Vec3.atLowerCornerOf(b.min()), Vec3.atLowerCornerOf(b.max()).add(1, 1, 1)).inflate(TAVERN_REACH).contains(v.position())) {
				return true;
			}
		}
		return false;
	}
}
