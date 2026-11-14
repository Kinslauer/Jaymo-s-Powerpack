package com.jaymoskingdoms;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The harness screen's slots ({@link Harness}): the ten places of the harness round the wearer's figure, the helmet,
 * chestplate, leggings and boots beside them (so the whole harness is put on in one place), and the inventory below.
 * Each place takes only its own pieces, and only ones that fit how the wearer is dressed and that they may wear.
 */
public class HarnessMenu extends AbstractContainerMenu {
	/** Where each place of the harness is drawn ({@link Harness.Slot} order), then the four armour slots, then the inventory. */
	public static final int[][] AT = {{124, 54}, {124, 36}, {106, 36}, {106, 54}, {106, 72}, {26, 54}, {26, 72}, {26, 36}, {26, 18}, {106, 18}};
	public static final int ARMOUR_X = 8, ARMOUR_Y = 18, STEP = 18, INVENTORY_Y = 122;
	private static final int OWN = Harness.SLOTS + 4;
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final List<Identifier> ARMOUR_SPRITES = List.of(InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
		InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);

	private final Player player;

	/** Client side. */
	public HarnessMenu(int id, Inventory inventory) {
		this(id, inventory, inventory.player, new SimpleContainer(Harness.SLOTS));
	}

	/** Server side. */
	public HarnessMenu(int id, Inventory inventory, Player player) {
		this(id, inventory, player, new Worn(player));
	}

	private HarnessMenu(int id, Inventory inventory, Player player, Container harness) {
		super(Harness.MENU, id);
		this.player = player;
		for (Harness.Slot slot : Harness.Slot.values()) {
			int[] at = AT[slot.ordinal()];
			addSlot(new Slot(harness, slot.ordinal(), at[0], at[1]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return Harness.slotOf(stack) == slot && Harness.fits(player, slot) && HeraldryWear.mayWear(player, stack);
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		for (int i = 0; i < ARMOUR.length; i++) {
			addSlot(new ArmorSlot(inventory, player, ARMOUR[i], 39 - i, ARMOUR_X, ARMOUR_Y + i * STEP, ARMOUR_SPRITES.get(i)));
		}
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	@Override
	public boolean stillValid(Player player) {
		return player == this.player && player.isAlive();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < OWN) {
			if (!moveItemStackTo(stack, OWN, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean moved = false;
			for (int i = 0; i < OWN && !moved; i++) {
				Slot own = this.slots.get(i);
				if (!own.hasItem() && own.mayPlace(stack)) {
					moved = moveItemStackTo(stack, i, i + 1, false);
				}
			}
			if (!moved) {
				int hotbar = OWN + 27;
				if (index < hotbar ? !moveItemStackTo(stack, hotbar, this.slots.size(), false) : !moveItemStackTo(stack, OWN, hotbar, false)) {
					return ItemStack.EMPTY;
				}
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	/** The harness as a container, kept on the wearer ({@link Harness#WORN}). */
	private static final class Worn implements Container {
		private final Player player;

		Worn(Player player) {
			this.player = player;
		}

		@Override
		public int getContainerSize() {
			return Harness.SLOTS;
		}

		@Override
		public boolean isEmpty() {
			return Harness.worn(this.player).stream().allMatch(ItemStack::isEmpty);
		}

		@Override
		public ItemStack getItem(int slot) {
			return Harness.worn(this.player).get(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			ItemStack stack = getItem(slot);
			if (stack.isEmpty() || count <= 0) {
				return ItemStack.EMPTY;
			}
			setItem(slot, ItemStack.EMPTY);
			return stack.copy();
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack stack = getItem(slot);
			setItem(slot, ItemStack.EMPTY);
			return stack.copy();
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			Harness.set(this.player, Harness.Slot.values()[slot], stack.copy());
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(Player player) {
			return player == this.player;
		}

		@Override
		public void clearContent() {
			Harness.clear(this.player);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
