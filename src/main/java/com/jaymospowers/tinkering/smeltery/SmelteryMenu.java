package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.Tinkering;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The smeltery's screen: one slot a block of space down the left (an item each), the molten pool in the middle (click
 * a liquid to send it to the bottom), a lava bucket goes in on the right and the empty bucket comes out under it.
 */
public class SmelteryMenu extends AbstractContainerMenu {
	/** Where the melting slots sit: three columns left of the screen. */
	public static final int SLOT_X = -56, SLOT_Y = 18, COLUMNS = 3;
	public static final int FUEL_X = 125, FUEL_Y = 46, BUCKET_X = 125, BUCKET_Y = 104;
	private static final int OWN = SmelteryEntity.SIZE;
	private final Container container;
	private final ContainerData data;
	public final BlockPos pos;

	/** Client side. */
	public SmelteryMenu(int id, Inventory inventory, BlockPos pos) {
		this(id, inventory, new SimpleContainer(SmelteryEntity.SIZE), new SimpleContainerData(SmelteryEntity.MAX_SLOTS + 2), pos);
	}

	public SmelteryMenu(int id, Inventory inventory, Container container, ContainerData data, BlockPos pos) {
		super(Tinkering.SMELTERY_MENU, id);
		this.container = container;
		this.data = data;
		this.pos = pos;
		for (int i = 0; i < SmelteryEntity.MAX_SLOTS; i++) {
			int index = i;
			addSlot(new Slot(container, i, SLOT_X + (i % COLUMNS) * 18, SLOT_Y + (i / COLUMNS) * 18) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return isActive() && Melting.of(stack) != null;
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}

				@Override
				public boolean isActive() {
					return index < slotsInUse();
				}
			});
		}
		addSlot(new Slot(container, SmelteryEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Items.LAVA_BUCKET);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addSlot(new Slot(container, SmelteryEntity.BUCKET_SLOT, BUCKET_X, BUCKET_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addStandardInventorySlots(inventory, 8, 138);
		addDataSlots(data);
	}

	/** How many melting slots the smeltery has (one a block of space). */
	public int slotsInUse() {
		return data.get(SmelteryEntity.MAX_SLOTS + 1);
	}

	public int fuel() {
		return data.get(SmelteryEntity.MAX_SLOTS);
	}

	/** A slot's melting progress, 0-100, or -1 if it won't melt. */
	public int progress(int slot) {
		return data.get(slot);
	}

	public @Nullable SmelteryEntity smeltery(net.minecraft.world.level.@Nullable Level level) {
		return level != null && level.getBlockEntity(pos) instanceof SmelteryEntity smeltery ? smeltery : null;
	}

	@Override
	public boolean clickMenuButton(Player player, int button) {
		if (container instanceof SmelteryEntity smeltery && button >= 0) {
			smeltery.toBottom(button);
		}
		return button >= 0;
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		int end = slots.size();
		if (index < OWN) {
			if (!moveItemStackTo(stack, OWN, end, true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(Items.LAVA_BUCKET)) {
			if (!moveItemStackTo(stack, SmelteryEntity.FUEL_SLOT, SmelteryEntity.FUEL_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (Melting.of(stack) != null) {
			if (!moveItemStackTo(stack, 0, Math.min(slotsInUse(), SmelteryEntity.MAX_SLOTS), false)) {
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
