package com.jaymospowers.armory;

import com.jaymospowers.power.arsenal.ArsenalFlintlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;

/**
 * The black-powder guns ({@link GunBlast.Gun}): WeaponMod's flintlock, muskets and blunderbuss, the Void Armory's flintlock,
 * and Kingdoms' musketoon, handgonne, arquebus and fire cannon. Andy, 2026-11-22: "the loading bar does not need to be
 * there for any of this old age weaponry, also pressing q with flintlocks just swaps to the other loaded one on the
 * hotbar" ({@code client/bits/UseBar}, {@code client/mixin/OldGunSwapMixin}).
 */
public final class OldGuns {
	private OldGuns() {
	}

	/** One of the black-powder guns. */
	public static boolean is(ItemStack stack) {
		return GunBlast.Gun.of(stack) != null;
	}

	/** Loaded: WeaponMod's own reload state, the Void Armory's flintlock not spent, a Kingdoms gun charged. */
	public static boolean loaded(ItemStack stack) {
		if (ArsenalFlintlock.isFlintlock(stack)) {
			return !Boolean.TRUE.equals(stack.get(ArsenalFlintlock.SPENT));
		}
		if ("weaponmod".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
			return Boolean.TRUE.equals(DualFlintlocks.loaded(stack));
		}
		return !stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty();
	}
}
