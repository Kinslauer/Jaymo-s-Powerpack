package com.jaymospowers.tinkering.fletching;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The Fletching Station's menu: head, shaft and fletching, an optional lingering potion, and the arrows they make ({@link Fletching#craft}). */
public class FletchingMenu extends AbstractContainerMenu {
	public static final int HEAD = 0, SHAFT = 1, FLETCHING = 2, POTION = 3, RESULT = 4;
	private static final int INPUTS = 4;
	/** One past the player's inventory slots. */
	private static final int PLAYER_END = RESULT + 1 + 36;
	/** Where the slots sit (x, y) in the screen. */
	public static final int[][] SLOT_AT = {{32, 18}, {32, 38}, {32, 58}, {84, 38}, {142, 38}};
	/** Most arrows one shift-click takes in one go. */
	private static final int QUICK_SETS = 16;

	private final ContainerLevelAccess access;
	private final Container inputs = new SimpleContainer(INPUTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			FletchingMenu.this.slotsChanged(this);
		}
	};
	private final ResultContainer result = new ResultContainer();

	/** Client side. */
	public FletchingMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public FletchingMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(Fletching.MENU, id);
		this.access = access;
		this.addSlot(new Slot(inputs, HEAD, SLOT_AT[HEAD][0], SLOT_AT[HEAD][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return Fletching.isHead(stack);
			}
		});
		this.addSlot(new Slot(inputs, SHAFT, SLOT_AT[SHAFT][0], SLOT_AT[SHAFT][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return Fletching.isShaft(stack);
			}
		});
		this.addSlot(new Slot(inputs, FLETCHING, SLOT_AT[FLETCHING][0], SLOT_AT[FLETCHING][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return Fletching.isFletching(stack);
			}
		});
		this.addSlot(new Slot(inputs, POTION, SLOT_AT[POTION][0], SLOT_AT[POTION][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return Fletching.isPotion(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		this.addSlot(new Slot(result, 0, SLOT_AT[RESULT][0], SLOT_AT[RESULT][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack carried) {
				Fletching.Craft craft = current();
				if (craft != null) {
					inputs.removeItem(HEAD, craft.head());
					inputs.removeItem(SHAFT, craft.shaft());
					inputs.removeItem(FLETCHING, craft.fletching());
					if (craft.potion() > 0) {
						inputs.removeItem(POTION, craft.potion());
						player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), Prediction.SERVER_ONLY);
					}
					access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.8F, 1.3F));
				}
				super.onTake(player, carried);
				update();
			}
		});
		this.addStandardInventorySlots(inventory, 8, 84);
	}

	private Fletching.Craft current() {
		return Fletching.craft(inputs.getItem(HEAD), inputs.getItem(SHAFT), inputs.getItem(FLETCHING), inputs.getItem(POTION));
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (container == inputs) {
			update();
		}
	}

	private void update() {
		Fletching.Craft craft = current();
		result.setItem(0, craft == null ? ItemStack.EMPTY : craft.result());
		broadcastChanges();
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, Blocks.FLETCHING_TABLE);
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return target.container != result && super.canTakeItemForPickAll(carried, target);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		if (index == RESULT) {
			// make as many sets as the inputs hold
			for (int made = 0; made < QUICK_SETS && slot.hasItem(); made++) {
				ItemStack one = slot.getItem().copy();
				player.getInventory().placeItemBackInInventory(one, Prediction.SERVER_ONLY);
				slot.onTake(player, one);
			}
			return ItemStack.EMPTY;
		}
		if (index < RESULT) {
			if (!this.moveItemStackTo(stack, RESULT + 1, PLAYER_END, true)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean placed = false;
			for (int i = 0; i < INPUTS && !placed; i++) {
				placed = this.slots.get(i).mayPlace(stack) && this.moveItemStackTo(stack, i, i + 1, false);
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

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.clearContainer(player, inputs);
	}
}
