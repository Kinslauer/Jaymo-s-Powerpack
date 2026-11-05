package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;

/**
 * (0.7.490) Hordes patch 3, the market: a greenskin camp's merchants are two of its own folk (MCA villagers, orcs or goblins)
 * who keep a stall (the market square once it's built, else by the fire), tagged {@link #TAG}. Talk to one on the Interact
 * page ("Show me your wares", {@link InteractActions#WARES}) and the stall opens, like the havens' wares
 * ({@link HavenWares}) but priced in bent ingots ({@link BentIngots}):
 * <ul>
 * <li><b>They sell the camp's surplus</b> ({@link #goods}): what its store holds beyond {@link Good#keep} of each, in lots.
 * The price moves with the stock, dearer as it runs low and cheaper as it piles up ({@link #price}).</li>
 * <li><b>They buy what it lacks</b>: a good under its reserve is bought from the player for bent ingots, from the hoard.</li>
 * <li>The camp books the trade when the screen closes ({@link #watch}): what was sold leaves its store and the bent ingots
 * paid go into the chief's hoard; what was bought goes into the store and is paid out of the hoard.</li>
 * <li><b>Only those the camp spares may trade</b> ({@link #welcome}): half-orcs, those raised by goblins, friends of the
 * tribe and anyone in a goblin mask. Anyone else is turned away in broken English.</li>
 * </ul>
 * The tribe's pedlar ({@link Pedlars}) is booked the same way, to his tribe's seat. Needs MCA (the merchants are villagers).
 */
final class GreenMarket {
	// ================================================================================================================
	// Tuning
	/** Merchants a camp keeps, by tier. */
	static final int[] MERCHANTS = {1, 1, 2, 2};
	/** A merchant keeps this near his stall. */
	static final int STALL_RADIUS = 5;
	/** The most uses one offer has (a sale; a purchase). */
	static final int SELL_USES = 8, BUY_USES = 6;
	/** The price at the stock's target is the base; it falls to this share of it when the stock is deep and rises to this many times when it runs dry (percent). */
	static final int PRICE_FLOOR = 50, PRICE_CEILING = 250;
	/** A merchant pays this share of a good's price when he buys it (percent). */
	static final int BUY_SHARE = 70;
	// ================================================================================================================

	/** A merchant's tag, and (with the camp's id after it) the camp he trades for. */
	static final String TAG = "jaymoskingdoms.green_merchant", TAG_OF = "jaymoskingdoms.green_merchant_of.";

	/** One good in a camp's market: a lot of {@code lot} at {@code base} worth when its store holds {@code target}, kept back to {@code keep}. */
	record Good(Item item, int lot, int base, int keep, int target) {
	}

	private static final List<Good> ORC_GOODS = List.of(
		new Good(Items.DARK_OAK_LOG, 8, 3, 24, 48), new Good(Items.COOKED_BEEF, 4, 3, 24, 40), new Good(Items.LEATHER, 2, 4, 8, 16),
		new Good(Items.IRON_NUGGET, 9, 4, 12, 36), new Good(Items.ARROW, 16, 3, 32, 64), new Good(Items.WOOL.pick(DyeColor.BLACK), 4, 3, 16, 32),
		new Good(Items.BONE, 8, 2, 8, 24), new Good(Items.SWEET_BERRIES, 8, 3, 16, 32));
	private static final List<Good> GOBLIN_GOODS = List.of(
		new Good(Items.SPRUCE_LOG, 8, 3, 24, 48), new Good(Items.MUD_BRICKS, 8, 3, 16, 40), new Good(Items.BROWN_MUSHROOM, 6, 2, 16, 36),
		new Good(Items.RED_MUSHROOM, 6, 2, 12, 30), new Good(Items.GOLD_NUGGET, 6, 5, 8, 24), new Good(Items.BREAD, 4, 3, 8, 16),
		new Good(Items.ARROW, 16, 3, 24, 48), new Good(Items.BONE, 8, 2, 8, 24), new Good(Items.COOKED_CHICKEN, 4, 3, 24, 40));

