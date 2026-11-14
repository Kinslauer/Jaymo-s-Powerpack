package com.jaymospowers.compat;

import com.jaymospowers.core.TwoHanded;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.logic.WeaponRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Better Combat (optional) files every weapon in the pack under a category (dagger, sword, claymore, spear, halberd,
 * mace...) and gives each its reach. This is the only place that asks it, and only when it is loaded ({@link #LOADED}):
 * {@code combat/WeaponKind} reads the category, {@code combat/Blows} the reach.
 */
public final class BetterCombatWeapons {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("bettercombat");
	/** A mob's reach before its weapon's bonus, as Jaymo's Kingdoms' fighters have it (a player's is their own attribute). */
	public static final double MOB_REACH = 3.0;

	private BetterCombatWeapons() {
	}

	/** Better Combat's category for {@code stack} ("dagger", "spear"...), or null if it has none or the mod is absent. */
	public static @Nullable String category(ItemStack stack) {
		return LOADED ? Asked.category(stack) : null;
	}

	/** How far {@code player} reaches with {@code stack} (blocks), two-handed weapons' extra included; 0 if the mod is absent. */
	public static double reach(Player player, ItemStack stack) {
		return LOADED ? Asked.reach(player, stack) : 0.0;
	}

	/** How far a mob reaches with {@code stack} (blocks); 0 if the mod is absent or doesn't know the weapon. */
	public static double mobReach(ItemStack stack) {
		return LOADED ? Asked.mobReach(stack) : 0.0;
	}

	/** Better Combat's own classes, only ever loaded when it is. */
	private static final class Asked {
		static @Nullable WeaponAttributes attributes(ItemStack stack) {
			try {
				return WeaponRegistry.getAttributes(stack);
			} catch (RuntimeException e) {
				return null;
			}
		}

		static @Nullable String category(ItemStack stack) {
			WeaponAttributes attributes = attributes(stack);
			String category = attributes == null ? null : attributes.category();
			return category == null || category.isEmpty() ? null : category;
		}

		static double reach(Player player, ItemStack stack) {
			try {
				return PlayerAttackHelper.getRangeForItem(player, stack);
			} catch (RuntimeException e) {
				return 0.0;
			}
		}

		static double mobReach(ItemStack stack) {
			WeaponAttributes attributes = attributes(stack);
			if (attributes == null) {
				return 0.0;
			}
			double reach = attributes.attackRange() > 0.0 ? attributes.attackRange() : MOB_REACH + attributes.rangeBonus();
			return attributes.isTwoHanded() ? reach * TwoHanded.REACH : reach;
		}
	}
}
