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
 * The sky folk's own trades. Every sky village and sky town takes on some of its idle people (Divine-born or halfway folk):
 * <ul>
 * <li>two <b>cloudwrights</b> ({@link Professions#CLOUDWRIGHT}), who sell and buy what the Aether grows: aercloud (cold,
 * blue for bouncing, golden), skyroot boards, logs and saplings, and ambrosium shards and torches;</li>
 * <li>a <b>sky whaler</b> ({@link Professions#SKY_WHALER}), who hunts the aerwhales with a harpoon and sells what they give:
 * meat, blubber, oil, bone and now and then ambergris, and harpoons;</li>
 * <li>two <b>ambrosia farmers</b> ({@link Professions#AMBROSIA_FARMER}), who keep the sky orchards with a hoe and sell their
 * fruit: white apples, blue berries, oranges and wyndberries, and the enchanted ones.</li>
 * </ul>
 * (Andy, 2026-11-12: "They need their own job, like sky whaling or ambrosia farming".) The goods are Jaymo's Powers' and
 * our whaling's (found by id, so a missing item is simply not on the stall). Prices are in emeralds; each stall is
 * restocked each day.
 */
public final class SkyTrades {
	/** How often a sky village's idle people are looked over (ticks), and how far round a player (blocks). */
	static final int EVERY = 400;
	static final double REACH = 48.0;
	/** How many of each trade a place keeps (within {@link #SPACING} of each other). */
	static final int CLOUDWRIGHTS = 2, WHALERS = 1, FARMERS = 2;
	static final double SPACING = 40.0;

	/** A sky trade: its profession, how many a place keeps, its stall, and what it carries at work. */
	record Job(java.util.function.Supplier<net.minecraft.world.entity.npc.villager.VillagerProfession> profession, int perPlace,
		java.util.function.Supplier<MerchantOffers> wares, java.util.function.Supplier<ItemStack> tool) {
		boolean is(Villager v) {
			return v.getVillagerData().profession().value() == profession.get();
		}
	}

	static final List<Job> JOBS = List.of(
		new Job(() -> Professions.CLOUDWRIGHT, CLOUDWRIGHTS, SkyTrades::wares, () -> ItemStack.EMPTY),
		new Job(() -> Professions.SKY_WHALER, WHALERS, SkyTrades::whalerWares, SkyTrades::harpoon),
		new Job(() -> Professions.AMBROSIA_FARMER, FARMERS, SkyTrades::farmerWares, SkyTrades::hoe));
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
		Item item = BuiltInRegistries.ITEM.getValue(id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath("jaymospowers", id));
		return item == Items.AIR ? null : item;
	}

	private static ItemStack harpoon() {
		Item item = ours("jaymoskingdoms:harpoon");
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	/** A skyroot hoe (Powers'), or a wooden one without it. */
	private static ItemStack hoe() {
		Item item = ours("skyroot_hoe");
		return new ItemStack(item == null ? Items.WOODEN_HOE : item);
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

	/** The sky whaler's stall: what an aerwhale gives, and the harpoons to take one. */
	static MerchantOffers whalerWares() {
		List<MerchantOffer> list = new ArrayList<>();
		sell(list, 1, "jaymoskingdoms:whale_meat", 4);
		sell(list, 1, "jaymoskingdoms:whale_blubber", 3);
		sell(list, 2, "jaymoskingdoms:whale_oil", 2);
		sell(list, 1, "jaymoskingdoms:whale_bone", 4);
		sell(list, 3, "jaymoskingdoms:harpoon", 2);
		sell(list, 12, "jaymoskingdoms:ambergris", 1);
		buy(list, "jaymoskingdoms:whale_meat", 8, 1);
		buy(list, "jaymoskingdoms:whale_blubber", 6, 1);
		MerchantOffers offers = new MerchantOffers();
		offers.addAll(list);
		return offers;
	}

	/** The ambrosia farmer's stall: the sky orchards' fruit, plain and enchanted. */
	static MerchantOffers farmerWares() {
		List<MerchantOffer> list = new ArrayList<>();
		sell(list, 1, "white_apple", 4);
		sell(list, 1, "blue_berry", 8);
		sell(list, 1, "orange", 4);
		sell(list, 1, "wyndberry", 6);
		sell(list, 3, "enchanted_blue_berry", 1);
		sell(list, 4, "enchanted_orange", 1);
		sell(list, 4, "enchanted_wyndberry", 1);
		buy(list, "blue_berry", 16, 1);
		buy(list, "orange", 8, 1);
		MerchantOffers offers = new MerchantOffers();
		offers.addAll(list);
		return offers;
	}

	private static @Nullable Job job(Villager v) {
		for (Job job : JOBS) {
			if (job.is(v)) {
				return job;
			}
		}
		return null;
	}

	private static void look(ServerLevel level) {
		long day = level.getGameTime() / 24000L;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !SkyFolk.in(player)) {
				continue;
			}
			List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(player.blockPosition()).inflate(REACH), v -> v.isAlive() && !v.isBaby());
			Map<Job, List<Villager>> working = new HashMap<>();
			for (Villager v : folk) {
				Job job = job(v);
				if (job == null) {
					continue;
				}
				working.computeIfAbsent(job, j -> new ArrayList<>()).add(v);
				if (STOCKED.getOrDefault(v.getUUID(), -1L) != day) {
					STOCKED.put(v.getUUID(), day);
					if (v.getOffers().isEmpty()) {
						v.setOffers(job.wares().get());
					} else {
						v.getOffers().forEach(MerchantOffer::resetUses);
					}
				}
			}
			for (Villager v : folk) {
				if (!hirable(v)) {
					continue;
				}
				for (Job job : JOBS) {
					List<Villager> those = working.computeIfAbsent(job, j -> new ArrayList<>());
					int near = 0;
					for (Villager w : those) {
						if (w.distanceToSqr(v) <= SPACING * SPACING) {
							near++;
						}
					}
					if (near >= job.perPlace()) {
						continue;
					}
					v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(job.profession().get())));
					// (a little experience, so the game does not take the trade off them for want of a job site)
					v.setVillagerXp(Math.max(1, v.getVillagerXp()));
					v.setPersistenceRequired();
					v.setOffers(job.wares().get());
					ItemStack tool = job.tool().get();
					if (!tool.isEmpty() && v.getMainHandItem().isEmpty()) {
						v.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, tool);
					}
					STOCKED.put(v.getUUID(), day);
					those.add(v);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 5, 0.3, 0.2, 0.3, 0.0);
					break;
				}
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
