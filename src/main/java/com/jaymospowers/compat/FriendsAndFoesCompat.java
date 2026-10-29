package com.jaymospowers.compat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.Baubles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * (0.17.188) Friends &amp; Foes (optional; CC BY-NC-ND, shipped unchanged, reached only by reflection). Its Totems of
 * Freezing and Illusion fire from the hand or from whatever slot mods are on its {@code ModChecker} list (Trinkets,
 * Curios); this puts our {@link Baubles} on that list, so a totem worn as a charm saves you just the same. It hands back
 * the worn stack itself, which Friends &amp; Foes uses up.
 */
public final class FriendsAndFoesCompat {
	private static final String MOD_ID = "friendsandfoes";
	private static final String CHECKER = "com.faboslav.friendsandfoes.common.modcompat.ModChecker";
	private static final String COMPAT = "com.faboslav.friendsandfoes.common.modcompat.ModCompat";

	private FriendsAndFoesCompat() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return;
		}
		try {
			ClassLoader loader = FriendsAndFoesCompat.class.getClassLoader();
			Class<?> compat = Class.forName(COMPAT, true, loader);
			Class<? extends Enum> type = (Class<? extends Enum>)Class.forName(COMPAT + "$Type", true, loader);
			EnumSet types = EnumSet.of(Enum.valueOf(type, "CUSTOM_EQUIPMENT_SLOTS"));
			InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
				case "compatTypes" -> types;
				case "getEquippedItemFromCustomSlots" -> worn((Entity)args[0], (Predicate<ItemStack>)args[1]);
				case "toString" -> "Jaymo's Powers baubles";
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> null;
			};
			Object ours = Proxy.newProxyInstance(loader, new Class<?>[] {compat}, handler);
			((List<Object>)Class.forName(CHECKER, true, loader).getField("CUSTOM_EQUIPMENT_SLOTS_COMPATS").get(null)).add(ours);
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Friends & Foes totems won't work from bauble slots", e);
		}
	}

	/** The first worn bauble {@code wanted} accepts (the stack itself, not a copy), or null. */
	private static ItemStack worn(Entity entity, Predicate<ItemStack> wanted) {
		if (entity instanceof Player player) {
			for (ItemStack stack : Baubles.worn(player)) {
				if (!stack.isEmpty() && wanted.test(stack)) {
					return stack;
				}
			}
		}
		return null;
	}
}
