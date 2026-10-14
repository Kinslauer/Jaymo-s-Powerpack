package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * (0.10.43) Our flintlocks in TaCZ: the musket, blunderbuss and flintlock pistol are a gunpack of our own
 * ({@code assets/jaymospowers/custom/jaymos_flintlocks}, made by tools/gen_tacz_guns.py), handed to TaCZ through its
 * API so it copies it into {@code .minecraft/tacz} beside its default pack. Through reflection, so Jaymo's Powers
 * still runs without TaCZ. TaCZ's own modern guns, ammo and attachments are taken off its benches
 * ({@code tinkering/Uncraftable}). As TaCZ items the guns are {@code tacz:modern_kinetic_gun} with
 * {@code GunId: "jaymospowers:musket"} in their custom data (Kingdoms hands them out that way).
 */
public final class TaczGuns {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("tacz");
	static final String PACK = "/assets/" + JaymosPowers.MOD_ID + "/custom/jaymos_flintlocks";

	private TaczGuns() {
	}

	public static void init() {
		if (!LOADED) {
			return;
		}
		// (0.10.65) TaCZ's gun smith table, statue, target and workbench loot tables use 1.21's block_state_property,
		// which 26.3 can't read or upgrade, so a server wouldn't load its data at all: 26.3-format copies in an
		// always-on pack
		ResourceLoader.registerBuiltinPack(JaymosPowers.id("tacz_fixes"),
			FabricLoader.getInstance().getModContainer(JaymosPowers.MOD_ID).orElseThrow(),
			Component.literal("Jaymo's Powers: TaCZ block drops"), PackActivationType.ALWAYS_ENABLED);
		try {
			Class.forName("com.tacz.guns.api.resource.ResourceManager")
				.getMethod("registerExportResource", Class.class, String.class)
				.invoke(null, JaymosPowers.class, PACK);
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosPowers.LOGGER.warn("Couldn't hand our flintlocks to TaCZ", e);
		}
	}

	/** One of our guns by name ({@code musket}, {@code void_flintlock}, ...), loaded; empty without TaCZ. */
	public static ItemStack gun(String name) {
		Item item = LOADED ? BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("tacz", "modern_kinetic_gun")) : Items.AIR;
		if (item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("GunId", JaymosPowers.MOD_ID + ":" + name);
		tag.putString("GunFireMode", "SEMI");
		tag.putInt("GunCurrentAmmoCount", 1);
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** Whether {@code stack} is our TaCZ gun {@code name}. */
	public static boolean is(ItemStack stack, String name) {
		if (!LOADED || stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && (JaymosPowers.MOD_ID + ":" + name).equals(data.copyTag().getStringOr("GunId", ""));
	}

	/** Rounds in a TaCZ gun: its magazine plus the one in the barrel. */
	public static int ammo(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return 0;
		}
		CompoundTag tag = data.copyTag();
		return tag.getIntOr("GunCurrentAmmoCount", 0) + (tag.getBooleanOr("HasBulletInBarrel", false) ? 1 : 0);
	}
}
