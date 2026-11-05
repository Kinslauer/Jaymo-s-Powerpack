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
 * (0.5.56) Money, when Jaymo's Kingdoms is loaded: a bridge (MethodHandles, so Kitchen never compiles against it) to
 * {@code com.jaymoskingdoms.money.Money}, which counts the culture coins (realm silver, hacksilver, dinars, shells, mon,
 * and the copper bit), the coin purses (the worn belt purse too) and bundles. Amounts are in milli, thousandths of an
 * emerald's worth ({@link #worth}: a price of "N emeralds" is {@code worth(N)}), and are paid and asked in the coin of the
 * land where the player stands. Without Kingdoms it falls back to emeralds one for one, so the butcher still works.
 * The coin's icon is looked up by id ({@code jaymoskingdoms:realm_silver}), so no Kingdoms item is named in code.
 */
public final class KingdomsMoney {
	private static final Logger LOGGER = LoggerFactory.getLogger("Jaymo's Kitchen");
	/** Milli in one emerald's worth (what "1" in an old price meant). */
	public static final long UNIT = 1000L;
	private static final Identifier SILVER_COIN = Identifier.fromNamespaceAndPath("jaymoskingdoms", "realm_silver");

	private static boolean looked;
	private static @Nullable MethodHandle count, charge, pay, canPay, text, textAt, coin;

	private KingdomsMoney() {
	}

	/** {@code n} emeralds' worth, in milli (what a price of "N emeralds" is now). */
	public static long worth(long n) {
		return n * UNIT;
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
			textAt = lookup.findStatic(money, "text", MethodType.methodType(Component.class, ServerPlayer.class, long.class));
			coin = lookup.findStatic(money, "coin", MethodType.methodType(ItemStack.class, ServerPlayer.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.warn("Couldn't find Jaymo's Kingdoms' money (older Kingdoms?): paying in emeralds", e);
			count = null;
			charge = null;
			pay = null;
			canPay = null;
			text = null;
			textAt = null;
			coin = null;
		}
	}

	/** Everything {@code player} carries that is money, at standard worth, in milli. */
	public static long count(ServerPlayer player) {
		look();
		if (count != null) {
			try {
				return (long)count.invoke(player);
			} catch (Throwable e) {
				LOGGER.warn("Money.count failed", e);
			}
		}
		return emeralds(player) * UNIT;
	}

	/** Whether {@code player} can pay {@code milli} (a creative player always can). */
	public static boolean canPay(ServerPlayer player, long milli) {
		look();
		if (canPay != null) {
			try {
				return (boolean)canPay.invoke(player, milli);
			} catch (Throwable e) {
				LOGGER.warn("Money.canPay failed", e);
			}
		}
		return milli <= 0L || player.hasInfiniteMaterials() || emeralds(player) * UNIT >= milli;
	}

	/** Takes {@code milli} from {@code player}, all or nothing; true if it was paid. */
	public static boolean charge(ServerPlayer player, long milli) {
		look();
		if (charge != null) {
			try {
				return (boolean)charge.invoke(player, milli);
			} catch (Throwable e) {
				LOGGER.warn("Money.charge failed", e);
			}
		}
		if (milli <= 0L || player.hasInfiniteMaterials()) {
			return true;
		}
		int need = (int)((milli + UNIT - 1) / UNIT);
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

	/** Gives {@code milli} to {@code player} as the coin of the land (into the worn purse, else the inventory, else the ground); without Kingdoms, emeralds. */
	public static void pay(ServerPlayer player, long milli) {
		if (milli <= 0L) {
			return;
		}
		look();
		if (pay != null) {
			try {
				pay.invoke(player, milli);
				return;
			} catch (Throwable e) {
				LOGGER.warn("Money.pay failed", e);
			}
		}
		int n = (int)((milli + UNIT - 1) / UNIT);
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

	/** {@code milli} as words in realm silver ("12 Realm Silver, 3 Copper Bit") with Kingdoms, "N emeralds" without. */
	public static Component text(long milli) {
		look();
		if (text != null) {
			try {
				return (Component)text.invoke(milli);
			} catch (Throwable e) {
				LOGGER.warn("Money.text failed", e);
			}
		}
		return Component.translatable("money.jaymoskitchen.emeralds", (milli + UNIT - 1) / UNIT);
	}

	/** {@code milli} as words in the coin of the land where {@code player} stands (see {@link #text(long)}). */
	public static Component text(ServerPlayer player, long milli) {
		look();
		if (textAt != null) {
			try {
				return (Component)textAt.invoke(player, milli);
			} catch (Throwable e) {
				LOGGER.warn("Money.text failed", e);
			}
		}
		return text(milli);
	}

	/** (0.5.56) One coin of the land where {@code player} stands, to draw beside a price (realm silver if Kingdoms can't say, an emerald without it). */
	public static ItemStack icon(ServerPlayer player) {
		look();
		if (coin != null) {
			try {
				return (ItemStack)coin.invoke(player);
			} catch (Throwable e) {
				LOGGER.warn("Money.coin failed", e);
			}
		}
		return icon();
	}

	/** The realm silver coin to draw beside a price (an emerald without Kingdoms). */
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
