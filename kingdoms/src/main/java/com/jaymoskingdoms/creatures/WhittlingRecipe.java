package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * A shapeless crafting recipe with a knife in it that hands the knife back worn by one (it breaks if that was its last):
 * sticks whittled into punji stakes ({@link PunjiBlock}). Type {@code jaymoskingdoms:whittling}, written like
 * {@code minecraft:crafting_shapeless}; being a shapeless recipe, JEI shows it on its crafting page as it is.
 */
public class WhittlingRecipe extends ShapelessRecipe {
	/** The knives that whittle: Kitchen's, Powers' first knives, and anything tagged a knife. */
	public static final TagKey<Item> KNIVES = TagKey.create(Registries.ITEM, JaymosKingdoms.id("whittling_knives"));
	public static final MapCodec<WhittlingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result),
		Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(o -> o.ingredients)
	).apply(i, WhittlingRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, WhittlingRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
		ItemStackTemplate.STREAM_CODEC, o -> o.result,
		Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), o -> o.ingredients,
		WhittlingRecipe::new);
	public static final RecipeSerializer<WhittlingRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final ItemStackTemplate result;
	private final List<Ingredient> ingredients;

	public WhittlingRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ItemStackTemplate result, List<Ingredient> ingredients) {
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
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> left = CraftingRecipe.defaultCraftingReminder(input);
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (!stack.is(KNIVES)) {
				continue;
			}
			ItemStack knife = stack.copyWithCount(1);
			if (knife.isDamageableItem()) {
				int worn = knife.getDamageValue() + 1;
				if (worn >= knife.getMaxDamage()) {
					knife = ItemStack.EMPTY;
				} else {
					knife.setDamageValue(worn);
				}
			}
			left.set(slot, knife);
		}
		return left;
	}
}
