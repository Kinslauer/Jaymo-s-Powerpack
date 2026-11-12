package com.jaymospowers.gear.belt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

/** What a belt of throwables carries: a stack in each of its slots, and which one is picked to throw. */
public record BeltContents(List<ItemStackTemplate> stacks, int pick) {
	public static final BeltContents EMPTY = new BeltContents(List.of(), 0);
	public static final Codec<BeltContents> CODEC = RecordCodecBuilder.create(i -> i.group(
		ItemStackTemplate.CODEC.listOf().fieldOf("stacks").forGetter(BeltContents::stacks),
		Codec.INT.optionalFieldOf("pick", 0).forGetter(BeltContents::pick)).apply(i, BeltContents::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, BeltContents> STREAM_CODEC = StreamCodec.composite(
		ItemStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()), BeltContents::stacks,
		ByteBufCodecs.VAR_INT, BeltContents::pick,
		BeltContents::new);

	public BeltContents {
		stacks = List.copyOf(stacks);
		pick = stacks.isEmpty() ? 0 : Math.floorMod(pick, stacks.size());
	}

	public boolean isEmpty() {
		return stacks.isEmpty();
	}

	public @Nullable ItemStackTemplate selected() {
		return stacks.isEmpty() ? null : stacks.get(pick);
	}

	public int count() {
		int total = 0;
		for (ItemStackTemplate stack : stacks) {
			total += stack.count();
		}
		return total;
	}

	/** The next slot picked. */
	public BeltContents next() {
		return new BeltContents(stacks, pick + 1);
	}

	/** {@code stack} loaded as far as the belt's {@code slots} allow (what went in is taken off {@code stack}). */
	public BeltContents insert(ItemStack stack, int slots) {
		List<ItemStackTemplate> next = new ArrayList<>(stacks);
		for (int i = 0; i < next.size() && !stack.isEmpty(); i++) {
			ItemStackTemplate held = next.get(i);
			if (ItemStack.isSameItemSameComponents(held.create(), stack) && held.count() < stack.getMaxStackSize()) {
				int move = Math.min(stack.getCount(), stack.getMaxStackSize() - held.count());
				next.set(i, held.withCount(held.count() + move));
				stack.shrink(move);
			}
		}
		if (!stack.isEmpty() && next.size() < slots) {
			int move = Math.min(stack.getCount(), stack.getMaxStackSize());
			next.add(ItemStackTemplate.fromNonEmptyStack(stack, move));
			stack.shrink(move);
		}
		return new BeltContents(next, pick);
	}

	/** One off the picked stack (the slot goes when it runs out). */
	public BeltContents withoutOne() {
		return without(1);
	}

	/** {@code amount} off the picked stack (all of it at most). */
	public BeltContents without(int amount) {
		if (stacks.isEmpty()) {
			return this;
		}
		List<ItemStackTemplate> next = new ArrayList<>(stacks);
		ItemStackTemplate held = next.get(pick);
		if (held.count() > amount) {
			next.set(pick, held.withCount(held.count() - amount));
		} else {
			next.remove(pick);
		}
		return new BeltContents(next, pick);
	}
}
