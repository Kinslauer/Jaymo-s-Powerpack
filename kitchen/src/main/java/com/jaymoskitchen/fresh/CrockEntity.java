package com.jaymoskitchen.fresh;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** What a pickling crock holds ({@link CrockBlock}): up to {@link Curing#SLOTS} stacks, each steeping since the tick it went in, and the liquid left in batches. */
public class CrockEntity extends BlockEntity {
	private final NonNullList<ItemStack> items = NonNullList.withSize(Curing.SLOTS, ItemStack.EMPTY);
	/** The game time each stack went in; -1 when empty or cured already. */
	private final long[] since = new long[Curing.SLOTS];
	private int charges;

	public CrockEntity(BlockPos pos, BlockState state) {
		super(Curing.CROCK_ENTITY, pos, state);
		java.util.Arrays.fill(since, -1L);
	}

	private String method() {
		return ((CrockBlock) getBlockState().getBlock()).method;
	}

	int charges() {
		return charges;
	}

	/** Pours a bottle in; false if it's full. */
	boolean fill() {
		if (charges >= Curing.MOST_CHARGES) {
			return false;
		}
		charges = Math.min(Curing.MOST_CHARGES, charges + Curing.PER_BOTTLE);
		setChanged();
		return true;
	}

	/** Puts {@code food}'s stack in using up a batch of the liquid: what moved (empty if it can't). */
	ItemStack put(ItemStack food) {
		if (charges <= 0 || level == null) {
			return ItemStack.EMPTY;
		}
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).isEmpty()) {
				ItemStack moved = food.copy();
				items.set(i, moved);
				since[i] = level.getGameTime();
				charges--;
				setChanged();
				return moved.copy();
			}
		}
		return ItemStack.EMPTY;
	}

	/** Takes the last stack out. */
	ItemStack takeLast() {
		for (int i = items.size() - 1; i >= 0; i--) {
			if (!items.get(i).isEmpty()) {
				ItemStack out = items.get(i);
				items.set(i, ItemStack.EMPTY);
				since[i] = -1L;
				setChanged();
				return out;
			}
		}
		return ItemStack.EMPTY;
	}

	/** A line on the liquid left and how many stacks are in. */
	Component status() {
		int held = 0, ready = 0;
		for (int i = 0; i < items.size(); i++) {
			if (!items.get(i).isEmpty()) {
				held++;
				if (items.get(i).has(Curing.CURED)) {
					ready++;
				}
			}
		}
		return Component.translatable("message.jaymoskitchen.crock.status", charges, held, ready);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CrockEntity crock) {
		if (level.getGameTime() % 100L != 0L) {
			return;
		}
		boolean changed = false;
		for (int i = 0; i < crock.items.size(); i++) {
			ItemStack stack = crock.items.get(i);
			if (!stack.isEmpty() && crock.since[i] >= 0L && level.getGameTime() - crock.since[i] >= Curing.CURE_TICKS) {
				Curing.cure(stack, crock.method());
				crock.since[i] = -1L;
				changed = true;
			}
		}
		if (changed) {
			crock.setChanged();
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropContents(level, pos, items);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.clear();
		ContainerHelper.loadAllItems(input, items);
		charges = input.getIntOr("charges", 0);
		for (int i = 0; i < since.length; i++) {
			since[i] = input.getLongOr("since" + i, -1L);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items, true);
		output.putInt("charges", charges);
		for (int i = 0; i < since.length; i++) {
			output.putLong("since" + i, since[i]);
		}
	}
}