	/**
	 * An open trade: whose stall, and each offer's uses when the screen opened. ((0.7.490) {@code merchant} is kept so the trade
	 * can still be booked from his offers if he dies or unloads before it closes, or the server stops.)
	 */
	private record Session(Villager merchant, int camp, boolean stocked, List<Integer> uses) {
	}

	/** Merchant (or pedlar) id → the trade open with him. */
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private GreenMarket() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0 && !SESSIONS.isEmpty()) {
				settleAll(server);
			}
		});
		// ((0.7.490) every trade still open is booked while the levels are still up, before the world saves)
		ServerLifecycleEvents.SERVER_STOPPING.register(GreenMarket::settleRemaining);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
	}

	static List<Good> goods(boolean orc) {
		return orc ? ORC_GOODS : GOBLIN_GOODS;
	}

	// ---- prices ----------------------------------------------------------------------------------------------------------

	/** What a lot of {@code g} costs with {@code stock} in the store, in bent worth: dearer as the stock runs low, cheaper as it piles up. */
	static int price(Good g, int stock) {
		int percent = Math.max(PRICE_FLOOR, Math.min(PRICE_CEILING, 200 * g.target() / (stock + g.target())));
		return Math.max(1, (g.base() * percent + 50) / 100);
	}

	/** What a lot of {@code g} fetches from the merchant when he buys it. */
	static int bounty(Good g, int stock) {
		return Math.max(1, price(g, stock) * BUY_SHARE / 100);
	}

	// ---- the merchants -----------------------------------------------------------------------------------------------------

	static boolean merchant(Entity e) {
		return e.entityTags().contains(TAG);
	}

	/** The camp {@code e}, a merchant, trades for (or -1). */
	static int camp(Entity e) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(TAG_OF)) {
				try {
					return Integer.parseInt(tag.substring(TAG_OF.length()));
				} catch (NumberFormatException ignored) {
					return -1;
				}
			}
		}
		return -1;
	}

	/** Where the camp's stall is: its market square once that's built, else by the fire. */
	static BlockPos stall(MinecraftServer server, Faction camp, Camps.CampData data) {
		Hordes.Horde h = Hordes.horde(server, camp.id());
		for (int t = 2; t <= h.tier(); t++) {
			List<IllagerBuildings.Spot> plan = Hordes.plan(server, camp.id(), data, t);
			int built = t < h.tier() ? plan.size() : Math.min(h.built(), plan.size());
			for (int i = 0; i < built; i++) {
				if (plan.get(i).id().endsWith("market_square")) {
					return plan.get(i).placer(data.center(), data.gate()).at(0, 0, 0);
				}
			}
		}
		return data.center().offset(3, 0, 2);
	}

	/** Every so often while a camp is loaded: it keeps its merchants (a villager of its own takes up the stall when one is missing). */
	static void tick(ServerLevel level, Faction camp, Camps.CampData data) {
		if (!McaCompat.LOADED || camp.fallen()) {
			return;
		}
		MinecraftServer server = level.getServer();
		BlockPos stall = stall(server, camp, data);
		int want = MERCHANTS[Hordes.tier(server, camp.id()) - 1];
		AABB box = new AABB(stall).inflate(Hordes.radius(server, camp.id()) + 8.0);
		List<Villager> have = level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && v.entityTags().contains(TAG_OF + camp.id()));
		for (Villager v : have) {
			// ((0.7.490) every pass, not on a tickCount gate: this runs every Camps.CHECK_TICKS, so a gate would skip most merchants forever)
			if (v.getTradingPlayer() == null) {
				v.setHomeTo(stall, STALL_RADIUS);
			}
		}
		if (have.size() >= want) {
			return;
		}
		boolean orc = data.orc();
		int role = orc ? Orcs.GRUNT : Goblins.GOBLIN;
		Worker pick = null;
		double best = Double.MAX_VALUE;
		for (Worker w : Workers.near(level, box, x -> x.factionId() == camp.id() && x.role() == role && x.mob() instanceof Villager && x.mob().isAlive()
			&& !merchant(x.mob()) && !Downed.isDowned(x.mob()) && x.mob().getTarget() == null)) {
			double d = w.mob().distanceToSqr(stall.getX() + 0.5, stall.getY(), stall.getZ() + 0.5);
			if (d < best) {
				best = d;
				pick = w;
			}
		}
		if (pick != null) {
			pick.mob().addTag(TAG);
			pick.mob().addTag(TAG_OF + camp.id());
			pick.mob().setHomeTo(stall, STALL_RADIUS);
			JaymosKingdoms.LOGGER.debug("{} has a new merchant at {}", camp.name(), stall);
		}
	}

	// ---- who may trade -----------------------------------------------------------------------------------------------------

	/** The camp spares {@code player} at its stalls: a half-orc, one raised by goblins, a friend of its tribe, or one in a goblin mask. */
	static boolean welcome(ServerPlayer player, Faction camp) {
		MinecraftServer server = player.level().getServer();
		if (player.isCreative() || GreenskinLife.halfOrc(player) || GreenskinLife.raised(player) || Disguise.fooled(player, camp.id())) {
			return true;
		}
		String tribe = Tribes.of(server, camp.id());
		return tribe != null && Tribes.grudge(server, player.getUUID(), tribe) <= TribePacts.FRIEND_AT;
	}

	/** (From the Interact page) {@code player} asks {@code merchant} for his wares: the stall opens if the camp spares them. */
	static void trade(ServerPlayer player, PathfinderMob merchant) {
		ServerLevel level = (ServerLevel)player.level();
		MinecraftServer server = level.getServer();
		Faction camp = Factions.get(server, camp(merchant));
		Camps.CampData data = camp == null ? null : Camps.data(server, camp.id());
		if (camp == null || data == null || !(merchant instanceof Villager v) || v.getTradingPlayer() != null) {
			return;
		}
		String brood = data.orc() ? "orc" : "goblin";
		if (!welcome(player, camp)) {
			Speech.tell(level, merchant, player, Component.translatable("talk.jaymoskingdoms.green.market.no." + brood + "." + merchant.getRandom().nextInt(3)), Speech.RED);
			return;
		}
		if (BentIngots.plainHeld(player) > 0 && BentIngots.held(player) == 0) {
			// (all they carry is straightened: refused, and the stall opens all the same)
			BentIngots.refuse(level, merchant, player);
		}
		// ((0.7.490) a trade still waiting to be booked is booked now, before the stall is laid out afresh)
		settleNow(v);
		stock(level, camp, data, v);
		if (v.getOffers().isEmpty()) {
			Speech.tell(level, merchant, player, Component.translatable("talk.jaymoskingdoms.green.market.empty." + brood + "." + merchant.getRandom().nextInt(3)), Speech.GOLD);
			return;
		}
		watch(v, camp.id(), true);
		v.setTradingPlayer(player);
		v.openTradingScreen(player, v.getDisplayName(), 1);
	}

	/** The stall laid out afresh from the camp's store and hoard. */
	private static void stock(ServerLevel level, Faction camp, Camps.CampData data, Villager v) {
		MerchantOffers offers = v.getOffers();
		offers.clear();
		if (!Storage.loaded(level, camp)) {
			return;
		}
		long hoard = HordeEconomy.hoard(level.getServer(), camp.id());
		for (Good g : goods(data.orc())) {
			int stock = Storage.count(level, camp, g.item());
			int price = price(g, stock);
			int spare = (stock - g.keep()) / g.lot();
			if (spare > 0) {
				offers.add(BentIngots.offer(price, new ItemStack(g.item(), g.lot()), Math.min(SELL_USES, spare), 0, 0.0F));
			}
			int pay = BentIngots.payable(bounty(g, stock)); // (0.7.490) what the one stack of coin really pays, so the hoard is charged no more or less than that
			int short_ = (g.keep() - stock + g.lot() - 1) / g.lot();
			// ((0.7.490) every buy offer draws on one budget, so all of them together never pay out more than the hoard holds)
			long afford = hoard / pay;
			if (short_ > 0 && afford > 0) {
				int uses = (int)Math.min(Math.min(BUY_USES, short_), afford);
				hoard -= (long)uses * pay;
				offers.add(new MerchantOffer(new ItemCost(g.item(), g.lot()), BentIngots.coin(pay), uses, 0, 0.0F));
			}
		}
	}

	// ---- booking a trade ---------------------------------------------------------------------------------------------------

	/**
	 * A trade is about to open with {@code v}: when it closes, what was sold or bought is booked with camp {@code camp}.
	 * {@code stocked}: the wares are the camp's store's (a pedlar's are not), so goods move in and out of it too.
	 */
	static void watch(Villager v, int camp, boolean stocked) {
		settleNow(v);
		List<Integer> uses = new ArrayList<>();
		for (MerchantOffer o : v.getOffers()) {
			uses.add(o.getUses());
		}
		SESSIONS.put(v.getUUID(), new Session(v, camp, stocked, uses));
	}

	/** ((0.7.490)) {@code v}'s last trade, if it's still waiting for settleAll, booked now so a new one can't overwrite it. */
	private static void settleNow(Villager v) {
		Session s = SESSIONS.remove(v.getUUID());
		if (s != null && v.level() instanceof ServerLevel level) {
			settle(level, v, s);
		}
	}

	private static void settleAll(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		for (var it = SESSIONS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			if (!(level.getEntity(e.getKey()) instanceof Villager v)) {
				// ((0.7.490) he died or his chunk unloaded: the trade is booked from the offers he left, not dropped)
				it.remove();
				settle(level, e.getValue().merchant(), e.getValue());
				continue;
			}
			if (v.getTradingPlayer() == null) {
				settle(level, v, e.getValue());
				it.remove();
			}
		}
	}

	/** ((0.7.490)) Every trade still open, booked and dropped (the server is stopping). */
	private static void settleRemaining(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		List<Session> open = new ArrayList<>(SESSIONS.values());
		SESSIONS.clear();
		for (Session s : open) {
			if (level != null) {
				settle(level, s.merchant(), s);
			}
		}
	}

	/** What changed hands in {@code v}'s trade, booked: sales into the hoard (and out of the store), purchases out of the hoard (and into the store). */
	private static void settle(ServerLevel level, Villager v, Session s) {
		MinecraftServer server = level.getServer();
		Faction camp = Factions.get(server, s.camp());
		if (camp == null) {
			if (s.stocked()) {
				v.getOffers().clear();
			}
			return;
		}
		boolean loaded = s.stocked() && Storage.loaded(level, camp);
		MerchantOffers offers = v.getOffers();
		for (int i = 0; i < offers.size() && i < s.uses().size(); i++) {
			MerchantOffer o = offers.get(i);
			int times = o.getUses() - s.uses().get(i);
			if (times <= 0) {
				continue;
			}
			ItemStack cost = o.getCostA(), result = o.getResult();
			if (BentIngots.bent(cost)) {
				HordeEconomy.deposit(server, camp.id(), (long)BentIngots.total(cost) * times);
				if (loaded) {
					Storage.take(level, camp, result.getItem(), result.getCount() * times);
				}
			} else if (BentIngots.bent(result)) {
				HordeEconomy.spend(server, camp.id(), Math.min(HordeEconomy.hoard(server, camp.id()), (long)BentIngots.total(result) * times));
				if (loaded) {
					Storage.put(level, camp, cost.getItem(), cost.getCount() * times);
				}
			}
		}
		if (s.stocked()) {
			// ((0.7.490) nothing priced is left on him between stall visits, so no other path can open an unbooked trade)
			offers.clear();
		}
	}
}
