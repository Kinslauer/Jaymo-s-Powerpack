package com.jaymoskitchen.delight;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * (From Farmer's Delight's {@code ItemStackWrapper}, MIT.) An item stack that can sit in a data component: stacks have
 * no {@code equals} of their own, so this compares them by item and components.
 */
public final class StackBox {
	public static final StackBox EMPTY = new StackBox(ItemStack.EMPTY);
	public static final Codec<StackBox> CODEC = ItemStack.OPTIONAL_CODEC.xmap(StackBox::new, StackBox::stack);
	public static final StreamCodec<RegistryFriendlyByteBuf, StackBox> STREAM_CODEC =
		ItemStack.OPTIONAL_STREAM_CODEC.map(StackBox::new, StackBox::stack);

	private final ItemStack stack;
	private final int hash;

	public StackBox(ItemStack stack) {
		this.stack = stack;
		this.hash = ItemStack.hashItemAndComponents(stack);
	}

	public ItemStack stack() {
		return stack;
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof StackBox box && ItemStack.matches(stack, box.stack);
	}
}
