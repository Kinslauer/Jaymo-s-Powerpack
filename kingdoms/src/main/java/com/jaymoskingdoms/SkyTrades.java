package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * The sky folk's own trade: the <b>cloudwright</b> ({@link Professions#CLOUDWRIGHT}). Every sky village and sky town takes on a
 * couple of its idle people (Divine-born or halfway folk) as cloudwrights, who sell and buy what the Aether grows: aercloud
 * (cold, blue for bouncing, golden), skyroot boards, logs and saplings, and ambrosium shards and torches. The goods are Jaymo's
 * Powers' (found by id, so a missing item is simply not on the stall). Prices are in emeralds; the stall is restocked each day.
 */
public final class SkyTrades {
	/** How often a sky village's idle people are looked over (ticks), and how far round a player (blocks). */
	static final int EVERY = 400;
	static final double REACH = 48.0;
	/** Cloudwrights a place keeps (within {@link #SPACING} of each other). */
	static final int PER_PLACE = 2;
	static final double SPACING = 40.0;
	/** Uses of each offer before it runs out (restocked daily), the trader's experience, the price rise. */
	static final int USES = 12, XP = 2;
	static final float PRICE_MULT = 0.05F;

	/** The day each cloudwright's stall was last stocked. */
	private static final Map<UUID, Long> STOCKED = new HashMap<>();

	private SkyTrades() {
	}

	static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (McaCompat.LOADED && level.getGameTime() % EVERY == 0L) {
				look(level);
			}
		});
	}

	static boolean cloudwright(Villager v) {
		return v.getVillagerData().profession().value() == Professions.CLOUDWRIGHT;
	}

	private static @Nullable Item ours(String id) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("jaymospowers", id));
		return item == Items.AIR ? null : item;
	}

	private static void sell(List<MerchantOffer> out, int emeralds, String id, int count) {
		Item item = ours(id);
		if (item != null) {
			out.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(item, count), USES, XP, PRICE_MULT));
		}
	}

	private static void buy(List<MerchantOffer> out, String id, int count, int emeralds) {
		Item item = ours(id);
		if (item != null) {
			out.add(new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD, emeralds), USES, XP, PRICE_MULT));
		}
	}

	/** The cloudwright's stall: sky goods for emeralds, and emeralds for what the village has no end of. */
	static MerchantOffers wares() {
		List<MerchantOffer> list = new ArrayList<>();
		sell(list, 1, "cold_aercloud", 6);
		sell(list, 2, "blue_aercloud", 4);
		sell(list, 3, "golden_aercloud", 4);
		sell(list, 1, "skyroot_planks", 16);
		sell(list, 2, "skyroot_sapling", 2);
		sell(list, 1, "ambrosium_shard", 3);
		sell(list, 1, "ambrosium_torch", 4);
		buy(list, "skyroot_log", 12, 1);
		buy(list, "ambrosium_shard", 8, 1);
		buy(list, "golden_aercloud", 6, 2);
		MerchantOffers offers = new MerchantOffers();
		offers.addAll(list);
		return offers;
	}

	private static void look(ServerLevel level) {
		long day = level.getGameTime() / 24000L;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !SkyFolk.in(player)) {
				continue;
			}
			List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(player.blockPosition()).inflate(REACH), v -> v.isAlive() && !v.isBaby());
			List<Villager> wrights = new ArrayList<>();
			for (Villager v : folk) {
				if (cloudwright(v)) {
					wrights.add(v);
					if (STOCKED.getOrDefault(v.getUUID(), -1L) != day) {
						STOCKED.put(v.getUUID(), day);
						if (v.getOffers().isEmpty()) {
							v.setOffers(wares());
						} else {
							v.getOffers().forEach(MerchantOffer::resetUses);
						}
					}
				}
			}
			for (Villager v : folk) {
				if (!hirable(v)) {
					continue;
				}
				int near = 0;
				for (Villager w : wrights) {
					if (w.distanceToSqr(v) <= SPACING * SPACING) {
						near++;
					}
				}
				if (near >= PER_PLACE) {
					continue;
				}
				v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(Professions.CLOUDWRIGHT)));
				// (a little experience, so the game does not take the trade off them for want of a job site)
				v.setVillagerXp(Math.max(1, v.getVillagerXp()));
				v.setPersistenceRequired();
				v.setOffers(wares());
				STOCKED.put(v.getUUID(), day);
				wrights.add(v);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 5, 0.3, 0.2, 0.3, 0.0);
			}
		}
	}

	/** An idle grown villager of the sky (not the lord, a warden, a worker, a ruler or someone's family) who might take up the stall. */
	private static boolean hirable(Villager v) {
		return SkyFolk.in(v) && McaBridge.adult(v) && v.getVillagerData().profession().is(VillagerProfession.NONE)
			&& !(v instanceof Worker w && w.role() >= 0) && !Rulers.is(v) && !v.entityTags().contains(SkyCourt.LORD_TAG)
			&& !v.entityTags().contains(SkyCourt.WARDEN_TAG) && !McaCompat.playerFamily(v) && McaCompat.following(v) == null
			&& !v.hasAttached(Emigrants.MOVE);
	}
}
