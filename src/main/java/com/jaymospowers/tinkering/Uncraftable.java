package com.jaymospowers.tinkering;

import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * (0.8.124) Andy: no craftable diamond weapons. Vanilla's and Armory's recipes are switched off in data
 * (tools/tinkering_assets.py, tools/armory_assets.py); another mod's data pack may load after ours and win, so its
 * recipes are dropped by id as the recipe map is built ({@code mixin/UncraftableRecipeMixin}). Ids of mods that aren't
 * loaded simply never match.
 */
public final class Uncraftable {
	/** Balkon's WeaponMod: every diamond weapon (its netherite ones smith from these, so they're loot-only too). */
	static final Set<String> RECIPES = Set.of(
		"weaponmod:knife.diamond", "weaponmod:knife.diamond.a", "weaponmod:spear.diamond", "weaponmod:spear.diamond.a",
		"weaponmod:halberd.diamond", "weaponmod:battleaxe.diamond", "weaponmod:warhammer.diamond",
		"weaponmod:katana.diamond", "weaponmod:flail.diamond", "weaponmod:boomerang.diamond",
		"weaponmod:musketbayonet.diamond");

	/**
	 * (0.11.77) Andy: "all weapons should be forged". Balkon's stone and metal weapons are put together at the Tool
	 * Station from their heads instead ({@link WeaponParts}); the musket bayonet stays a musket and a knife put together.
	 */
	static final Set<String> FORGED = forged();
	/** (0.15.36) Andy: "All tools need crafting routes through jaymos": other mods' tools forged from heads instead. */
	static final Set<String> FORGED_TOOLS = Set.of("pantryforblockheads:knife");

	private static Set<String> forged() {
		Set<String> ids = new java.util.HashSet<>();
		for (String weapon : List.of("knife", "spear", "halberd", "battleaxe", "warhammer", "katana", "flail", "boomerang")) {
			for (String metal : List.of("stone", "iron", "gold", "copper")) {
				ids.add("weaponmod:" + weapon + "." + metal);
				ids.add("weaponmod:" + weapon + "." + metal + ".a");
			}
		}
		return Set.copyOf(ids);
	}

	private Uncraftable() {
	}

	/**
	 * (0.10.43) TaCZ's own modern guns, their ammo and attachments (its default gunpack's recipes) and the attachment
	 * bench: the pack only uses TaCZ for our flintlocks (armory/TaczGuns), whose recipes are jaymospowers:gun/... .
	 * (0.10.85) Andy: nothing modern at all, so every other TaCZ block and item too (ammo boxes, the ammo bench,
	 * targets, the statue, the poster); only the gun smith table (our Gunsmith's Bench) and its gunpowder recipe stay.
	 * (0.15.118) Andy didn't like our flintlocks, so they're gone and the gun smith table with them.
	 */
	static final List<String> PREFIXES = List.of("tacz:gun/", "tacz:ammo/", "tacz:attachments/", "tacz:misc/");
	static final Set<String> TACZ = Set.of("tacz:attachment_workbench", "tacz:ammo_workbench", "tacz:iron_ammo_box",
		"tacz:gold_ammo_box", "tacz:diamond_ammo_box", "tacz:statue", "tacz:target", "tacz:target_minecart",
		"tacz:gun_smith_table");

	/**
	 * (0.16.2) Andy: no craftable planks (RLCraft's start, ground/FirstTools): a log is set down and split with an axe.
	 * Every crafting recipe named for planks goes, vanilla's, the Aether's and other mods' alike.
	 */
	static boolean planks(Holder.Reference<Recipe<?>> recipe) {
		String path = recipe.key().identifier().getPath();
		return recipe.value().getType() == RecipeType.CRAFTING && (path.endsWith("_planks") || path.contains("_planks_from_"));
	}

	public static boolean keeps(Holder.Reference<Recipe<?>> recipe) {
		String id = recipe.key().identifier().toString();
		return !planks(recipe) && !RECIPES.contains(id) && !FORGED.contains(id) && !FORGED_TOOLS.contains(id) && !TACZ.contains(id) && PREFIXES.stream().noneMatch(id::startsWith);
	}
}
