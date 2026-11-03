package com.jaymoskingdoms.money;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.487) The coins: copper, silver and gold, worth 1, 10 and 100 copper pieces (cp). One silver coin is what one
 * emerald used to be, so every price that was "N emeralds" is now N silver. Each coin also comes as a stack of
 * {@value #STACK_COINS} (a placeable pile, used for hoards and big withdrawals).
 */
public enum Coin {
	COPPER("copper", 1), SILVER("silver", 10), GOLD("gold", 100);

	/** Coins in one coin-stack item. */
	public static final int STACK_COINS = 10;
	/** The most one stack of loose coins holds (a coin is an ordinary 64-stack item). */
	private static final int MOST = 64;

	private final String id;
	private final int value;

	Coin(String id, int value) {
		this.id = id;
		this.value = value;
	}

	public String id() {
		return this.id;
	}

	/** Its worth in copper pieces. */
	public int value() {
		return this.value;
	}

	public Item item() {
		return MoneyItems.COIN_ITEMS[ordinal()];
	}

	public Item stackItem() {
		return MoneyItems.STACK_ITEMS[ordinal()];
	}

	public ItemStack stack(int count) {
		return new ItemStack(item(), count);
	}

	/** The coin {@code stack} is one of (a loose coin, not a coin stack), or null. */
	public static @Nullable Coin of(ItemStack stack) {
		for (Coin c : values()) {
			if (stack.is(c.item())) {
				return c;
			}
		}
		return null;
	}

	/** The coin {@code stack} is a stack of, or null. */
	public static @Nullable Coin ofStack(ItemStack stack) {
		for (Coin c : values()) {
			if (stack.is(c.stackItem())) {
				return c;
			}
		}
		return null;
	}

	/** What {@code stack} is worth in copper pieces: loose coins and coin stacks (x10), nothing else (a purse is {@link PurseContents}). */
	public static long valueOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0L;
		}
		Coin c = of(stack);
		if (c != null) {
			return (long)c.value * stack.getCount();
		}
		c = ofStack(stack);
		return c != null ? (long)c.value * STACK_COINS * stack.getCount() : 0L;
	}

	/** {@code cp} as loose coins: gold first, then silver, then copper, at most 64 to a stack. */
	public static List<ItemStack> coins(long cp) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = values().length - 1; i >= 0 && cp > 0; i--) {
			Coin c = values()[i];
			long n = cp / c.value;
			cp -= n * c.value;
			while (n > 0) {
				int k = (int)Math.min(n, MOST);
				out.add(c.stack(k));
				n -= k;
			}
		}
		return out;
	}
}
