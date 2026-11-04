package com.jaymoskingdoms;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.level.Level;

/**
 * (0.7.497) A cut key with one to eight blank keys at a crafting table: that many copies of the key, its name kept. The
 * original comes back ({@link Locks}).
 */
public class KeyCopyRecipe extends CustomRecipe {
	static final KeyCopyRecipe INSTANCE = new KeyCopyRecipe();
	public static final RecipeSerializer<KeyCopyRecipe> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

	@Override
	public boolean matches(CraftingInput input, Level level) {
		boolean key = false;
		boolean blank = false;
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(Locks.KEY) && stack.has(Locks.LOCK_ID) && !key) {
				key = true;
			} else if (stack.is(Locks.BLANK_KEY)) {
				blank = true;
			} else {
				return false;
			}
		}
		return key && blank;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack source = ItemStack.EMPTY;
		int blanks = 0;
		for (int slot = 0; slot < input.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			if (stack.is(Locks.KEY)) {
				source = stack;
			} else if (stack.is(Locks.BLANK_KEY)) {
				blanks++;
			}
		}
		if (source.isEmpty() || blanks == 0) {
			return ItemStack.EMPTY;
		}
		return TransmuteRecipe.createWithOriginalComponents(new ItemStackTemplate(Locks.KEY), source, blanks - 1);
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		for (int slot = 0; slot < result.size(); slot++) {
			ItemStack stack = input.getItem(slot);
			ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
			if (remainder != null) {
				result.set(slot, remainder.create());
			} else if (stack.is(Locks.KEY)) {
				result.set(slot, stack.copyWithCount(1));
			}
		}
		return result;
	}

	@Override
	public RecipeSerializer<KeyCopyRecipe> getSerializer() {
		return SERIALIZER;
	}
}
