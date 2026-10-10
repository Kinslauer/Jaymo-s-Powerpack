package com.jaymoskitchen.kitchen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.blay09.mods.cookingforblockheads.api.CacheHint;
import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProvider;
import net.blay09.mods.cookingforblockheads.crafting.KitchenMultiblockScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Everything a cook at a spot can reach: their own pockets, plus the whole Cooking for Blockheads kitchen it's part of
 * (fridges, counters, cabinets, milk jars, connected through counters and connectors, found by Cooking for
 * Blockheads' own multiblock scanner). With no kitchen around it's just the pockets.
 */
public final class KitchenPool {
	/** A guard against a provider that keeps handing back tokens. */
	private static final int MAX_TOKENS = 1024;

	private final List<KitchenItemProvider> providers;

	private KitchenPool(List<KitchenItemProvider> providers) {
		this.providers = providers;
	}

	public static KitchenPool at(Player player, BlockPos pos) {
		KitchenMultiblockScanner scanner = new KitchenMultiblockScanner();
		scanner.findNeighbourCraftingBlocks(player.level(), pos);
		// the player's inventory comes first in the list
		return new KitchenPool(scanner.createKitchen(player.level(), false).createCraftingContext(player).getItemProviders());
	}

	/** How many of {@code item} (that also pass {@code matches}) there are, counting no further than {@code max}. */
	public int count(Item item, Predicate<ItemStack> matches, int max) {
		return find(item, matches, max).size();
	}

	/** Takes {@code amount} of them (as many as there are, if fewer); returns how many were taken. */
	public int take(Item item, Predicate<ItemStack> matches, int amount) {
		List<IngredientToken> found = find(item, matches, amount);
		for (IngredientToken token : found) {
			token.consume();
		}
		return found.size();
	}

	/** One token per item, up to {@code max}; tokens for items that don't pass {@code matches} are passed over. */
	private List<IngredientToken> find(Item item, Predicate<ItemStack> matches, int max) {
		Ingredient ingredient = Ingredient.of(item);
		List<IngredientToken> used = new ArrayList<>();
		List<IngredientToken> found = new ArrayList<>();
		for (KitchenItemProvider provider : providers) {
			while (found.size() < max && used.size() < MAX_TOKENS) {
				IngredientToken token = provider.findIngredient(ingredient, used, CacheHint.NONE, false);
				if (token == null || token == IngredientToken.EMPTY) {
					break;
				}
				used.add(token);
				if (matches.test(token.peek())) {
					found.add(token);
				}
			}
		}
		return found;
	}
}
