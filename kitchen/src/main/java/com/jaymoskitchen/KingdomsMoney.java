package com.jaymoskitchen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * (0.5.53) Money, when Jaymo's Kingdoms is loaded: a bridge (MethodHandles, so Kitchen never compiles against it) to
 * {@code com.jaymoskingdoms.money.Money}, which counts coins, coin stacks, purses (the worn belt purse too) and bundles
 * in copper pieces. Prices are given in silver ({@link #silver}: a silver coin is what an emerald used to be, 10 cp). Without
 * Kingdoms it falls back to emeralds one for one, so the butcher still works.
 * The coin's icon is looked up by id ({@code jaymoskingdoms:silver_coin}), so no Kingdoms item is named in code.
 */
public final class KingdomsMoney {
	private static final Logger LOGGER = LoggerFactory.getLogger("Jaymo's Kitchen");
	/** Copper pieces in a silver coin. */
	public static final long SILVER = 10L;
	private static final Identifier SILVER_COIN = Identifier.fromNamespaceAndPath("jaymoskingdoms", "silver_coin");

	private static boolean looked;
	private static @Nullable MethodHandle count, charge, pay, canPay, text;

	private KingdomsMoney() {
	}

	/** {@code n} silver, in copper pieces (what a price of "N emeralds" is now). */
	public static long silver(long n) {
		return n * SILVER;
	}

	/** Whether Kingdoms' money is here (else emeralds stand in). */
	public static boolean present() {
		look();
		return count != null;
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			return;
		}
		try {
			Class<?> money = Class.forName("com.jaymoskingdoms.money.Money");
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			count = lookup.findStatic(money, "count", MethodType.methodType(long.class, ServerPlayer.class));
			charge = lookup.findStatic(money, "charge", MethodType.methodType(boolean.class, ServerPlayer.class, long.class));
			pay = lookup.findStatic(money, "pay", MethodType.methodType(void.class, ServerPlayer.class, long.class));
			canPay = lookup.findStatic(money, "canPay", MethodType.methodType(boolean.class, ServerPlayer.class, long.class));
			text = lookup.findStatic(money, "text", MethodType.methodType(Component.class, long.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.warn("Couldn't find Jaymo's Kingdoms' money (older Kingdoms?): paying in emeralds", e);
			count = null;
			charge = null;
			pay = null;
			canPay = null;
			text = null;
		}
	}

	/** Everything {@code player} carries, in copper pieces. */
	public static long count(ServerPlayer player) {
		look();
		if (count != null) {
			try {
				return (long)count.invoke(player);
			} catch (Throwable e) {
				LOGGER.warn("Money.count failed", e);
			}
		}
		return emeralds(player) * SILVER;
	}

	/** Whether {@code player} can pay {@code cp} (a creative player always can). */
	public static boolean canPay(ServerPlayer player, long cp) {
		look();
		if (canPay != null) {
			try {
				return (boolean)canPay.invoke(player, cp);
			} catch (Throwable e) {
				LOGGER.warn("Money.canPay failed", e);
			}
		}
		return cp <= 0L || player.hasInfiniteMaterials() || emeralds(player) * SILVER >= cp;
	}

	/** Takes {@code cp} from {@code player}, all or nothing; true if it was paid. */
	public static boolean charge(ServerPlayer player, long cp) {
		look();
		if (charge != null) {
			try {
				return (boolean)charge.invoke(player, cp);
			} catch (Throwable e) {
				LOGGER.warn("Money.charge failed", e);
			}
		}
		if (cp <= 0L || player.hasInfiniteMaterials()) {
			return true;
		}
		int need = (int)((cp + SILVER - 1) / SILVER);
		if (emeralds(player) < need) {
			return false;
		}
		int left = need;
		for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(Items.EMERALD)) {
				int k = Math.min(left, stack.getCount());
				stack.shrink(k);
				left -= k;
			}
		}
		return true;
	}

	/** Gives {@code cp} to {@code player} (into the worn purse, else the inventory, else the ground); without Kingdoms, emeralds. */
	public static void pay(ServerPlayer player, long cp) {
		if (cp <= 0L) {
			return;
		}
		look();
		if (pay != null) {
			try {
				pay.invoke(player, cp);
				return;
			} catch (Throwable e) {
				LOGGER.warn("Money.pay failed", e);
			}
		}
		int n = (int)((cp + SILVER - 1) / SILVER);
		while (n > 0) {
			int k = Math.min(n, 64);
			ItemStack stack = new ItemStack(Items.EMERALD, k);
			player.getInventory().add(stack);
			if (!stack.isEmpty()) {
				player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			n -= k;
		}
	}

	/** {@code cp} as words: "3 g 4 s" with Kingdoms, "N emeralds" without. */
	public static Component text(long cp) {
		look();
		if (text != null) {
			try {
				return (Component)text.invoke(cp);
			} catch (Throwable e) {
				LOGGER.warn("Money.text failed", e);
			}
		}
		return Component.translatable("money.jaymoskitchen.emeralds", (cp + SILVER - 1) / SILVER);
	}

	/** The silver coin to draw beside a price (an emerald without Kingdoms). */
	public static ItemStack icon() {
		Item coin = BuiltInRegistries.ITEM.getOptional(SILVER_COIN).orElse(Items.EMERALD);
		return new ItemStack(coin);
	}

	private static long emeralds(ServerPlayer player) {
		long n = 0L;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(Items.EMERALD)) {
				n += stack.getCount();
			}
		}
		return n;
	}
}
