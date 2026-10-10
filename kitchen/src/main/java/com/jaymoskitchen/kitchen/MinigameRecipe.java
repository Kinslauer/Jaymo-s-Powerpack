package com.jaymoskitchen.kitchen;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Step;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

/**
 * A minigame dish as a recipe, so it's listed in Cooking for Blockheads' cooking table with its ingredients (see
 * CookingTableBridge). It never crafts in a crafting grid: picking it in the cooking table starts the dish's minigame
 * at a station nearby instead. Its ingredients and result come from the {@link Dish} it names.
 */
public class MinigameRecipe extends NormalCraftingRecipe {
	public static final MapCodec<MinigameRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
		Codec.STRING.fieldOf("dish").forGetter(o -> o.dish.id())
	).apply(i, MinigameRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, MinigameRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
		ByteBufCodecs.STRING_UTF8, o -> o.dish.id(),
		MinigameRecipe::new);
	public static final RecipeSerializer<MinigameRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final Dish dish;
	private final List<Ingredient> ingredients;

	public MinigameRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, String dish) {
		super(commonInfo, bookInfo);
		this.dish = Dish.valueOf(dish.toUpperCase());
		List<Ingredient> list = new ArrayList<>();
		for (Map.Entry<Step, Integer> need : this.dish.ingredients().entrySet()) {
			for (int n = 0; n < need.getValue(); n++) {
				list.add(Ingredient.of(need.getKey().item()));
			}
		}
		this.ingredients = List.copyOf(list);
	}

	public static void register() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, JaymosKitchen.id("minigame"), SERIALIZER);
	}

	public Dish dish() {
		return dish;
	}

	public List<Ingredient> ingredients() {
		return ingredients;
	}

	public ItemStackTemplate result() {
		return new ItemStackTemplate(ModItems.dish(dish));
	}

	@Override
	public RecipeSerializer<MinigameRecipe> getSerializer() {
		return SERIALIZER;
	}

	@Override
	protected PlacementInfo createPlacementInfo() {
		// a real placement (unplaceable recipes get no display id, and the cooking table finds recipes by display id);
		// it still never crafts, see matches
		return PlacementInfo.create(ingredients);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		// cooked, not crafted: only the cooking table's bridge (and the minigame) makes it
		return false;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return ItemStack.EMPTY;
	}

	@Override
	public List<RecipeDisplay> display() {
		return List.of(new ShapelessCraftingRecipeDisplay(
			ingredients.stream().map(Ingredient::display).toList(),
			new SlotDisplay.ItemStackSlotDisplay(result()),
			new SlotDisplay.ItemSlotDisplay(Items.CAMPFIRE)));
	}
}
