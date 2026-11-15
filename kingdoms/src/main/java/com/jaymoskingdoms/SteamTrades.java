package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The steam towns' trades ({@link SteamTowns}). Every steam town takes on some of its idle people:
 * <ul>
 * <li><b>tinkers</b> ({@link Professions#TINKER}), who build steam and sell its parts: Jaymo's Kitchen's steam cylinder,
 * safety valve, fireboxes, boilers, steam engines, shafts and gearboxes, windmill sails and the windsock;</li>
 * <li><b>engineers</b> ({@link Professions#ENGINEER}), who keep the coke yard and the boilers fed: Jaymo's Powers' coal
 * coke, creosote, treated planks, coke oven bricks, firebricks and tin;</li>
 * <li>an <b>aeronaut</b> ({@link Professions#AERONAUT}), the airship's captain, who sells passage ({@link SteamAirships})
 * and a few things for the sky.</li>
 * </ul>
 * The other mods' goods are found by id ({@link SteamGoods}), only when that mod is loaded, so a missing item is simply
 * not on the stall. Prices are in emeralds' worth, set in the town's own coin when the trade opens as every trader's are
 * ({@link Currencies#localize}); each stall is restocked each day.
 */
public final class SteamTrades {
	/** How often the towns' idle people are looked over (ticks), and how near a player a town must be (blocks). */
	static final int EVERY = 400;
	static final double PLAYER_NEAR = 96.0;
	/** How many of each trade a town keeps. */
	static final int TINKERS = 2, ENGINEERS = 2, AERONAUTS = 1;
	/** Uses of each offer before it runs out (restocked daily), the trader's experience, the price rise. */
	static final int USES = 8, XP = 2;
	static final float PRICE_MULT = 0.05F;

	/** A steam trade: its profession, how many a town keeps, its stall, and what it carries at work. */
	record Job(Supplier<VillagerProfession> profession, int perTown, Supplier<MerchantOffers> wares, Supplier<ItemStack> tool) {
		boolean is(Villager v) {
			return v.getVillagerData().profession().value() == this.profession.get();
		}
	}

	static final List<Job> JOBS = List.of(
		new Job(() -> Professions.AERONAUT, AERONAUTS, SteamTrades::aeronautWares, () -> new ItemStack(Items.SPYGLASS)),
		new Job(() -> Professions.TINKER, TINKERS, SteamTrades::tinkerWares, () -> new ItemStack(Items.IRON_INGOT)),
		new Job(() -> Professions.ENGINEER, ENGINEERS, SteamTrades::engineerWares, () -> new ItemStack(Items.COAL)));

	/** The day each trader's stall was last stocked. */
	private static final Map<UUID, Long> STOCKED = new HashMap<>();

	private SteamTrades() {
	}

	static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % EVERY == 0L && level == Factions.level(level.getServer())) {
				look(level);
			}
		});
	}

	/** {@code v} is a steam town's airship captain. */
	static boolean aeronaut(Villager v) {
		return v.getVillagerData().profession().value() == Professions.AERONAUT;
	}

	// ---- the stalls ----------------------------------------------------------------------------------------------------

	private static void sell(List<MerchantOffer> out, int emeralds, @Nullable Item item, int count) {
		if (item != null) {
			out.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(item, count), USES, XP, PRICE_MULT));
		}
	}

	private static void buy(List<MerchantOffer> out, @Nullable Item item, int count, int emeralds) {
		if (item != null) {
			out.add(new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD, emeralds), USES, XP, PRICE_MULT));
		}
	}

	private static MerchantOffers offers(List<MerchantOffer> list) {
		MerchantOffers offers = new MerchantOffers();
		offers.addAll(list);
		return offers;
	}

	/** The tinker's stall: steam parts (Kitchen's) and the windmill's sails, and copper and iron bought for the work. */
	static MerchantOffers tinkerWares() {
		String k = SteamGoods.KITCHEN;
		List<MerchantOffer> list = new ArrayList<>();
		sell(list, 6, SteamGoods.item(k, "steam_cylinder"), 1);
		sell(list, 5, SteamGoods.item(k, "safety_valve"), 1);
		sell(list, 8, SteamGoods.item(k, "firebox"), 1);
		sell(list, 10, SteamGoods.item(k, "copper_boiler"), 1);
		sell(list, 14, SteamGoods.item(k, "bronze_boiler"), 1);
		sell(list, 16, SteamGoods.item(k, "steam_engine"), 1);
		sell(list, 2, SteamGoods.item(k, "shaft"), 4);
		sell(list, 5, SteamGoods.item(k, "gearbox"), 1);
		sell(list, 4, SteamGoods.item(k, "cloth_sail"), 1);
		sell(list, 3, SteamGoods.item(k, "wool_sail"), 1);
		sell(list, 3, SteamGoods.item(k, "leather_sail"), 1);
		sell(list, 3, SteamGoods.item(k, "windsock"), 1);
		buy(list, Items.COPPER_INGOT, 8, 1);
		buy(list, Items.IRON_INGOT, 4, 1);
		if (list.size() <= 2) {
			// without Kitchen: the plain smith's side of the work
			sell(list, 1, Items.IRON_BARS, 8);
			sell(list, 2, Items.BUCKET, 1);
		}
		return offers(list);
	}

	/** The engineer's stall: what the coke yard gives (Powers'), tin, bricks for ovens and boilers, and fuel bought in. */
	static MerchantOffers engineerWares() {
		String p = SteamGoods.POWERS;
		List<MerchantOffer> list = new ArrayList<>();
		sell(list, 2, SteamGoods.item(p, "coal_coke"), 8);
		sell(list, 7, SteamGoods.item(p, "coal_coke_block"), 1);
		sell(list, 3, SteamGoods.item(p, "creosote_bucket"), 1);
		sell(list, 1, SteamGoods.item(p, "creosote_rag"), 4);
		sell(list, 2, SteamGoods.item(p, "treated_planks"), 8);
		sell(list, 2, SteamGoods.item(p, "coke_oven_brick"), 8);
		sell(list, 3, SteamGoods.item(p, "firebrick"), 8);
		sell(list, 3, SteamGoods.item(p, "tin_ingot"), 2);
		sell(list, 1, SteamGoods.item(p, "tin_nugget"), 6);
		buy(list, SteamGoods.item(p, "raw_tin"), 6, 1);
		buy(list, Items.COAL, 16, 1);
		buy(list, Items.CHARCOAL, 16, 1);
		buy(list, Items.OAK_LOG, 16, 1);
		sell(list, 1, Items.BRICK, 10);
		return offers(list);
	}

	/** The aeronaut's stall: what a captain keeps about him. His passage is sold from his deck, not here. */
	static MerchantOffers aeronautWares() {
		List<MerchantOffer> list = new ArrayList<>();
		sell(list, 6, Items.SPYGLASS, 1);
		sell(list, 3, Items.COMPASS, 1);
		sell(list, 2, Items.LEAD, 2);
		buy(list, Items.STRING, 16, 1);
		return offers(list);
	}

	// ---- taking people on ------------------------------------------------------------------------------------------------

	private static @Nullable Job job(Villager v) {
		for (Job job : JOBS) {
			if (job.is(v)) {
				return job;
			}
		}
		return null;
	}

	/** Every steam town a player is near: its traders restocked each day, and idle people taken on until it keeps its share of each trade. */
	private static void look(ServerLevel level) {
		long day = level.getGameTime() / 24000L;
		for (SteamTowns.Town town : SteamTowns.all(level)) {
			Faction f = Factions.get(level.getServer(), town.id());
			if (f == null || !level.isPositionEntityTicking(f.seat()) || !playerNear(level, f)) {
				continue;
			}
			List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(SteamTowns.TOWN_REACH), v -> v.isAlive() && !v.isBaby());
			Map<Job, Integer> working = new HashMap<>();
			for (Villager v : folk) {
				Job job = job(v);
				if (job == null) {
					continue;
				}
				if (job.profession().get() == Professions.AERONAUT) {
					// a captain is his own airship's: one here from another town's is not this town's
					if (!v.hasAttached(SteamAirships.CAPTAIN)) {
						v.setAttached(SteamAirships.CAPTAIN, town.id());
					}
					if (Integer.valueOf(town.id()).equals(v.getAttached(SteamAirships.CAPTAIN))) {
						working.merge(job, 1, Integer::sum);
					}
				} else {
					working.merge(job, 1, Integer::sum);
				}
				if (STOCKED.getOrDefault(v.getUUID(), -1L) != day) {
					STOCKED.put(v.getUUID(), day);
					if (v.getOffers().isEmpty()) {
						v.setOffers(job.wares().get());
					} else {
						v.getOffers().forEach(MerchantOffer::resetUses);
					}
				}
			}
			// while her airship is away her captain is aboard her: none taken on in his place
			boolean shipHome = SteamAirships.home(level, town);
			for (Villager v : folk) {
				if (!hirable(f, v)) {
					continue;
				}
				for (Job job : JOBS) {
					if (working.getOrDefault(job, 0) >= job.perTown() || job.profession().get() == Professions.AERONAUT && !shipHome) {
						continue;
					}
					v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(job.profession().get())));
					// (a little experience, so the game does not take the trade off them for want of a job site)
					v.setVillagerXp(Math.max(1, v.getVillagerXp()));
					v.setPersistenceRequired();
					v.setOffers(job.wares().get());
					if (job.profession().get() == Professions.AERONAUT) {
						v.setAttached(SteamAirships.CAPTAIN, town.id());
					}
					if (v.getMainHandItem().isEmpty()) {
						v.setItemSlot(EquipmentSlot.MAINHAND, job.tool().get());
					}
					STOCKED.put(v.getUUID(), day);
					working.merge(job, 1, Integer::sum);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 5, 0.3, 0.2, 0.3, 0.0);
					break;
				}
			}
		}
	}

	private static boolean playerNear(ServerLevel level, Faction f) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && Raids.horizontal(p.blockPosition(), f.seat()) <= PLAYER_NEAR * PLAYER_NEAR) {
				return true;
			}
		}
		return false;
	}

	/** An idle grown villager of {@code f} (not its elder, a worker, a ruler, a guard, a visitor, downed or someone's family) who might take up a stall. */
	private static boolean hirable(Faction f, Villager v) {
		boolean grown = McaCompat.LOADED ? McaCompat.adult(v) : !v.isBaby();
		return grown && v.getVillagerData().profession().is(VillagerProfession.NONE) && !(v instanceof Worker w && w.role() >= 0) && !Rulers.is(v)
			&& (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID())) && !Visitors.is(v) && !Downed.isDowned(v)
			&& !(McaCompat.LOADED && (McaCompat.isGuard(v) || McaCompat.playerFamily(v) || McaCompat.following(v) != null))
			&& !v.hasAttached(Emigrants.MOVE);
	}
}
