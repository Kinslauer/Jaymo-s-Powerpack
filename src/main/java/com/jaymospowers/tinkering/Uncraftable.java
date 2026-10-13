package com.jaymospowers.tinkering;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.item.crafting.Recipe;

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

	private Uncraftable() {
	}

	public static boolean keeps(Holder.Reference<Recipe<?>> recipe) {
		return !RECIPES.contains(recipe.key().identifier().toString());
	}
}
