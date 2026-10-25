package com.jaymoskitchen.tanning;

import com.jaymoskitchen.climate.Climate;
import com.jaymoskitchen.climate.WaterskinItem;
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
 * (0.5.27) A shapeless crafting recipe that uses its tools instead of eating them: a work blade
 * ({@link Hides#WORK_BLADES}) comes back a little blunter, and a waterskin comes back a sip lighter (an empty one won't
 * do). Everything else is used up as usual (a water bucket leaves its bucket). Type {@code jaymoskitchen:blade_crafting},
 * written like {@code minecraft:crafting_shapeless}.
 */
public class BladeRecipe extends ShapelessRecipe {
	public static final MapCodec<BladeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result),
		Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(o -> o.ingredients)
	).apply(i, BladeRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, BladeRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
		ItemStackTemplate.STREAM_CODEC, o -> o.result,
		Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), o -> o.ingredients,
		BladeRecipe::new);
	public static final RecipeSerializer<BladeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final ItemStackTemplate result;
	private final List<Ingredient> ingredients;

	public BladeRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ItemStackTemplate result,
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
			if (stack.getItem() instanceof WaterskinItem && WaterskinItem.water(stack).sips() <= 0) {
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
			if (stack.is(Hides.WORK_BLADES)) {
				ItemStack blade = stack.copyWithCount(1);
				if (blade.isDamageableItem()) {
					blade.setDamageValue(blade.getDamageValue() + 1);
					if (blade.getDamageValue() >= blade.getMaxDamage()) {
						blade = ItemStack.EMPTY;
					}
				}
				left.set(slot, blade);
			} else if (stack.getItem() instanceof WaterskinItem) {
				ItemStack skin = stack.copyWithCount(1);
				WaterskinItem.Water water = WaterskinItem.water(skin);
				skin.set(Climate.WATERSKIN_WATER, water.sips() <= 1 ? WaterskinItem.Water.EMPTY
					: new WaterskinItem.Water(water.sips() - 1, water.clean()));
				left.set(slot, skin);
			}
		}
		return left;
	}
}
