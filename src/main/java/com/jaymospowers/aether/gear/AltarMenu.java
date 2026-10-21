package com.jaymospowers.aether.gear;

import com.jaymospowers.aether.AetherItems;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The altar's screen: the input in the middle, the eight shard slots in a ring round it (the first N of them, clockwise from
 * the top, are the ones a recipe asks for), the output on the right. Drawn by the client's AltarScreen.
 */
public class AltarMenu extends AbstractContainerMenu {
	/** Where the slots sit: the input, the ring of shards (clockwise from the top), the output, and the player's inventory. */
	public static final int INPUT_X = 62, INPUT_Y = 40, OUTPUT_X = 140, OUTPUT_Y = 40, INVENTORY_Y = 102;
	public static final int[][] FUEL_AT = {{62, 22}, {80, 22}, {80, 40}, {80, 58}, {62, 58}, {44, 58}, {44, 40}, {44, 22}};
	private static final int OWN = AltarBlockEntity.SIZE;

	private final Container altar;
	private final ContainerData data;

	/** Client side. */
	public AltarMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(AltarBlockEntity.SIZE), new SimpleContainerData(3));
	}

	public AltarMenu(int id, Inventory inventory, Container altar, ContainerData data) {
		super(AetherGear.ALTAR_MENU, id);
		this.altar = altar;
		this.data = data;
		this.addSlot(new Slot(altar, AltarBlockEntity.INPUT, INPUT_X, INPUT_Y));
		for (int i = 0; i < AltarBlockEntity.FUEL_COUNT; i++) {
			this.addSlot(new Slot(altar, AltarBlockEntity.FUEL_FIRST + i, FUEL_AT[i][0], FUEL_AT[i][1]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.is(AetherItems.AMBROSIUM_SHARD);
				}
			});
		}
		this.addSlot(new Slot(altar, AltarBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		this.addDataSlots(data);
	}

	/** Work done and work needed (ticks), and how many shards the recipe takes (0 = nothing to do). */
	public int progress() {
		return this.data.get(0);
	}

	public int total() {
		return this.data.get(1);
	}

	public int needed() {
		return this.data.get(2);
	}

	@Override
	public boolean stillValid(Player player) {
		return this.altar.stillValid(player);
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
		} else if (stack.is(AetherItems.AMBROSIUM_SHARD)) {
			// shards go into the slot that holds the fewest
			int best = AltarBlockEntity.FUEL_FIRST;
			for (int i = AltarBlockEntity.FUEL_FIRST; i < AltarBlockEntity.FUEL_FIRST + AltarBlockEntity.FUEL_COUNT; i++) {
				if (this.slots.get(i).getItem().getCount() < this.slots.get(best).getItem().getCount()) {
					best = i;
				}
			}
			if (!this.moveItemStackTo(stack, best, best + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, AltarBlockEntity.INPUT, AltarBlockEntity.INPUT + 1, false)) {
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
