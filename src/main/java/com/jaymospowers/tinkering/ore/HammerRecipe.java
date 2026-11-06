package com.jaymospowers.tinkering.ore;

import com.jaymospowers.power.zfighter.pod.PodParts;
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
 * (0.21.29) A shapeless crafting recipe that uses a hammer ({@code #jaymospowers:hammers}) instead of eating it, the way
 * GregTech's hand tools work: the hammer comes back worn by {@link OreWashing#HAMMER_WEAR}. A Tinkering hammer stops at
 * broken rather than vanishing (as it does everywhere else), and a broken one won't do. Type
 * {@code jaymospowers:hammer_crafting}, written like {@code minecraft:crafting_shapeless} (after Jaymo's Kitchen's
 * blade crafting).
 */
public class HammerRecipe extends ShapelessRecipe {
	public static final MapCodec<HammerRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
		CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result),
		Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(o -> o.ingredients)
	).apply(i, HammerRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, HammerRecipe> STREAM_CODEC = StreamCodec.composite(
		Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
		CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
		ItemStackTemplate.STREAM_CODEC, o -> o.result,
		Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), o -> o.ingredients,
		HammerRecipe::new);
	public static final RecipeSerializer<HammerRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final ItemStackTemplate result;
	private final List<Ingredient> ingredients;

	public HammerRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ItemStackTemplate result,
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
			if (stack.is(PodParts.HAMMERS) && ToolStats.isBroken(stack)) {
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
			if (!stack.is(PodParts.HAMMERS)) {
				continue;
			}
			ItemStack hammer = stack.copyWithCount(1);
			if (hammer.isDamageableItem()) {
				int worn = hammer.getDamageValue() + OreWashing.HAMMER_WEAR;
				if (hammer.getItem() instanceof TinkerTool) {
					hammer.setDamageValue(Math.min(worn, hammer.getMaxDamage()));
				} else if (worn >= hammer.getMaxDamage()) {
					hammer = ItemStack.EMPTY;
				} else {
					hammer.setDamageValue(worn);
				}
			}
			left.set(slot, hammer);
		}
		return left;
	}
}
