package com.jaymoskitchen.horse;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The horse inventory's chest slots while it wears saddlebags: a view onto the bags' own contents, written back
 * into the bags on every change. Shared by everyone looking into the same bags.
 */
public final class SaddlebagContainer implements Container {
	/** Three rows of five, like a donkey's chest. */
	public static final int COLUMNS = 5, SIZE = COLUMNS * 3;
	private static final Map<ItemStack, SaddlebagContainer> OPEN = new WeakHashMap<>();

	private final AbstractHorse horse;
	private final ItemStack bag;
	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int viewers;

	private SaddlebagContainer(AbstractHorse horse, ItemStack bag) {
		this.horse = horse;
		this.bag = bag;
	}

	public static SaddlebagContainer of(AbstractHorse horse, ItemStack bag) {
		SaddlebagContainer container = OPEN.computeIfAbsent(bag, b -> new SaddlebagContainer(horse, b));
		if (container.viewers == 0) {
			// nobody's looking: re-read the bags in case they were changed some other way
			for (int i = 0; i < SIZE; i++) {
				container.items.set(i, ItemStack.EMPTY);
			}
			bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(container.items);
		}
		return container;
	}

	/** Still on the horse: once the bags are taken off, the slots close. */
	public boolean attached() {
		return horse.isAlive() && horse.getItemBySlot(EquipmentSlot.BODY) == bag;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, count);
		if (!removed.isEmpty()) {
			setChanged();
		}
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack removed = ContainerHelper.takeItem(items, slot);
		setChanged();
		return removed;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.limitSize(getMaxStackSize(stack));
		setChanged();
	}

	@Override
	public void setChanged() {
		if (attached()) {
			bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return attached();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		// no bags inside bags
		return !stack.is(Saddlebags.SADDLEBAG);
	}

	@Override
	public void startOpen(ContainerUser user) {
		viewers++;
	}

	@Override
	public void stopOpen(ContainerUser user) {
		viewers = Math.max(0, viewers - 1);
		setChanged();
	}

	@Override
	public void clearContent() {
		items.clear();
		setChanged();
	}
}
