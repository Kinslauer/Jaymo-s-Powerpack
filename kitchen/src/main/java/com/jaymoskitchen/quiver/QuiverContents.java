package com.jaymoskitchen.quiver;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

/**
 * What's in a quiver: stacks of arrows, bottom first. The last stack is the top of the quiver, the one that's
 * drawn from and the one taken out first. Holds up to {@link #CAPACITY} arrows of any type.
 */
public record QuiverContents(List<ItemStackTemplate> arrows) {
	public static final int CAPACITY = 32;
	public static final QuiverContents EMPTY = new QuiverContents(List.of());
	public static final Codec<QuiverContents> CODEC = ItemStackTemplate.CODEC.listOf()
		.xmap(QuiverContents::new, QuiverContents::arrows);
	public static final StreamCodec<RegistryFriendlyByteBuf, QuiverContents> STREAM_CODEC = ItemStackTemplate.STREAM_CODEC
		.apply(ByteBufCodecs.list())
		.map(QuiverContents::new, QuiverContents::arrows);

	public QuiverContents {
		arrows = List.copyOf(arrows);
	}

	public static boolean canHold(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ItemTags.ARROWS);
	}

	public int count() {
		int total = 0;
		for (ItemStackTemplate stack : arrows) {
			total += stack.count();
		}
		return total;
	}

	public int space() {
		return Math.max(0, CAPACITY - count());
	}

	public boolean isEmpty() {
		return arrows.isEmpty();
	}

	public @Nullable ItemStackTemplate top() {
		return arrows.isEmpty() ? null : arrows.get(arrows.size() - 1);
	}

	/** Adds {@code amount} of {@code stack}'s arrows on top, merging with the top stack when they're the same kind. */
	public QuiverContents withAdded(ItemStack stack, int amount) {
		if (amount <= 0) {
			return this;
		}
		List<ItemStackTemplate> next = new ArrayList<>(arrows);
		ItemStackTemplate top = top();
		if (top != null && ItemStack.isSameItemSameComponents(top.create(), stack)) {
			next.set(next.size() - 1, top.withCount(top.count() + amount));
		} else {
			next.add(ItemStackTemplate.fromNonEmptyStack(stack, amount));
		}
		return new QuiverContents(next);
	}

	/** Takes {@code amount} arrows off the stack at {@code index}, dropping the stack when it runs out. */
	public QuiverContents withRemoved(int index, int amount) {
		if (index < 0 || index >= arrows.size() || amount <= 0) {
			return this;
		}
		List<ItemStackTemplate> next = new ArrayList<>(arrows);
		ItemStackTemplate stack = next.get(index);
		if (stack.count() > amount) {
			next.set(index, stack.withCount(stack.count() - amount));
		} else {
			next.remove(index);
		}
		return new QuiverContents(next);
	}

	/** Every arrow, one entry each, bottom first (for drawing them on the player's back). */
	public List<ItemStack> eachArrow() {
		List<ItemStack> each = new ArrayList<>(count());
		for (ItemStackTemplate stack : arrows) {
			ItemStack single = stack.create();
			single.setCount(1);
			for (int i = 0; i < stack.count(); i++) {
				each.add(single);
			}
		}
		return each;
	}
}
