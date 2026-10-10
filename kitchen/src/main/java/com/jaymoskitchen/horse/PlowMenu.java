package com.jaymoskitchen.horse;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The plow's three hoe slots, one per arm (layout from NiftyCarts' plow screen). Only hoes go in. */
public class PlowMenu extends AbstractContainerMenu {
	public static final int SLOT_COUNT = 3;
	private final Container container;

	/** Client side. */
	public PlowMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(SLOT_COUNT));
	}

	public PlowMenu(int id, Inventory inventory, Container container) {
		super(Saddlebags.PLOW_MENU, id);
		checkContainerSize(container, SLOT_COUNT);
		this.container = container;
		container.startOpen(inventory.player);
		int[][] at = {{57, 24}, {80, 17}, {103, 24}};
		for (int i = 0; i < SLOT_COUNT; i++) {
			this.addSlot(new Slot(container, i, at[i][0], at[i][1]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.is(ItemTags.HOES);
				}
			});
		}
		this.addStandardInventorySlots(inventory, 8, 84);
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		if (index < SLOT_COUNT) {
			if (!this.moveItemStackTo(stack, SLOT_COUNT, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, 0, SLOT_COUNT, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return moved;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.stopOpen(player);
	}
}
