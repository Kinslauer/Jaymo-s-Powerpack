package com.jaymospowers.tinkering;

import java.util.List;
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

	/**
	 * (0.10.43) TaCZ's own modern guns, their ammo and attachments (its default gunpack's recipes) and the attachment
	 * bench: the pack only uses TaCZ for our flintlocks (armory/TaczGuns), whose recipes are jaymospowers:gun/... .
	 */
	static final List<String> PREFIXES = List.of("tacz:gun/", "tacz:ammo/", "tacz:attachments/");
	static final Set<String> TACZ = Set.of("tacz:attachment_workbench");

	public static boolean keeps(Holder.Reference<Recipe<?>> recipe) {
		String id = recipe.key().identifier().toString();
		return !RECIPES.contains(id) && !TACZ.contains(id) && PREFIXES.stream().noneMatch(id::startsWith);
	}
}
