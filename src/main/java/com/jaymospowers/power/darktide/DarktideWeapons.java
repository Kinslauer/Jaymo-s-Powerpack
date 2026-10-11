package com.jaymospowers.power.darktide;

import com.jaymospowers.power.arsenal.ArsenalPool;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Balkon's WeaponMod (optional), the only place Darktide looks at its items: katanas ({@code weaponmod:katana.<tier>})
 * and knives ({@code weaponmod:knife.<tier>}), every tier, found by registry id at runtime (no compile dependency, the
 * same way {@link ArsenalPool} finds them). Sora's Daggers are Wind's and never count. Without WeaponMod everything
 * here says "unarmed".
 */
public final class DarktideWeapons {
	/** What the hands hold, as far as Darktide cares. */
	public enum Kit { UNARMED, KATANA, KNIFE, OTHER }

	/** The tier Tide Arms conjures. */
	static final String TIDE_TIER = "diamond";

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(ArsenalPool.WEAPON_MOD);

	private DarktideWeapons() {
	}

	public static boolean loaded() {
		return LOADED;
	}

	public static boolean isKatana(ItemStack stack) {
		return LOADED && is(stack, "katana.");
	}

	public static boolean isKnife(ItemStack stack) {
		return LOADED && is(stack, "knife.");
	}

	private static boolean is(ItemStack stack, String prefix) {
		if (stack.isEmpty()) {
			return false;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return ArsenalPool.WEAPON_MOD.equals(id.getNamespace()) && id.getPath().startsWith(prefix);
	}

	/** The main hand's kit (both sides). */
	public static Kit kit(Player player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (main.isEmpty()) {
			return Kit.UNARMED;
		}
		if (isKatana(main)) {
			return Kit.KATANA;
		}
		if (isKnife(main)) {
			return Kit.KNIFE;
		}
		return Kit.OTHER;
	}

	/** Whether the hands can raise a Darktide guard: main hand empty, a katana or a knife; off hand empty or a blade too. */
	public static boolean canGuard(Player player) {
		if (kit(player) == Kit.OTHER) {
			return false;
		}
		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		return off.isEmpty() || isKnife(off) || isKatana(off);
	}

	/** A conjurable Balkon blade ({@code "katana"} or {@code "knife"}) of the Tide Arms tier, or empty without WeaponMod. */
	static ItemStack conjure(String kind) {
		if (!LOADED) {
			return ItemStack.EMPTY;
		}
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(ArsenalPool.WEAPON_MOD, kind + "." + TIDE_TIER));
		return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}
}
