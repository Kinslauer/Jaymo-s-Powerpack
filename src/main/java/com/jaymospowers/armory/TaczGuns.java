package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
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
 * TaCZ in the pack: its modern guns, for the future only ({@link #trimDefaultPack}). Through reflection, so Jaymo's
 * Powers still runs without TaCZ. TaCZ's own modern guns, ammo and attachments are taken off its benches
 * ({@code tinkering/Uncraftable}). As TaCZ items the guns are {@code tacz:modern_kinetic_gun} with
 * {@code GunId: "tacz:ak47"} in their custom data.
 * <p>(0.10.43) Our flintlocks (musket, blunderbuss, flintlock pistol, the Void Armory's) were a TaCZ gunpack of our
 * own, crafted at the Gunsmith's Bench (TaCZ's gun smith table). (0.15.118) Andy: "i dont like the tacz flintlocks":
 * the gunpack is gone (its exported copy is deleted, {@link #OLD_PACK}) and so is the bench; the Void Armory has our
 * own Flintlock item again, Kingdoms hands out WeaponMod's musket and flintlock.
 */
public final class TaczGuns {
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("tacz");
	/** Our old flintlock gunpack, as TaCZ exported it into {@code .minecraft/tacz} (it would go on loading from there). */
	static final String OLD_PACK = "jaymos_flintlocks";
	/** TaCZ's own gunpack, all modern: for the future only (see trimDefaultPack). */
	static final String DEFAULT_PACK = "tacz_default_gun";
	/** What's left out of it: these would put it in the rest of the game. */
	static final String[] DEFAULT_PACK_LEFT_OUT = {"data/tacz/recipe", "data/tacz/tacz_loot_injectors"};

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
		hideModern();
	}

	/**
	 * (0.10.85) Andy: nothing modern in the pack, so TaCZ's default gunpack was kept from loading at all. (0.10.111) Andy:
	 * the modern guns, but only in the future ({@code power/chrono/future}): the pack loads again (it can't load per
	 * dimension; outside the future its guns, ammo and attachments are kept out of every creative tab and JEI,
	 * {@link #hideModern}; one brought back from the future is yours to keep). We export it ourselves
	 * (TaCZ's entry comes off its export list, so it can't undo this) and then take out what would leak it into the rest
	 * of the game: its recipes (modern guns are never craftable) and its bonus-chest pistol. (0.15.118) Its gun smith
	 * table index stays (it was ours, the Gunsmith's Bench, until our flintlock gunpack went; a table already placed
	 * needs one, though nothing's crafted there now), and our old flintlock gunpack is deleted. TaCZ's art is CC BY-NC-ND: it's TaCZ's own copy, untouched apart
	 * from leaving those files out. Called from the client and dedicated-server entrypoints, which run after every mod's
	 * main one (where TaCZ adds its pack) and before resources load (where it would copy and read them).
	 */
	@SuppressWarnings("unchecked")
	public static void trimDefaultPack() {
		if (!LOADED) {
			return;
		}
		java.nio.file.Path root = FabricLoader.getInstance().getGameDir().resolve("tacz");
		try {
			Class<?> manager = Class.forName("com.tacz.guns.api.resource.ResourceManager");
			Method copy = Class.forName("com.tacz.guns.util.GetJarResources")
				.getMethod("copyModDirectory", Class.class, String.class, java.nio.file.Path.class, String.class);
			java.util.List<Object> entries = (java.util.List<Object>)manager.getField("EXTRA_ENTRIES").get(null);
			for (java.util.Iterator<Object> it = entries.iterator(); it.hasNext(); ) {
				Object entry = it.next();
				Class<?> type = entry.getClass();
				if (DEFAULT_PACK.equals(type.getMethod("extraDirName").invoke(entry))) {
					copy.invoke(null, type.getMethod("modMainClass").invoke(entry), type.getMethod("srcPath").invoke(entry), root, DEFAULT_PACK);
					it.remove();
				}
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			JaymosPowers.LOGGER.warn("Couldn't export TaCZ's default (modern) gunpack ourselves; TaCZ will", e);
		}
		java.nio.file.Path pack = root.resolve(DEFAULT_PACK);
		for (java.nio.file.Path path : java.util.stream.Stream.concat(java.util.Arrays.stream(DEFAULT_PACK_LEFT_OUT).map(pack::resolve),
			java.util.stream.Stream.of(root.resolve(OLD_PACK))).toList()) {
			if (!java.nio.file.Files.exists(path)) {
				continue;
			}
			try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(path)) {
				for (java.nio.file.Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
					java.nio.file.Files.delete(p);
				}
			} catch (java.io.IOException | RuntimeException e) {
				JaymosPowers.LOGGER.warn("Couldn't delete {} from TaCZ's gunpacks", path, e);
			}
		}
	}

	/** TaCZ's ids for a gun, ammo, an attachment (or an ammo box's ammo) in an item's custom data. */
	static final String[] ID_KEYS = {"GunId", "AmmoId", "AttachmentId"};

	/** Whether {@code stack} is one of TaCZ's own (modern) guns, its ammo, an ammo box of it, or an attachment. */
	public static boolean isModern(ItemStack stack) {
		if (!LOADED || stack.isEmpty() || !"tacz".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		CompoundTag tag = data.copyTag();
		for (String key : ID_KEYS) {
			if (tag.getStringOr(key, "").startsWith("tacz:")) {
				return true;
			}
		}
		return false;
	}

	/** (0.10.111) TaCZ's modern guns, ammo and attachments are in no creative tab (JEI lists what the tabs hold). */
	public static void hideModern() {
		if (!LOADED) {
			return;
		}
		CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, output) -> {
			if (ZERO_TAB.equals(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab))) {
				// an empty category tab isn't shown at all
				output.getDisplayStacks().clear();
				output.getSearchTabStacks().clear();
				return;
			}
			output.getDisplayStacks().removeIf(TaczGuns::isModern);
			output.getSearchTabStacks().removeIf(TaczGuns::isModern);
		});
	}

	/**
	 * (0.11.9) Andy: TaCZ's "Timeless & Classics Guns: Zero" tab goes (its benches, targets, statue and ammo boxes are
	 * uncraftable and modern anyway, {@code tinkering/Uncraftable}); the gun smith table (our Gunsmith's Bench) moved to
	 * Functional Blocks, until (0.15.118) it went with our flintlocks.
	 */
	static final Identifier ZERO_TAB = Identifier.fromNamespaceAndPath("tacz", "other");

	/** One of TaCZ's own guns ({@code tacz:ak47}) with {@code rounds} in it; empty without TaCZ. */
	public static ItemStack modernGun(String name, int rounds) {
		return gun(Identifier.fromNamespaceAndPath("tacz", name), rounds);
	}

	/** {@code count} rounds of TaCZ ammo {@code id} ({@code tacz:9mm}); empty without TaCZ. */
	public static ItemStack modernAmmo(String id, int count) {
		Item item = LOADED ? BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("tacz", "ammo")) : Items.AIR;
		if (item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("AmmoId", id);
		ItemStack stack = new ItemStack(item, count);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** The TaCZ gun id in {@code stack}'s custom data, or "". */
	public static String gunId(ItemStack stack) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr("GunId", "");
	}

	static ItemStack gun(Identifier id, int rounds) {
		Item item = LOADED ? BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("tacz", "modern_kinetic_gun")) : Items.AIR;
		if (item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("GunId", id.toString());
		tag.putString("GunFireMode", "SEMI");
		tag.putInt("GunCurrentAmmoCount", rounds);
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/**
	 * (0.15.114) Takes one round out of a TaCZ gun (the one in the barrel first, else the magazine's). Returns false if it
	 * was empty.
	 */
	public static boolean spendRound(ItemStack stack) {
		if (ammo(stack) <= 0) {
			return false;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			if (tag.getBooleanOr("HasBulletInBarrel", false)) {
				tag.putBoolean("HasBulletInBarrel", false);
			} else {
				tag.putInt("GunCurrentAmmoCount", Math.max(0, tag.getIntOr("GunCurrentAmmoCount", 0) - 1));
			}
		});
		return true;
	}

	private static boolean soundLooked;
	private static @org.jspecify.annotations.Nullable Method sendSound;

	/**
	 * (0.15.114) One of a TaCZ gun's own sounds ({@code shoot_3p}, {@code fire_select}, {@code flesh_hit}: the keys of its
	 * display's "sounds") heard by everyone within {@code range} of {@code from}, through TaCZ (by reflection, as
	 * {@code FutureGuns} does). Returns false if that couldn't be done.
	 */
	public static boolean gunSound(net.minecraft.world.entity.LivingEntity from, String gunId, String sound, int range, float volume, float pitch) {
		if (!LOADED || gunId.isEmpty()) {
			return false;
		}
		if (!soundLooked) {
			soundLooked = true;
			try {
				sendSound = Class.forName("com.tacz.guns.sound.SoundManager").getMethod("sendSoundToNearby", net.minecraft.world.entity.LivingEntity.class,
					int.class, Identifier.class, Identifier.class, String.class, float.class, float.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				sendSound = null;
			}
		}
		Identifier id = Identifier.tryParse(gunId);
		if (sendSound == null || id == null) {
			return false;
		}
		try {
			sendSound.invoke(null, from, range, id, id.withSuffix("_display"), sound, volume, pitch);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			sendSound = null;
			return false;
		}
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
