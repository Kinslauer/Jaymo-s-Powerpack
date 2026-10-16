package com.jaymospowers.armory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The bomb glider's hold: its fuel slot (blaze rods or a lava bucket) and its rack of dynamite (WeaponMod's, or TNT),
 * with how long the fuel in the burner has left. Drawn by the client's BombGliderScreen.
 */
public class BombGliderMenu extends AbstractContainerMenu {
	/** Where the two slots sit, and the player's inventory under them. */
	public static final int FUEL_X = 44, FUEL_Y = 35, RACK_X = 116, RACK_Y = 35, INVENTORY_Y = 84;
	/** The glider's own slots come first in the menu. */
	private static final int OWN = 2;
	/** Farther than this (blocks) from a glider you aren't riding, the hold closes. */
	private static final double REACH = 8.0;

	private final Container hold;
	private final ContainerData data;
	private final @Nullable BombGlider glider;

	/** Client side. */
	public BombGliderMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(OWN), new SimpleContainerData(2), null);
	}

	public BombGliderMenu(int id, Inventory inventory, Container hold, ContainerData data, @Nullable BombGlider glider) {
		super(ArmoryEntities.BOMB_GLIDER_MENU, id);
		this.hold = hold;
		this.data = data;
		this.glider = glider;
		this.addSlot(new Slot(hold, BombGlider.FUEL_SLOT, FUEL_X, FUEL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return BombGlider.isFuel(stack);
			}
		});
		this.addSlot(new Slot(hold, BombGlider.DYNAMITE_SLOT, RACK_X, RACK_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return BombGlider.isExplosive(stack);
			}

			@Override
			public int getMaxStackSize() {
				return BombGlider.MAX_DYNAMITE;
			}
		});
		this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		this.addDataSlots(data);
	}

	/** Ticks the burner has left of what it's burning, and how long that lasts in all. */
	public int burn() {
		return this.data.get(0);
	}

	public int burnMax() {
		return this.data.get(1);
	}

	@Override
	public boolean stillValid(Player player) {
		if (this.glider == null) {
			return true;
		}
		return this.glider.isAlive() && (this.glider.hasPassenger(player) || player.distanceToSqr(this.glider) < REACH * REACH);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		int end = this.slots.size();
		if (index < OWN) {
			if (!this.moveItemStackTo(stack, OWN, end, true)) {
				return ItemStack.EMPTY;
			}
		} else if (BombGlider.isFuel(stack)) {
			if (!this.moveItemStackTo(stack, BombGlider.FUEL_SLOT, BombGlider.FUEL_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (BombGlider.isExplosive(stack)) {
			if (!this.moveItemStackTo(stack, BombGlider.DYNAMITE_SLOT, BombGlider.DYNAMITE_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return moved;
	}
}
