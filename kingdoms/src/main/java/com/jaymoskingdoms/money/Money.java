package com.jaymoskingdoms.money;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;
import com.jaymoskingdoms.Currencies;
import com.jaymoskingdoms.Currency;
import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;

/**
 * (0.7.498) The kingdoms' money over {@link Currencies}. Every price is in milli (thousandths of an emerald, the unit
 * every settlement's treasury is kept in): {@link #UNIT} is what an emerald was worth, and an old price of "N emeralds"
 * is {@code worth(N)}. Server side, and aware of everywhere a player keeps coins: loose coins in the inventory, coin
 * purses in it, coins in bundles, and the purse worn in Jaymo's Powers' belt slot. Emeralds are a gem now and never pay.
 *
 * <p>What a payer takes: the settlement's own coin at par, copper bits at par, and any other realm's coin at the going
 * rate less {@link Currencies#FOREIGN_CUT} (switch {@link Currencies#FOREIGN_TAKEN}). Order of spending: the local coin, bits, then foreign
 * coins smallest first; within a coin, loose ones, the worn purse, the pack's purses, bundles. If the last coin is too big,
 * the smallest that covers the rest is broken and the change ({@link Currencies#pay} in the local coin, with bits) goes
 * back into the place it came from, else the pack, else the ground.
 *
 * <p>Which coin: given a faction, {@link Currencies#of}; given none, the coin of the land around the player or the place
 * ({@link Currencies#at}); and when that is emeralds, silver.
 */
public final class Money {
	/** (0.7.498) A milli price of one emerald's worth: what "1" in an old price meant. */
	public static final int UNIT = 1000;
	/** The most coins one stack of loose coins holds. */
	private static final int STACK = 64;

	/** The running server, for the faction-only overloads (set while a world is open). */
	private static @Nullable MinecraftServer server;

