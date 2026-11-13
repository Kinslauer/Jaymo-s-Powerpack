package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.JakiShips;
import com.jaymoskingdoms.sailing.Sailing;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.ShipPlans;
import com.jaymoskingdoms.sailing.Ships;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Hailing a ship at sea (Andy's ocean pick-list, "More ships, and ships that sail themselves": traders you can hail).
 * Blow a horn within {@link #HAIL_RANGE} of a merchantman or a fishing smack sailing on her own and her bell answers:
 * she heaves to and lies waiting while you're about her ({@link #NEAR}), a while after you've gone. Come alongside, go
 * aboard and talk with her captain about work, and he opens his hold to you: a merchantman sells stores, shot and sails
 * from her cargo, and the charted plans of a ship (one of Jaki's, {@link ShipPlans#charted}), and buys fish and whale
 * oil; a smack sells her catch and buys bread. Prices are in the coin of her realm. Her stock lasts the voyage.
 */
public final class SeaTrade {
	/** A horn is heard this far off (blocks). */
	static final double HAIL_RANGE = 96.0;
	/** She lies hove to this long (ticks) after the hail, and as long again while a player is this near her. */
	static final long HOVE_TICKS = 20L * 90L;
	static final double NEAR = 32.0;
	/** You trade with her captain from this near. */
	static final double TRADE_RANGE = 8.0;
	/** Uses before an offer runs out (it doesn't restock at sea). */
	static final int USES = 6, PLANS_USES = 1;
	/** What charted plans cost (emeralds): by the ship. */
	static final Map<JakiShips.Design, Integer> PLANS_PRICE = Map.of(JakiShips.Design.GALLEON, 24, JakiShips.Design.DRAKKAR, 14,
		JakiShips.Design.WAR_TRIREME, 22, JakiShips.Design.CARGO_SHIP, 16, JakiShips.Design.TRADER, 16);

	/** Ship → when she stops lying hove to (game time). */
	private static final Map<UUID, Long> HOVE = new HashMap<>();
	/** Ship → her stall, kept for her voyage (what's bought stays bought). */
	private static final Map<UUID, MerchantOffers> STOCK = new HashMap<>();

	private SeaTrade() {
	}

	static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(Items.GOAT_HORN) && player instanceof ServerPlayer sp && level instanceof ServerLevel server
				&& !player.getCooldowns().isOnCooldown(stack)) {
				hail(server, sp);
			}
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOVE.clear();
			STOCK.clear();
		});
	}

	/** {@code player} blew a horn: every merchantman and smack in earshot heaves to, her bell answering. */
	static void hail(ServerLevel level, ServerPlayer player) {
		long now = level.getGameTime();
		for (Ship ship : Ships.in(level)) {
			Crew crew = ship.crew;
			if (crew == null || !trades(crew) || ship.foundering() || ship.distanceToSqr(player) > HAIL_RANGE * HAIL_RANGE
				|| "run".equals(crew.mode)) {
				continue;
			}
			boolean already = HOVE.containsKey(ship.getUUID());
			HOVE.put(ship.getUUID(), now + HOVE_TICKS);
			if (!already) {
				level.playSound(null, ship.getX(), ship.getY() + 3.0, ship.getZ(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 2.0F, 1.1F);
			}
		}
	}

	/** She's one who trades at sea: a merchantman or a smack. */
	static boolean trades(Crew crew) {
		return crew.side == Crew.Side.MERCHANT || crew.side == Crew.Side.FISHER;
	}

	/** {@code ship} lies to until {@code until} (game time) as if hailed, and trades meanwhile (a blossom trader at a harbour). */
	static void lie(Ship ship, long until) {
		HOVE.put(ship.getUUID(), until);
	}

	/** {@code ship} gets under way again now, whoever is near her. */
	static void sail(Ship ship) {
		HOVE.remove(ship.getUUID());
	}

	/** Whether {@code ship} lies hove to for a hail now (kept so while a player is near her). */
	static boolean hoveTo(ServerLevel level, Ship ship) {
		Long until = HOVE.get(ship.getUUID());
		if (until == null) {
			return false;
		}
		long now = level.getGameTime();
		if (now < until) {
			return true;
		}
		if (level.getNearestPlayer(ship, NEAR) != null) {
			HOVE.put(ship.getUUID(), now + HOVE_TICKS / 3);
			return true;
		}
		HOVE.remove(ship.getUUID());
		return false;
	}

	/** Her captain, talked to about work while she lies hove to for a hail: true if he opened his hold to {@code player}. */
	static boolean talk(ServerLevel level, ServerPlayer player, LivingEntity captain, Ship ship) {
		if (ship.crew == null || !trades(ship.crew) || !HOVE.containsKey(ship.getUUID()) || !hoveTo(level, ship)) {
			return false;
		}
		MerchantOffers offers = STOCK.computeIfAbsent(ship.getUUID(), k -> stock(level, ship.crew, k));
		if (offers.isEmpty()) {
			return false;
		}
		Stall stall = new Stall(captain, offers);
		stall.setTradingPlayer(player);
		stall.openTradingScreen(player, captain.getDisplayName(), 1);
		return true;
	}

	/** Forget a ship's stall and hail (she's gone). */
	static void forget(Ship ship) {
		HOVE.remove(ship.getUUID());
		STOCK.remove(ship.getUUID());
	}

	// ---- her stall ----------------------------------------------------------------------------------------------------

	private static MerchantOffers stock(ServerLevel level, Crew crew, UUID ship) {
		MinecraftServer server = level.getServer();
		Currency coin = Currencies.of(server, Factions.get(server, crew.faction));
		RandomSource random = RandomSource.create(ship.getMostSignificantBits() ^ ship.getLeastSignificantBits());
		MerchantOffers offers = new MerchantOffers();
		Item tuna = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("jaymospowers", "tuna"));
		boolean hasTuna = tuna != Items.AIR;
		if (crew.side == Crew.Side.FISHER) {
			sell(offers, coin, 1, new ItemStack(Items.COD, 6));
			sell(offers, coin, 1, new ItemStack(Items.SALMON, 4));
			if (hasTuna) {
				sell(offers, coin, 2, new ItemStack(tuna));
			}
			sell(offers, coin, 1, new ItemStack(Items.KELP, 16));
			sell(offers, coin, 1, new ItemStack(Items.STRING, 4));
			buy(offers, coin, new ItemStack(Items.BREAD, 6), 1);
			return offers;
		}
		if (BlossomTraders.is(crew)) {
			// what the home continent has none of its own; the rest of her cargo comes with the blossom land's own goods
			sell(offers, coin, 1, new ItemStack(Items.PAPER, 12));
			sell(offers, coin, 1, new ItemStack(Items.BAMBOO, 16));
			sell(offers, coin, 2, new ItemStack(Items.CHERRY_SAPLING, 2));
			sell(offers, coin, 1, new ItemStack(Items.PINK_PETALS, 8));
			sell(offers, coin, 2, new ItemStack(Items.LANTERN, 2));
			sell(offers, coin, 3, new ItemStack(Items.IRON_INGOT, 6));
			buy(offers, coin, new ItemStack(Items.LEATHER, 6), 1);
			buy(offers, coin, new ItemStack(Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), 8), 1);
			buy(offers, coin, new ItemStack(com.jaymoskingdoms.whaling.Whaling.WHALE_OIL, 2), 3);
			return offers;
		}
		sell(offers, coin, 1, new ItemStack(Items.BREAD, 6));
		sell(offers, coin, 2, new ItemStack(Items.GUNPOWDER, 4));
		sell(offers, coin, 2, new ItemStack(Sailing.ROUND_SHOT, 4));
		sell(offers, coin, 2, new ItemStack(Sailing.OAR_ITEM));
		sell(offers, coin, 3, new ItemStack(Sailing.SAIL_ITEM, 2));
		Item[] cargo = {Items.PAPER, Items.LEATHER, Items.SUGAR, Items.COCOA_BEANS, Items.HONEYCOMB, Items.GLASS_BOTTLE};
		Item good = cargo[random.nextInt(cargo.length)];
		sell(offers, coin, 1, new ItemStack(good, good == Items.LEATHER ? 3 : 8));
		JakiShips.Design[] designs = JakiShips.Design.values();
		JakiShips.Design design = designs[random.nextInt(designs.length)];
		ItemStack plans = ShipPlans.charted(level, design);
		if (!plans.isEmpty()) {
			MerchantOffer offer = priced(coin, PLANS_PRICE.getOrDefault(design, 16), plans);
			if (offer != null) {
				offers.add(new MerchantOffer(offer.getItemCostA(), offer.getItemCostB(), plans, PLANS_USES, 0, 0.0F));
			}
		}
		buy(offers, coin, new ItemStack(Items.COD, 6), 1);
		buy(offers, coin, new ItemStack(Items.SALMON, 4), 1);
		if (hasTuna) {
			buy(offers, coin, new ItemStack(tuna), 2);
		}
		buy(offers, coin, new ItemStack(com.jaymoskingdoms.whaling.Whaling.WHALE_OIL, 2), 3);
		return offers;
	}

	private static void sell(MerchantOffers offers, Currency coin, int emeralds, ItemStack ware) {
		MerchantOffer offer = priced(coin, emeralds, ware);
		if (offer != null) {
			offers.add(offer);
		}
	}

	/** {@code ware} for {@code emeralds} of worth in {@code coin} (two stacks of it if one won't hold the price); null if two won't. */
	private static @Nullable MerchantOffer priced(Currency coin, int emeralds, ItemStack ware) {
		int n = Currencies.coinsFor(coin, emeralds);
		Item item = coin.item();
		if (n <= 64) {
			return new MerchantOffer(new ItemCost(item, n), ware, USES, 0, 0.0F);
		}
		if (n <= 128) {
			return new MerchantOffer(new ItemCost(item, 64), java.util.Optional.of(new ItemCost(item, n - 64)), ware, USES, 0, 0.0F);
		}
		return null;
	}

	private static void buy(MerchantOffers offers, Currency coin, ItemStack wanted, int emeralds) {
		int n = (int)Math.max(1L, Math.min(64L, emeralds * 1000L / coin.base));
		offers.add(new MerchantOffer(new ItemCost(wanted.getItem(), wanted.getCount()), new ItemStack(coin.item(), n), USES, 0, 0.0F));
	}

	/** A captain's hold, open to one player: what the trade screen deals with. */
	private static final class Stall implements Merchant {
		private final LivingEntity captain;
		private final MerchantOffers offers;
		private @Nullable Player trading;

		Stall(LivingEntity captain, MerchantOffers offers) {
			this.captain = captain;
			this.offers = offers;
		}

		@Override
		public void setTradingPlayer(@Nullable Player player) {
			this.trading = player;
		}

		@Override
		public @Nullable Player getTradingPlayer() {
			return this.trading;
		}

		@Override
		public MerchantOffers getOffers() {
			return this.offers;
		}

		@Override
		public void overrideOffers(MerchantOffers offers) {
		}

		@Override
		public void notifyTrade(MerchantOffer offer) {
			offer.increaseUses();
			Level level = this.captain.level();
			level.playSound(null, this.captain.getX(), this.captain.getY(), this.captain.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}

		@Override
		public void notifyTradeUpdated(ItemStack stack) {
		}

		@Override
		public int getVillagerXp() {
			return 0;
		}

		@Override
		public void overrideXp(int xp) {
		}

		@Override
		public boolean showProgressBar() {
			return false;
		}

		@Override
		public SoundEvent getNotifyTradeSound() {
			return SoundEvents.VILLAGER_YES;
		}

		@Override
		public boolean isClientSide() {
			return false;
		}

		@Override
		public boolean stillValid(Player player) {
			return this.captain.isAlive() && this.trading == player && player.distanceTo(this.captain) <= TRADE_RANGE;
		}
	}
}
