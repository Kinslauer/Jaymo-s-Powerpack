package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.ore.HammerRecipe;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponents;
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

/**
 * A shapeless recipe whose result keeps what the leather piece in the grid had ({@link LightArmour#carryOver}); a hammer
 * among the ingredients is worn, not eaten, as in {@link HammerRecipe}. Type {@code jaymospowers:light_armour_crafting},
 * written like {@code minecraft:crafting_shapeless}.
 */
public class LightArmourRecipe extends HammerRecipe {
	public static final MapCodec<LightArmourRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.made),
		Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(o -> o.needs)
	).apply(i, LightArmourRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, LightArmourRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
		ItemStackTemplate.STREAM_CODEC, o -> o.made,
		Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), o -> o.needs,
		LightArmourRecipe::new);
	public static final RecipeSerializer<LightArmourRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final ItemStackTemplate made;
	private final List<Ingredient> needs;

	public LightArmourRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ItemStackTemplate result,
		List<Ingredient> ingredients) {
		super(commonInfo, bookInfo, result, ingredients);
		this.made = result;
		this.needs = ingredients;
	}

	@Override
	public RecipeSerializer<ShapelessRecipe> getSerializer() {
		@SuppressWarnings("unchecked")
		RecipeSerializer<ShapelessRecipe> serializer = (RecipeSerializer<ShapelessRecipe>) (RecipeSerializer<?>) SERIALIZER;
		return serializer;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack out = super.assemble(input);
		for (ItemStack stack : input.items()) {
			if (stack.has(DataComponents.EQUIPPABLE)) {
				LightArmour.carryOver(stack, out);
				break;
			}
		}
		return out;
	}
}
