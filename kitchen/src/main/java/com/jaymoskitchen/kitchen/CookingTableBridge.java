package com.jaymoskitchen.kitchen;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Kitchen;
import com.jaymoskitchen.cooking.Station;
import java.util.List;
import java.util.Optional;
import net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI;
import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler;
import net.blay09.mods.cookingforblockheads.crafting.CraftingContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import org.jspecify.annotations.Nullable;

/**
 * Minigame dishes in Cooking for Blockheads' cooking table: listed there like any recipe, with their ingredients
 * (from the kitchen, as usual), and picking one starts its minigame at the nearest station that can cook it (a lit
 * oven with the right pot or pan, a campfire, furnace, smoker or blast furnace).
 */
public final class CookingTableBridge {
	/** How far from the cook a station can be. */
	private static final int STATION_REACH = 6;

	private CookingTableBridge() {
	}

	public static void register() {
		MinigameRecipe.register();
		CookingForBlockheadsAPI.registerKitchenRecipeHandler(MinigameRecipe.class, new KitchenRecipeHandler<CraftingInput, MinigameRecipe>() {
			@Override
			public int mapToMatrixSlot(MinigameRecipe recipe, int ingredientIndex) {
				return ingredientIndex;
			}

			@Override
			public List<Optional<Ingredient>> getIngredients(MinigameRecipe recipe) {
				return recipe.ingredients().stream().map(Optional::of).toList();
			}

			@Override
			public ItemStackTemplate predictResultItem(MinigameRecipe recipe) {
				return recipe.result();
			}

			@Override
			public ItemStack assemble(CraftingContext context, MinigameRecipe recipe, List<IngredientToken> tokens, RegistryAccess registryAccess) {
				// never reached: the craft is caught first (see KitchenMenuMixin) and cooked as a minigame
				return ItemStack.EMPTY;
			}
		});
	}

	/** The cook picked a minigame dish in the cooking table: cook it at a station nearby, or say what it needs. */
	public static void cook(ServerPlayer player, Dish dish) {
		ServerLevel level = player.level();
		BlockPos at = null;
		Station atStation = null;
		double best = Double.MAX_VALUE;
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-STATION_REACH, -2, -STATION_REACH), origin.offset(STATION_REACH, 3, STATION_REACH))) {
			Station station = Station.of(level, pos);
			if (station != null && station.allows(dish)) {
				double distance = pos.distSqr(origin);
				if (distance < best) {
					best = distance;
					at = pos.immutable();
					atStation = station;
				}
			}
		}
		player.closeContainer();
		if (at == null) {
			player.sendOverlayMessage(Component.translatable(noStationKey(dish), dishName(dish)));
			return;
		}
		Kitchen.startAt(player, at, atStation, dish);
	}

	/** A short "what you need" line for a dish with nowhere to cook it: the oven and the cookware to put on it. */
	private static String noStationKey(Dish dish) {
		return "message.jaymoskitchen.no_station." + switch (dish.method()) {
			case SEQUENCE, STIR -> "pot";
			case GRILL, CHOP -> "grill";
			case SEAR -> "sear";
			case OVEN, ROAST -> "oven";
			case SMOKE -> "smoker";
		};
	}

	private static Component dishName(Dish dish) {
		return Component.translatable(com.jaymoskitchen.ModItems.dish(dish).getDescriptionId());
	}

	public static @Nullable Dish dishOf(Object recipe) {
		return recipe instanceof MinigameRecipe minigame ? minigame.dish() : null;
	}
}
