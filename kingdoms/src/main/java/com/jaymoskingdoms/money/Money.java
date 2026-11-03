package com.jaymoskingdoms.money;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;

/**
 * (0.7.486) The kingdoms' money, counted in copper pieces (cp; a silver coin is 10, a gold coin 100). Server side, and
 * aware of everywhere a player keeps coins: loose coins and coin stacks in the inventory, coin purses in it, coins in
 * bundles, and the purse worn in Jaymo's Powers' belt slot. Every price in the mod goes through here, so no site counts
 * emeralds, handles creative or forgets a bundle on its own. Emeralds are gems now and never count.
 *
 * <p>Order of spending: loose coins, the worn belt purse, purses in the pack, bundles. Exact change is used up before a
 * coin is broken (smallest coins first); the change from a broken coin goes back into the purse it came from, else the
 * inventory, else the ground. Old prices that said "N emeralds" are {@code silver(N)}.
 */
public final class Money {
	/** Copper pieces in a silver coin, and a gold coin. */
	public static final int SILVER = Coin.SILVER.value(), GOLD = Coin.GOLD.value();

	private Money() {
	}

	/** {@code n} silver coins' worth (what a price of "N emeralds" is now). */
	public static long silver(long n) {
		return n * SILVER;
	}

	/** {@code n} gold coins' worth. */
	public static long gold(long n) {
		return n * GOLD;
	}

	/** Everything {@code player} carries, in cp. */
	public static long count(ServerPlayer player) {
		long total = 0L;
		for (Source s : sources(player)) {
			total += s.value();
		}
		return total;
	}

	/** Whether {@code player} can pay {@code cp} (a creative player always can). */
	public static boolean canPay(ServerPlayer player, long cp) {
		return cp <= 0L || player.hasInfiniteMaterials() || count(player) >= cp;
	}

	/** Takes {@code cp} from {@code player}, all or nothing; true if it was paid (a creative player pays nothing). */
	public static boolean charge(ServerPlayer player, long cp) {
		if (cp <= 0L || player.hasInfiniteMaterials()) {
			return true;
		}
		List<Source> sources = sources(player);
		long total = 0L;
		for (Source s : sources) {
			total += s.value();
		}
		if (total < cp) {
			return false;
		}
		long need = cp;
		// exact change first, smallest coins first, so no coin is broken while the small ones can pay
		for (Coin c : Coin.values()) {
			long units = need / c.value();
			for (Source s : sources) {
				if (units <= 0) {
					break;
				}
				int n = (int)Math.min(units, s.count(c));
				if (n > 0) {
					s.take(c, n);
					units -= n;
					need -= (long)n * c.value();
				}
			}
		}
		// still short of a whole coin: break the smallest coin that covers it
		Source broke = null;
		long change = 0L;
		if (need > 0L) {
			search:
			for (Coin c : Coin.values()) {
				if (c.value() < need) {
					continue;
				}
				for (Source s : sources) {
					if (s.count(c) > 0) {
						s.take(c, 1);
						change = c.value() - need;
						need = 0L;
						broke = s;
						break search;
					}
				}
			}
		}
		if (need > 0L) {
			JaymosKingdoms.LOGGER.warn("Money.charge: {} cp short after counting enough for {}", need, cp);
		}
		long left = change;
		if (broke != null && change > 0L) {
			left = broke.add(change);
		}
		for (Source s : sources) {
			s.commit(player);
		}
		if (left > 0L) {
			give(player, Coin.coins(left));
		}
		return true;
	}

	/** Takes up to {@code cp} from {@code player}; how much was taken. */
	public static long chargeUpTo(ServerPlayer player, long cp) {
		if (cp <= 0L) {
			return 0L;
		}
		if (player.hasInfiniteMaterials()) {
			return cp;
		}
		long take = Math.min(cp, count(player));
		return take > 0L && charge(player, take) ? take : 0L;
	}

	/** Gives {@code cp} to {@code player}: into the worn purse if it has room, else the inventory, else the ground. */
	public static void pay(ServerPlayer player, long cp) {
		if (cp <= 0L) {
			return;
		}
		ItemStack belt = PowersCompat.belt(player);
		if (belt.is(MoneyItems.COIN_PURSE)) {
			PurseContents.Added added = PurseContents.of(belt).plus(cp);
			if (added.contents().value() != PurseContents.of(belt).value()) {
				PurseContents.set(belt, added.contents());
				PowersCompat.setBelt(player, belt);
			}
			cp = added.left();
		}
		if (cp > 0L) {
			give(player, Coin.coins(cp));
		}
	}

