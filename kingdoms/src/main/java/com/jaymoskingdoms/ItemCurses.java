package com.jaymoskingdoms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

/**
 * The curse a thing can carry. Two kinds count: the enchantments vanilla calls curses (binding, vanishing), and a curse tag
 * in the item's own data, a short name any system can put there with {@link #curse}. {@link Dispel} lifts both and
 * {@link Rituals} shows them when it identifies the thing.
 */
public final class ItemCurses {
	/** The custom data key a curse tag sits under. */
	public static final String CURSE_KEY = "jaymoskingdoms_curse";

	private ItemCurses() {
	}

	/** The name of the curse tag on {@code stack}, or null. */
	public static @Nullable String tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		return data.copyTag().getString(CURSE_KEY).filter(s -> !s.isEmpty()).orElse(null);
	}

	/** Puts a curse tag named {@code name} on {@code stack}. */
	public static void curse(ItemStack stack, String name) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(CURSE_KEY, name));
	}

	/** Whether {@code stack} carries a curse enchantment. */
	public static boolean cursedEnchant(ItemStack stack) {
		return stack.getEnchantments().keySet().stream().anyMatch(h -> h.is(EnchantmentTags.CURSE));
	}

	/** Whether {@code stack} is cursed in either way. */
	public static boolean cursed(ItemStack stack) {
		return !stack.isEmpty() && (tag(stack) != null || cursedEnchant(stack));
	}

	/** Lifts every curse from {@code stack}; true if there was one. */
	public static boolean lift(ItemStack stack) {
		boolean had = false;
		if (cursedEnchant(stack)) {
			ItemEnchantments after = EnchantmentHelper.updateEnchantments(stack, m -> m.removeIf(h -> h.is(EnchantmentTags.CURSE)));
			had = !after.equals(null);
		}
		if (tag(stack) != null) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(CURSE_KEY));
			had = true;
		}
		return had;
	}
}