	private Money() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
	}

	/** {@code n} emeralds' worth (what an old price of "N emeralds" is now), in milli. */
	public static long worth(long n) {
		return n * UNIT;
	}

	// ---- which coin ------------------------------------------------------------------------------------------------------

	/** Emeralds and nothing are not a coin to pay in: those become silver. */
	private static Currency fix(@Nullable Currency c) {
		return c == null || c == Currency.EMERALD ? Currency.SILVER : c;
	}

	private static Currency local(@Nullable MinecraftServer server, @Nullable Faction f) {
		return server == null || f == null ? Currency.SILVER : fix(Currencies.of(server, f));
	}

	private static Currency local(ServerLevel level, BlockPos pos) {
		return fix(Currencies.at(level, pos));
	}

	private static Currency local(ServerPlayer player) {
		return local(player.level(), player.blockPosition());
	}

	private static Currency local(ServerPlayer player, @Nullable Faction f) {
		return f == null ? local(player) : local(player.level().getServer(), f);
	}

	/** One {@code c} coin's worth to a payer whose own coin is {@code local}, in milli (0: not taken). */
	private static long each(@Nullable MinecraftServer server, Currency local, Currency c) {
		if (c == local || c == Currency.BIT) {
			return c.base;
		}
		if (!Currencies.FOREIGN_TAKEN || server == null || !c.coin()) {
			return 0L;
		}
		return (long)(Currencies.rate(server, c) * (1.0 - Currencies.FOREIGN_CUT));
	}

	/** The coins a payer in {@code local} spends, in order: the local coin, bits, then the others smallest first. */
	private static List<Currency> order(@Nullable MinecraftServer server, Currency local) {
		List<Currency> out = new ArrayList<>();
		out.add(local);
		if (local != Currency.BIT) {
			out.add(Currency.BIT);
		}
		List<Currency> foreign = new ArrayList<>();
		for (Currency c : Currency.values()) {
			if (c.money() && !out.contains(c) && each(server, local, c) > 0L) {
				foreign.add(c);
			}
		}
		foreign.sort((a, b) -> Long.compare(each(server, local, a), each(server, local, b)));
		out.addAll(foreign);
		return out;
	}

	// ---- counting --------------------------------------------------------------------------------------------------------

	/** Everything {@code player} carries that a purse can hold, at standard worth, in milli (for display). */
	public static long count(ServerPlayer player) {
		List<Source> sources = sources(player);
		long total = 0L;
		for (Currency c : Currency.values()) {
			if (c.money()) {
				total += have(sources, c) * c.base;
			}
		}
		return total;
	}

	/** What {@code f} takes of what {@code player} carries, in milli (see the class comment). */
	public static long count(ServerPlayer player, @Nullable Faction f) {
		return count(player, local(player, f));
	}

	private static long count(ServerPlayer player, Currency local) {
		MinecraftServer server = player.level().getServer();
		List<Source> sources = sources(player);
		long total = 0L;
		for (Currency c : order(server, local)) {
			total += have(sources, c) * each(server, local, c);
		}
		return total;
	}

	/** Whether {@code player} can pay {@code milli} where they stand (a creative player always can). */
	public static boolean canPay(ServerPlayer player, long milli) {
		return milli <= 0L || player.hasInfiniteMaterials() || count(player, local(player)) >= milli;
	}

	/** Whether {@code player} can pay {@code f} {@code milli} (a creative player always can). */
	public static boolean canPay(ServerPlayer player, @Nullable Faction f, long milli) {
		return milli <= 0L || player.hasInfiniteMaterials() || count(player, local(player, f)) >= milli;
	}

	// ---- paying ----------------------------------------------------------------------------------------------------------

	/** Takes {@code milli} from {@code player} where they stand, all or nothing; true if it was paid (a creative player pays nothing). */
	public static boolean charge(ServerPlayer player, long milli) {
		return charge(player, local(player), milli);
	}

	/** Takes {@code milli} from {@code player} for {@code f}, all or nothing; true if it was paid (a creative player pays nothing). */
	public static boolean charge(ServerPlayer player, @Nullable Faction f, long milli) {
		return charge(player, local(player, f), milli);
	}

	private static boolean charge(ServerPlayer player, Currency local, long milli) {
		if (milli <= 0L || player.hasInfiniteMaterials()) {
			return true;
		}
		MinecraftServer server = player.level().getServer();
		List<Source> sources = sources(player);
		List<Currency> order = order(server, local);
		long total = 0L;
		for (Currency c : order) {
			total += have(sources, c) * each(server, local, c);
		}
		if (total < milli) {
			return false;
		}
		long need = milli;
		// whole coins that fit, the local coin first, so no coin is broken while smaller ones can pay
		for (Currency c : order) {
			long each = each(server, local, c);
			if (each <= 0L || need < each) {
				continue;
			}
			int n = take(sources, c, (int)Math.min(need / each, Integer.MAX_VALUE));
			need -= n * each;
		}
		// still short of a whole coin: break the smallest coin that covers it
		Source broke = null;
		long change = 0L;
		if (need > 0L) {
			Currency pick = null;
			long pickEach = Long.MAX_VALUE;
			for (Currency c : order) {
				long each = each(server, local, c);
				if (each >= need && each < pickEach && have(sources, c) > 0L) {
					pick = c;
					pickEach = each;
				}
			}
			if (pick != null) {
				for (Source s : sources) {
					if (s.count(pick) > 0) {
						s.take(pick, 1);
						broke = s;
						break;
					}
				}
				change = pickEach - need;
				need = 0L;
			}
		}
		if (need > 0L) {
			JaymosKingdoms.LOGGER.warn("Money.charge: {} milli short after counting enough for {}", need, milli);
		}
		// the change goes back where the broken coin came from; what doesn't fit there goes to the pack
		Currency changeCoin = local;
		int coinsLeft = 0, bitsLeft = 0;
		if (change > 0L) {
			Currencies.Pay back = Currencies.pay(local, change);
			changeCoin = back.coin();
			coinsLeft = (int)Math.min(Integer.MAX_VALUE, back.coins());
			bitsLeft = (int)Math.min(Integer.MAX_VALUE, back.bits());
			if (broke != null) {
				coinsLeft = broke.add(changeCoin, coinsLeft);
				bitsLeft = broke.add(Currency.BIT, bitsLeft);
			}
		}
		for (Source s : sources) {
			s.commit(player);
		}
		giveCoins(player, changeCoin, coinsLeft);
		giveCoins(player, Currency.BIT, bitsLeft);
		return true;
	}

	/** Takes up to {@code milli} from {@code player} where they stand; how much was taken. */
	public static long chargeUpTo(ServerPlayer player, long milli) {
		return chargeUpTo(player, local(player), milli);
	}

	/** Takes up to {@code milli} from {@code player} for {@code f}; how much was taken. */
	public static long chargeUpTo(ServerPlayer player, @Nullable Faction f, long milli) {
		return chargeUpTo(player, local(player, f), milli);
	}

	private static long chargeUpTo(ServerPlayer player, Currency local, long milli) {
		if (milli <= 0L) {
			return 0L;
		}
		if (player.hasInfiniteMaterials()) {
			return milli;
		}
		long take = Math.min(milli, count(player, local));
		return take > 0L && charge(player, local, take) ? take : 0L;
	}

	/** Takes exactly {@code n} of {@code c} from {@code player} (loose, purses and bundles), all or nothing; true if it was taken. */
	public static boolean chargeCoin(ServerPlayer player, Currency c, long n) {
		if (n <= 0L || player.hasInfiniteMaterials()) {
			return true;
		}
		if (n > Integer.MAX_VALUE) {
			return false;
		}
		List<Source> sources = sources(player);
		if (have(sources, c) < n) {
			return false;
		}
		take(sources, c, (int)n);
		for (Source s : sources) {
			s.commit(player);
		}
		return true;
	}

	/** Gives {@code player} {@code milli} where they stand, as coins: into the worn purse if it has room, else the inventory, else the ground. */
	public static void pay(ServerPlayer player, long milli) {
		deposit(player, stacks(local(player), milli));
	}

	/** Gives {@code player} {@code milli} in {@code f}'s coin (see {@link #pay(ServerPlayer, long)}). */
	public static void pay(ServerPlayer player, @Nullable Faction f, long milli) {
		deposit(player, stacks(local(player, f), milli));
	}

	// ---- coins as items --------------------------------------------------------------------------------------------------

	/** {@code milli} as coins in {@code f}'s coin, for loot, chests and storehouses. */
	public static List<ItemStack> stacks(@Nullable Faction f, long milli) {
		return stacks(local(server, f), milli);
	}

	public static List<ItemStack> stacks(@Nullable MinecraftServer server, @Nullable Faction f, long milli) {
		return stacks(local(server, f), milli);
	}

	/** {@code milli} as the coins of the land around {@code pos}. */
	public static List<ItemStack> stacks(ServerLevel level, BlockPos pos, long milli) {
		return stacks(local(level, pos), milli);
	}

	/** {@code milli} as silver, for a place with no settlement or position to ask. */
	public static List<ItemStack> stacks(long milli) {
		return stacks(Currency.SILVER, milli);
	}

	private static List<ItemStack> stacks(Currency coin, long milli) {
		return milli <= 0L ? List.of() : Currencies.pay(coin, milli).stacks();
	}

	/** (0.7.498) One coin of the land where {@code player} stands, to draw beside a price (Kitchen's butcher, over the bridge). */
	public static ItemStack coin(ServerPlayer player) {
		return new ItemStack(local(player).item());
	}

	/** A price of {@code milli} as asked in {@code f}'s coin ("12 Realm Silver, 3 Copper Bit"). */
	public static Component text(@Nullable Faction f, long milli) {
		return Currencies.amount(Currencies.owe(local(server, f), milli));
	}

	public static Component text(ServerLevel level, BlockPos pos, long milli) {
		return Currencies.amount(Currencies.owe(local(level, pos), milli));
	}

	public static Component text(ServerPlayer player, long milli) {
		return Currencies.amount(Currencies.owe(local(player), milli));
	}

	/** A price of {@code milli} in silver, for a place with no settlement or position to ask. */
	public static Component text(long milli) {
		return Currencies.amount(Currencies.owe(Currency.SILVER, milli));
	}

	/** A payout of {@code milli} as given in {@code f}'s coin (never more than the worth). */
	public static Component textPaid(@Nullable Faction f, long milli) {
		return Currencies.amount(Currencies.pay(local(server, f), milli));
	}

	public static Component textPaid(ServerPlayer player, long milli) {
		return Currencies.amount(Currencies.pay(local(player), milli));
	}

	/** Hands {@code stacks} to {@code player}: into the worn purse where they fit, then the inventory, the rest onto the ground. */
	public static void deposit(ServerPlayer player, List<ItemStack> stacks) {
		for (ItemStack stack : stacks) {
			deposit(player, stack);
		}
	}

	/**
	 * Hands {@code stack} to {@code player}: a coin a purse can hold goes into the worn purse first (as far as it has room),
	 * the rest into the inventory, and what doesn't fit onto the ground. The greenskins' and pirates' coins go straight to the pack.
	 */
	public static void deposit(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemStack rest = stack.copy();
		Currency c = Currency.byItem(rest.getItem());
		ItemStack belt = c != null && c.money() ? PowersCompat.belt(player) : ItemStack.EMPTY;
		if (belt.is(MoneyItems.COIN_PURSE)) {
			PurseContents before = PurseContents.of(belt);
			PurseContents after = before.with(c, rest.getCount());
			int put = after.count(c) - before.count(c);
			if (put > 0) {
				PurseContents.set(belt, after);
				PowersCompat.setBelt(player, belt);
				rest.shrink(put);
			}
		}
		give(player, rest);
	}

	/** {@code n} of {@code c}'s coins, deposited. */
	private static void giveCoins(ServerPlayer player, Currency c, long n) {
		for (long left = n; left > 0L; left -= STACK) {
			deposit(player, new ItemStack(c.item(), (int)Math.min(STACK, left)));
		}
	}

	/** Hands {@code stacks} to {@code player}: into the inventory as far as it goes, the rest onto the ground. */
	public static void give(ServerPlayer player, List<ItemStack> stacks) {
		for (ItemStack stack : stacks) {
			give(player, stack);
		}
	}

	public static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		player.getInventory().add(stack);
		if (!stack.isEmpty()) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	// ---- where coins are kept ----------------------------------------------------------------------------------------

	private static long have(List<Source> sources, Currency c) {
		long n = 0L;
		for (Source s : sources) {
			n += s.count(c);
		}
		return n;
	}

	/** Takes up to {@code n} of {@code c} across {@code sources} in order; how many were taken. */
	private static int take(List<Source> sources, Currency c, int n) {
		int left = n;
		for (Source s : sources) {
			if (left <= 0) {
				break;
			}
			int k = Math.min(left, s.count(c));
			if (k > 0) {
				s.take(c, k);
				left -= k;
			}
		}
		return n - left;
	}

	private static boolean isCoin(ItemStack stack) {
		return !stack.isEmpty() && Currency.byItem(stack.getItem()) != null;
	}

	/** Spending order: loose coins, the belt purse, the pack's purses, bundles. */
	private static List<Source> sources(ServerPlayer player) {
		List<Source> out = new ArrayList<>();
		Inventory inv = player.getInventory();
		out.add(new Pack(player));
		ItemStack belt = PowersCompat.belt(player);
		if (belt.is(MoneyItems.COIN_PURSE)) {
			out.add(new PurseSource(belt, true));
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(MoneyItems.COIN_PURSE) && stack != belt) {
				out.add(new PurseSource(stack, false));
			}
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
			if (bundle != null && bundle.itemCopies().anyMatch(Money::isCoin)) {
				out.add(new BundleSource(stack, bundle));
			}
		}
		return out;
	}

	/** One place coins are kept: counted, taken from, added to, and written back. */
	private abstract static class Source {
		abstract int count(Currency coin);

		abstract void take(Currency coin, int n);

		/** Adds {@code n} coins; returns how many didn't fit. */
		abstract int add(Currency coin, int n);

		abstract void commit(ServerPlayer player);
	}

	/** Loose coins in a list of stacks (the inventory's own, or a bundle's contents). */
	private abstract static class Loose extends Source {
		abstract List<ItemStack> stacks();

		/** Puts {@code stack} somewhere in the list. */
		abstract void put(ItemStack stack);

		@Override
		int count(Currency coin) {
			int n = 0;
			for (ItemStack stack : stacks()) {
				if (stack.is(coin.item())) {
					n += stack.getCount();
				}
			}
			return n;
		}

		@Override
		void take(Currency coin, int n) {
			for (ItemStack stack : stacks()) {
				if (n > 0 && stack.is(coin.item())) {
					int k = Math.min(n, stack.getCount());
					stack.shrink(k);
					n -= k;
				}
			}
		}

		@Override
		int add(Currency coin, int n) {
			for (int left = n; left > 0; left -= STACK) {
				put(new ItemStack(coin.item(), Math.min(STACK, left)));
			}
			return 0;
		}
	}

	/** The inventory's loose coins. */
	private static final class Pack extends Loose {
		private final ServerPlayer player;
		private final Inventory inv;

		Pack(ServerPlayer player) {
			this.player = player;
			this.inv = player.getInventory();
		}

		@Override
		List<ItemStack> stacks() {
			List<ItemStack> out = new ArrayList<>();
			for (int i = 0; i < this.inv.getContainerSize(); i++) {
				ItemStack stack = this.inv.getItem(i);
				if (isCoin(stack)) {
					out.add(stack);
				}
			}
			return out;
		}

		@Override
		void put(ItemStack stack) {
			give(this.player, stack);
		}

		@Override
		void commit(ServerPlayer player) {
			this.inv.setChanged();
		}
	}

	/** Coins inside a bundle. */
	private static final class BundleSource extends Loose {
		private final ItemStack bundle;
		private final List<ItemStack> inside = new ArrayList<>();
		private boolean changed;

		BundleSource(ItemStack bundle, BundleContents contents) {
			this.bundle = bundle;
			contents.itemCopies().forEach(this.inside::add);
		}

		@Override
		List<ItemStack> stacks() {
			return this.inside;
		}

		@Override
		void take(Currency coin, int n) {
			this.changed = true;
			super.take(coin, n);
		}

		@Override
		void put(ItemStack stack) {
			this.changed = true;
			this.inside.add(stack);
		}

		/** (0.7.498) Adds only as many coins as the bundle's weight still holds; the rest is returned so it goes to the pack. */
		@Override
		int add(Currency coin, int n) {
			int put = Math.min(n, this.room(coin));
			if (put > 0) {
				super.add(coin, put);
			}
			return n - put;
		}

		/** How many more of {@code coin} the bundle holds, by its weight (copyWithContents drops what doesn't fit). */
		private int room(Currency coin) {
			BundleContents old = this.bundle.get(DataComponents.BUNDLE_CONTENTS);
			if (old == null) {
				return 0;
			}
			BundleContents now = old.copyWithContents(this.inside.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy));
			Fraction weight = now.weight().result().orElse(Fraction.ONE);
			Fraction each = Fraction.getFraction(1, new ItemStack(coin.item()).getMaxStackSize());
			return Math.max(0, Fraction.ONE.subtract(weight).divideBy(each).intValue());
		}

		@Override
		void commit(ServerPlayer player) {
			BundleContents old = this.bundle.get(DataComponents.BUNDLE_CONTENTS);
			if (this.changed && old != null) {
				this.bundle.set(DataComponents.BUNDLE_CONTENTS, old.copyWithContents(this.inside.stream().filter(s -> !s.isEmpty())));
			}
		}
	}

	/** A purse, worn or in the pack. It holds only the coins a purse can (see {@link Currency#money}). */
	private static final class PurseSource extends Source {
		private final ItemStack purse;
		private final boolean worn;
		private PurseContents now;

		PurseSource(ItemStack purse, boolean worn) {
			this.purse = purse;
			this.worn = worn;
			this.now = PurseContents.of(purse);
		}

		@Override
		int count(Currency coin) {
			return this.now.count(coin);
		}

		@Override
		void take(Currency coin, int n) {
			this.now = this.now.with(coin, -n);
		}

		@Override
		int add(Currency coin, int n) {
			if (!coin.money() || n <= 0) {
				return Math.max(0, n);
			}
			int before = this.now.count(coin);
			this.now = this.now.with(coin, n);
			return n - (this.now.count(coin) - before);
		}

		@Override
		void commit(ServerPlayer player) {
			if (!this.now.equals(PurseContents.of(this.purse))) {
				PurseContents.set(this.purse, this.now);
				if (this.worn) {
					PowersCompat.setBelt(player, this.purse);
				}
			}
		}
	}
}