	/** {@code cp} as loose coins, for loot, chests and storehouses. */
	public static List<ItemStack> stacks(long cp) {
		return Coin.coins(cp);
	}

	/** "3 g 4 s 2 c" (only the parts that aren't nothing; "0 c" for none). */
	public static Component text(long cp) {
		long gold = cp / GOLD, silver = cp % GOLD / SILVER, copper = cp % SILVER;
		MutableComponent out = Component.empty();
		boolean any = false;
		if (gold > 0L) {
			out.append(Component.translatable("money.jaymoskingdoms.gold", gold));
			any = true;
		}
		if (silver > 0L) {
			out.append(any ? Component.literal(" ") : Component.empty()).append(Component.translatable("money.jaymoskingdoms.silver", silver));
			any = true;
		}
		if (copper > 0L || !any) {
			out.append(any ? Component.literal(" ") : Component.empty()).append(Component.translatable("money.jaymoskingdoms.copper", copper));
		}
		return out;
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
			if (bundle != null && bundle.itemCopies().anyMatch(s -> Coin.valueOf(s) > 0L)) {
				out.add(new BundleSource(stack, bundle));
			}
		}
		return out;
	}

	/** One place coins are kept: counted, taken from, added to, and written back. */
	private abstract static class Source {
		abstract int count(Coin coin);

		abstract void take(Coin coin, int n);

		/** Adds {@code cp} as coins; returns what didn't fit (in cp). */
		abstract long add(long cp);

		abstract void commit(ServerPlayer player);

		final long value() {
			long v = 0L;
			for (Coin c : Coin.values()) {
				v += (long)count(c) * c.value();
			}
			return v;
		}
	}

	/** Loose coins and coin stacks in a list of stacks (the inventory's own, or a bundle's contents). */
	private abstract static class Loose extends Source {
		abstract List<ItemStack> stacks();

		/** Puts {@code stack} somewhere in the list; what is left over is returned. */
		abstract ItemStack put(ItemStack stack);

		@Override
		int count(Coin coin) {
			int n = 0;
			for (ItemStack stack : stacks()) {
				if (stack.is(coin.item())) {
					n += stack.getCount();
				} else if (stack.is(coin.stackItem())) {
					n += stack.getCount() * Coin.STACK_COINS;
				}
			}
			return n;
		}

		@Override
		void take(Coin coin, int n) {
			for (ItemStack stack : stacks()) {
				if (n > 0 && stack.is(coin.item())) {
					int k = Math.min(n, stack.getCount());
					stack.shrink(k);
					n -= k;
				}
			}
			for (ItemStack stack : stacks()) {
				if (n > 0 && stack.is(coin.stackItem())) {
					int k = Math.min((n + Coin.STACK_COINS - 1) / Coin.STACK_COINS, stack.getCount());
					stack.shrink(k);
					n -= k * Coin.STACK_COINS;
				}
			}
			// a stack broken for part of its coins: the rest of its ten comes back loose
			if (n < 0) {
				put(coin.stack(-n));
			}
		}

		@Override
		long add(long cp) {
			long left = 0L;
			for (ItemStack stack : Coin.coins(cp)) {
				ItemStack rest = put(stack);
				left += Coin.valueOf(rest);
			}
			return left;
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
				if (!stack.isEmpty() && (Coin.of(stack) != null || Coin.ofStack(stack) != null)) {
					out.add(stack);
				}
			}
			return out;
		}

		@Override
		ItemStack put(ItemStack stack) {
			give(this.player, stack);
			return ItemStack.EMPTY;
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

		BundleSource(ItemStack bundle, BundleContents contents) {
			this.bundle = bundle;
			contents.itemCopies().forEach(this.inside::add);
		}

		@Override
		List<ItemStack> stacks() {
			return this.inside;
		}

		@Override
		ItemStack put(ItemStack stack) {
			this.inside.add(stack.copy());
			stack.setCount(0);
			return stack;
		}

		@Override
		void commit(ServerPlayer player) {
			BundleContents old = this.bundle.get(DataComponents.BUNDLE_CONTENTS);
			if (old != null) {
				this.bundle.set(DataComponents.BUNDLE_CONTENTS, old.copyWithContents(this.inside.stream().filter(s -> !s.isEmpty())));
			}
		}
	}

	/** A purse, worn or in the pack. */
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
		int count(Coin coin) {
			return this.now.count(coin);
		}

		@Override
		void take(Coin coin, int n) {
			this.now = this.now.with(coin, -n);
		}

		@Override
		long add(long cp) {
			PurseContents.Added added = this.now.plus(cp);
			this.now = added.contents();
			return added.left();
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
