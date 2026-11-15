package com.jaymospowers.ground;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** A clay bucket of milk ({@link ClayBucketItem}): drunk like a milk bucket, it leaves the clay bucket a use more worn. */
public class ClayMilkBucketItem extends Item {
	public ClayMilkBucketItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		ItemStack full = stack.copy();
		ItemStack left = super.finishUsingItem(stack, level, user);
		if (user instanceof Player player && player.hasInfiniteMaterials()) {
			return left;
		}
		ItemStack empty = ClayBucketItem.emptied(full, level, user);
		if (left.isEmpty()) {
			return empty;
		}
		if (!empty.isEmpty() && user instanceof Player player) {
			player.getInventory().placeItemBackInInventory(empty, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		return left;
	}

	@Override
	public @Nullable ItemStackTemplate getCraftingRemainder(ItemStack stack) {
		ItemStack empty = ClayBucketItem.emptied(stack, null, null);
		return empty.isEmpty() ? null : ItemStackTemplate.fromNonEmptyStack(empty);
	}
}
