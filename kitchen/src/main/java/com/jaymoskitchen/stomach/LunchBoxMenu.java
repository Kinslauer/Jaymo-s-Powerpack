package com.jaymoskitchen.stomach;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** The lunch box's nine food slots (three by three, where a dispenser's are). Only food goes in. */
public class LunchBoxMenu extends AbstractContainerMenu {
	public static final int SLOT_COUNT = 9;
	private final Container container;

	/** Client side. */
	public LunchBoxMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(SLOT_COUNT));
	}

	public LunchBoxMenu(int id, Inventory inventory, Container container) {
		super(LunchBox.MENU, id);
		checkContainerSize(container, SLOT_COUNT);
		this.container = container;
		container.startOpen(inventory.player);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				this.addSlot(new Slot(container, col + row * 3, 62 + col * 18, 17 + row * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return LunchBox.fits(stack);
					}
				});
			}
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
		} else if (!LunchBox.fits(stack) || !this.moveItemStackTo(stack, 0, SLOT_COUNT, false)) {
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

	/** The box in a player's hand, opened: a view onto its contents, written back into the box on every change. */
	static final class Contents extends SimpleContainer {
		private final ItemStack box;
		private final InteractionHand hand;

		Contents(ItemStack box, InteractionHand hand) {
			super(SLOT_COUNT);
			this.box = box;
			this.hand = hand;
			box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItems());
		}

		@Override
		public void setChanged() {
			super.setChanged();
			box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
		}

		@Override
		public boolean stillValid(Player player) {
			return player.getItemInHand(hand) == box;
		}

		@Override
		public void stopOpen(ContainerUser user) {
			setChanged();
		}
	}
}
