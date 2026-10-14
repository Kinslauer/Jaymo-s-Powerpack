package com.jaymoskingdoms;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * (0.7.8) Jaymo's flintlocks (musket, blunderbuss, flintlock pistol), a TaCZ gunpack that Jaymo's Powers ships
 * (its armory/TaczGuns). TaCZ guns and ammo are one item each, told apart by an id in their custom data, so they're
 * made here by item id: nothing of TaCZ is compiled against. Empty stacks when TaCZ isn't installed.
 */
public final class TaczGuns {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("tacz");

	private TaczGuns() {
	}

	/** One of our guns ({@code musket}, {@code blunderbuss}, {@code flintlock_pistol}), loaded and ready. */
	public static ItemStack gun(String name) {
		CompoundTag tag = new CompoundTag();
		tag.putString("GunId", "jaymospowers:" + name);
		tag.putString("GunFireMode", "SEMI");
		tag.putInt("GunCurrentAmmoCount", 1);
		return make("modern_kinetic_gun", 1, tag);
	}

	/** {@code n} of our ammo ({@code musket_ball}, {@code shot}). */
	public static ItemStack ammo(String name, int n) {
		CompoundTag tag = new CompoundTag();
		tag.putString("AmmoId", "jaymospowers:" + name);
		return make("ammo", n, tag);
	}

	/** Whether {@code stack} is one of TaCZ's guns. */
	public static boolean isGun(ItemStack stack) {
		return LOADED && stack.is(item("modern_kinetic_gun"));
	}

	private static ItemStack make(String id, int n, CompoundTag tag) {
		Item item = LOADED ? item(id) : Items.AIR;
		if (item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item, n);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("tacz", id));
	}
}
