package com.jaymospowers.ground;

import com.jaymospowers.tinkering.TinkerTool;
import com.jaymospowers.tinkering.ToolStats;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * A shapeless crafting recipe that hands back its tools ({@link StoneAge#GRID_TOOLS}): the knife that carves a brick form,
 * the two stone slabs paper is pressed between, the brick form clay is pressed into. A tool that wears comes back worn by
 * one (a Tinkering one stops at broken, and a broken one won't do, as for {@code tinkering/ore/HammerRecipe}). Type
 * {@code jaymospowers:tool_crafting}, written like {@code minecraft:crafting_shapeless}.
 */
public class GridToolRecipe extends ShapelessRecipe {
	public static final MapCodec<GridToolRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result),
		Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(o -> o.ingredients)
	).apply(i, GridToolRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, GridToolRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
		ItemStackTemplate.STREAM_CODEC, o -> o.result,
		Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), o -> o.ingredients,
		GridToolRecipe::new);
	public static final RecipeSerializer<GridToolRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final ItemStackTemplate result;
	private final List<Ingredient> ingredients;

	public GridToolRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ItemStackTemplate result,
		List<Ingredient> ingredients) {
		super(commonInfo, bookInfo, result, ingredients);
		this.result = result;
		this.ingredients = ingredients;
	}

	@Override
	public RecipeSerializer<ShapelessRecipe> getSerializer() {
		@SuppressWarnings("unchecked")
		RecipeSerializer<ShapelessRecipe> serializer = (RecipeSerializer<ShapelessRecipe>) (RecipeSerializer<?>) SERIALIZER;
		return serializer;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		if (!super.matches(input, level)) {
			return false;
		}
		for (ItemStack stack : input.items()) {
			if (stack.is(StoneAge.GRID_TOOLS) && stack.getItem() instanceof TinkerTool && ToolStats.isBroken(stack)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> left = CraftingRecipe.defaultCraftingReminder(input);
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (!stack.is(StoneAge.GRID_TOOLS)) {
				continue;
			}
			ItemStack tool = stack.copyWithCount(1);
			if (tool.isDamageableItem()) {
				int worn = tool.getDamageValue() + 1;
				if (tool.getItem() instanceof TinkerTool) {
					tool.setDamageValue(Math.min(worn, tool.getMaxDamage()));
				} else if (worn >= tool.getMaxDamage()) {
					tool = ItemStack.EMPTY;
				} else {
					tool.setDamageValue(worn);
				}
			}
			left.set(slot, tool);
		}
		return left;
	}
}
