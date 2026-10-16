package com.jaymospowers.backpack;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A backpack's four rows, shown in the plain chest screen (so the client needs nothing of its own). Its slots refuse
 * anything that can't go inside a container item: other backpacks, shulker boxes.
 */
public class BackpackMenu extends ChestMenu {
	private final boolean worn;

	private BackpackMenu(int id, Inventory inventory, Container container, boolean worn) {
		super(MenuType.GENERIC_9x4, id, inventory, container, Backpack.ROWS);
		this.worn = worn;
		for (int i = 0; i < Backpack.ROWS * 9; i++) {
			Slot old = this.slots.get(i);
			Slot slot = new Slot(container, old.getContainerSlot(), old.x, old.y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return fits(stack);
				}
			};
			slot.index = i;
			this.slots.set(i, slot);
		}
	}

	/** The menu for the backpack on this player's back. */
	static BackpackMenu worn(int id, Inventory inventory, Container container) {
		return new BackpackMenu(id, inventory, container, true);
	}

	/** The menu for a set-down backpack. */
	static BackpackMenu placed(int id, Inventory inventory, Container container) {
		return new BackpackMenu(id, inventory, container, false);
	}

	/** Whether this is the worn backpack's menu. */
	boolean worn() {
		return this.worn;
	}

	/** Whether {@code stack} may go in a backpack. */
	static boolean fits(ItemStack stack) {
		return stack.getItem().canFitInsideContainerItems();
	}
}
