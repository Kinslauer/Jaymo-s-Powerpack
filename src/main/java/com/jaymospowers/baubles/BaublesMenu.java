package com.jaymospowers.baubles;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The bauble slots' screen: the amulet, two rings and the belt down the left of the player, the head, body and charm down
 * the right, the player's inventory below. Drawn by the client's BaublesScreen.
 */
public class BaublesMenu extends AbstractContainerMenu {
	/** Where each of the seven slots sits (x, y), in {@link Baubles#SLOTS} order; and the player's inventory. */
	public static final int[][] SLOT_AT = {{8, 8}, {8, 26}, {8, 44}, {8, 62}, {152, 8}, {152, 26}, {152, 44}};
	public static final int INVENTORY_Y = 84;

	private final Container baubles;

	/** Client side. */
	public BaublesMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(Baubles.SIZE));
	}

	public BaublesMenu(int id, Inventory inventory, Container baubles) {
		super(Baubles.MENU, id);
		this.baubles = baubles;
		for (int i = 0; i < Baubles.SIZE; i++) {
			int slot = i;
			this.addSlot(new Slot(baubles, i, SLOT_AT[i][0], SLOT_AT[i][1]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return Baubles.fits(slot, stack);
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	@Override
	public boolean stillValid(Player player) {
		return this.baubles.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		if (index < Baubles.SIZE) {
			if (!this.moveItemStackTo(stack, Baubles.SIZE, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean placed = false;
			for (int i = 0; i < Baubles.SIZE && !placed; i++) {
				Slot target = this.slots.get(i);
				if (!target.hasItem() && target.mayPlace(stack)) {
					target.setByPlayer(stack.split(1));
					placed = true;
				}
			}
			if (!placed) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return moved;
	}
}
