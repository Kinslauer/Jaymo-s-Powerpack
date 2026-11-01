package com.jaymospowers.armory;

import java.lang.reflect.Method;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (Motion patch 4) Two of WeaponMod's flintlock pistols at once (Andy: "make flintlock work similarly ... and dual
 * wielding"). With one in each hand, use goes to whichever hand's turn it is, so they fire one after the other and
 * each reloads on its own:
 * <pre>
 *   main  off     use goes to
 *   ready ready   main (fires)
 *   empty ready   off  (fires)
 *   empty empty   main (reloads)
 *   ready empty   off  (reloads)
 * </pre>
 * So from both loaded it's fire, fire, reload, reload, the main hand always first. One flintlock alone, or with anything
 * else in the other hand, works as WeaponMod made it. The pistols' state is WeaponMod's own (read by reflection, so
 * nothing here needs WeaponMod to load). The first-person hands rise one at a time ({@code client/armory/MusketSights}).
 */
public final class DualFlintlocks {
	private static final String FLINTLOCK = "weaponmod:flintlock";
	private static boolean looked;
	private static @Nullable Method isReloaded;

	private DualFlintlocks() {
	}

	public static boolean isFlintlock(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(FLINTLOCK);
	}

	/** Whether a use of the main hand should step aside for the off hand's flintlock. */
	public static boolean offHandsTurn(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return false;
		}
		ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
		if (!isFlintlock(main) || !isFlintlock(off)) {
			return false;
		}
		Boolean mainLoaded = loaded(main), offLoaded = loaded(off);
		return mainLoaded != null && offLoaded != null && !mainLoaded.equals(offLoaded);
	}

	/** WeaponMod's own "reloaded" for this gun, or null if it can't be read. */
	static @Nullable Boolean loaded(ItemStack stack) {
		if (!looked) {
			looked = true;
			try {
				isReloaded = Class.forName("ckathode.weaponmod.item.RangedComponent").getMethod("isReloaded", ItemStack.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				isReloaded = null;
			}
		}
		if (isReloaded == null) {
			return null;
		}
		try {
			return (Boolean)isReloaded.invoke(null, stack);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
