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
 * API so it copies it into {@code .minecraft/tacz}; since 0.10.80 it's the only gunpack (TaCZ's modern one is kept
 * out, {@link #dropDefaultPack}). Through reflection, so Jaymo's Powers
 * still runs without TaCZ. TaCZ's own modern guns, ammo and attachments are taken off its benches
 * ({@code tinkering/Uncraftable}). As TaCZ items the guns are {@code tacz:modern_kinetic_gun} with
 * {@code GunId: "jaymospowers:musket"} in their custom data (Kingdoms hands them out that way).
 */
public final class TaczGuns {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("tacz");
	static final String PACK = "/assets/" + JaymosPowers.MOD_ID + "/custom/jaymos_flintlocks";
	/** TaCZ's own gunpack, all modern: never loaded (see dropDefaultPack). */
	static final String DEFAULT_PACK = "tacz_default_gun";

	private TaczGuns() {
	}

	public static void init() {
		if (!LOADED) {
			return;
		}
		// (0.10.66) TaCZ's gun smith table, statue, target and workbench loot tables use 1.21's block_state_property,
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

	/**
	 * (0.10.80) Andy: nothing modern in the pack. TaCZ's default gunpack (its AK-47s, ammo, attachments, benches and
	 * the scripts and poses it shares with every gun) is kept from loading: its entry comes off TaCZ's export list
	 * before TaCZ copies it into {@code .minecraft/tacz}, and a copy an earlier launch left there is deleted (TaCZ
	 * loads every folder in it). Our gunpack brings its own state machine, Gunsmith's Bench, muzzle flash, hit sounds
	 * and third-person poses instead (tools/gen_tacz_guns.py). TaCZ's art is CC BY-NC-ND, so nothing of it is copied.
	 * Called from the client and dedicated-server entrypoints, which run after every mod's main one (where TaCZ adds
	 * its pack) and before resources load (where it copies and reads them).
	 */
	@SuppressWarnings("unchecked")
	public static void dropDefaultPack() {
		if (!LOADED) {
			return;
		}
		try {
			Class<?> manager = Class.forName("com.tacz.guns.api.resource.ResourceManager");
			java.util.List<Object> entries = (java.util.List<Object>)manager.getField("EXTRA_ENTRIES").get(null);
			for (java.util.Iterator<Object> it = entries.iterator(); it.hasNext(); ) {
				Object entry = it.next();
				if (DEFAULT_PACK.equals(entry.getClass().getMethod("extraDirName").invoke(entry))) {
					it.remove();
				}
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			JaymosPowers.LOGGER.warn("Couldn't keep TaCZ's default (modern) gunpack from loading", e);
		}
		java.nio.file.Path copy = FabricLoader.getInstance().getGameDir().resolve("tacz").resolve(DEFAULT_PACK);
		if (java.nio.file.Files.isDirectory(copy)) {
			try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(copy)) {
				for (java.nio.file.Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
					java.nio.file.Files.delete(path);
				}
				JaymosPowers.LOGGER.info("Removed TaCZ's default (modern) gunpack from {}", copy);
			} catch (java.io.IOException | RuntimeException e) {
				JaymosPowers.LOGGER.warn("Couldn't remove TaCZ's default gunpack from {}", copy, e);
			}
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
