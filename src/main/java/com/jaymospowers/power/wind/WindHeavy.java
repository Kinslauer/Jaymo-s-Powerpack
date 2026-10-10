package com.jaymospowers.power.wind;

import com.jaymospowers.core.Powers;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Heavy weapons in a wind's hands: axes and maces fight the wind, so a Wind user's melee hits with one deal
 * {@link #DAMAGE_SCALE} of their damage (the main hit and its sweep; see {@code mixin.WindPlayerAttackMixin}).
 */
public final class WindHeavy {
	/** What an axe or mace's melee hit is worth in a wind's hands. */
	public static final float DAMAGE_SCALE = 0.6F;

	private WindHeavy() {
	}

	/** Whether this is a heavy weapon: anything in the axes tag, or a mace. */
	public static boolean isHeavy(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(ItemTags.AXES) || stack.is(Items.MACE));
	}

	/** A melee hit's damage, after the weight of what's swung. */
	public static float scale(Player attacker, float damage) {
		return Powers.has(attacker, WindPower.INSTANCE) && isHeavy(attacker.getWeaponItem()) ? damage * DAMAGE_SCALE : damage;
	}
}
