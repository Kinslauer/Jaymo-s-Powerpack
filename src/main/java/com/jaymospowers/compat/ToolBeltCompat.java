package com.jaymospowers.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Traveler Tool Belt (optional; read by item id, never linked): without Trinkets its belt counts as worn, and is drawn on
 * the hip, while it sits in the inventory; the first one found is the one worn.
 */
public final class ToolBeltCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("travelertoolbelt");

	private ToolBeltCompat() {
	}

	/** Takes the worn tool belt out of the inventory and hands it back (for the caller to drop), or null if none. */
	public static @Nullable ItemStack takeWorn(Player player) {
		if (!LOADED) {
			return null;
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getNonEquipmentItems().size(); i++) {
			ItemStack stack = inventory.getNonEquipmentItems().get(i);
			Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (!stack.isEmpty() && id.getNamespace().equals("travelertoolbelt") && id.getPath().endsWith("belt")) {
				inventory.getNonEquipmentItems().set(i, ItemStack.EMPTY);
				inventory.setChanged();
				return stack;
			}
		}
		return null;
	}
}
