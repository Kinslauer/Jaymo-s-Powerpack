package com.jaymospowers.core.wounds;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * How deep a blade bites: shallow for wood and stone, normal for iron, gold and copper (and anything unknown), deep for
 * diamond and netherite; Sharpness adds a step. Read from the weapon's item name, so modded blades named for their metal
 * fall in too. Depth widens the mark ({@link WoundMarks}), opens a cut at a lighter blow ({@link Bleeding}) and makes a
 * limb likelier to come off ({@link com.jaymospowers.core.Dismember}).
 */
public final class CutDepth {
	public static final int SHALLOW = 0, NORMAL = 1, DEEP = 2;
	/** Per depth: how much a cut's raw edge is spread, how much of {@link Bleeding#BLEED_FROM} a cut needs, how much its sever chance is scaled. */
	public static final float[] WIDTH = {0.6F, 1.0F, 1.6F};
	public static final float[] BLEED_FROM = {1.5F, 1.0F, 0.6F};
	public static final float[] SEVER = {0.6F, 1.0F, 1.5F};
	private static final String[] SHALLOW_WORDS = {"wooden", "wood", "stone", "flint", "bone"};
	private static final String[] DEEP_WORDS = {"diamond", "netherite", "obsidian"};

	private CutDepth() {
	}

	/** The depth {@code weapon}'s blade cuts to ({@link #NORMAL} for an empty hand). */
	public static int of(Level level, ItemStack weapon) {
		if (weapon == null || weapon.isEmpty()) {
			return NORMAL;
		}
		String path = BuiltInRegistries.ITEM.getKey(weapon.getItem()).getPath();
		int depth = NORMAL;
		for (String word : SHALLOW_WORDS) {
			if (path.contains(word)) {
				depth = SHALLOW;
			}
		}
		for (String word : DEEP_WORDS) {
			if (path.contains(word)) {
				depth = DEEP;
			}
		}
		int sharp = EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), weapon);
		return Math.min(DEEP, depth + (sharp > 0 ? 1 : 0));
	}

	/** {@link #BLEED_FROM} for {@code weapon}'s depth (1 for no weapon). */
	public static float bleedFromScale(Level level, ItemStack weapon) {
		return weapon == null || weapon.isEmpty() ? 1.0F : BLEED_FROM[of(level, weapon)];
	}

	/** {@link #SEVER} for {@code weapon}'s depth. */
	public static float severScale(Level level, ItemStack weapon) {
		return SEVER[of(level, weapon)];
	}
}
