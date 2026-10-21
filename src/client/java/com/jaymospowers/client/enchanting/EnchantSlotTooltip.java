package com.jaymospowers.client.enchanting;

import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * (BWM port, after its enchanted-book tooltip) An enchanted book says what gear each enchantment in it goes on, read off
 * the enchantment's supported-items tag: "Goes on: Boots". A tag not named here shows by its own name.
 */
public final class EnchantSlotTooltip {
	/** Vanilla's enchantable tags, by the lang key's last part. */
	private static final Map<String, String> NAMES = Map.ofEntries(
		Map.entry("minecraft:enchantable/armor", "armor"),
		Map.entry("minecraft:enchantable/head_armor", "head_armor"),
		Map.entry("minecraft:enchantable/chest_armor", "chest_armor"),
		Map.entry("minecraft:enchantable/leg_armor", "leg_armor"),
		Map.entry("minecraft:enchantable/foot_armor", "foot_armor"),
		Map.entry("minecraft:enchantable/equippable", "equippable"),
		Map.entry("minecraft:enchantable/sword", "sword"),
		Map.entry("minecraft:enchantable/sweeping", "sword"),
		Map.entry("minecraft:enchantable/sharp_weapon", "sharp_weapon"),
		Map.entry("minecraft:enchantable/melee_weapon", "melee_weapon"),
		Map.entry("minecraft:enchantable/weapon", "weapon"),
		Map.entry("minecraft:enchantable/fire_aspect", "melee_weapon"),
		Map.entry("minecraft:enchantable/mace", "mace"),
		Map.entry("minecraft:enchantable/lunge", "spear"),
		Map.entry("minecraft:enchantable/mining", "mining"),
		Map.entry("minecraft:enchantable/mining_loot", "mining"),
		Map.entry("minecraft:enchantable/bow", "bow"),
		Map.entry("minecraft:enchantable/crossbow", "crossbow"),
		Map.entry("minecraft:enchantable/trident", "trident"),
		Map.entry("minecraft:enchantable/fishing", "fishing"),
		Map.entry("minecraft:enchantable/durability", "durability"),
		Map.entry("minecraft:enchantable/vanishing", "vanishing"));

	private EnchantSlotTooltip() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
			if (stored == null || stored.isEmpty()) {
				return;
			}
			boolean named = stored.size() > 1;
			for (Holder<Enchantment> enchantment : stored.keySet()) {
				Component fits = fits(enchantment.value());
				if (fits == null) {
					continue;
				}
				lines.add((named ? Component.translatable("tooltip.jaymospowers.enchant_fits.named", enchantment.value().description(), fits)
					: Component.translatable("tooltip.jaymospowers.enchant_fits", fits)).withStyle(ChatFormatting.GRAY));
			}
		});
	}

	/** What the enchantment goes on, or null if it lists items one by one rather than by a tag. */
	private static Component fits(Enchantment enchantment) {
		Optional<TagKey<Item>> tag = enchantment.definition().supportedItems().unwrapKey();
		if (tag.isEmpty()) {
			return null;
		}
		Identifier id = tag.get().location();
		String name = NAMES.get(id.toString());
		if (name != null) {
			return Component.translatable("tooltip.jaymospowers.enchant_fits." + name);
		}
		String path = id.getPath();
		String last = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
		return Component.literal(last.isEmpty() ? path : Character.toUpperCase(last.charAt(0)) + last.substring(1));
	}
}
