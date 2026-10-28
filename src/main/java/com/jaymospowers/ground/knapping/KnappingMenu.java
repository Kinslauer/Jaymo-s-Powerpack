package com.jaymospowers.ground.knapping;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The knapping grid. Server-authoritative: the client only sends which cell it clicked ({@link #clickMenuButton}); the
 * server keeps the chips (one bit a cell, synced as a data slot) and the rocks, and fills the output when what is left
 * is a shape ({@link Knapping#SHAPES}).
 */
public class KnappingMenu extends AbstractContainerMenu {
	public static final int ROCK = 0, RESULT = 1;
	/** The button that starts over on a fresh rock (the chipped one is spoiled). */
	public static final int START_OVER = Knapping.CELLS;
	/** One past the player's inventory slots. */
	private static final int PLAYER_END = 2 + 36;
	/** Pitch range of the chip sound. */
	private static final float PITCH_LOW = 0.9F, PITCH_RANGE = 0.5F;

	private final Inventory inventory;
	private final boolean server;
	private final DataSlot chips = DataSlot.standalone();
	private final Container rocks = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			super.setChanged();
			KnappingMenu.this.slotsChanged(this);
		}
	};
	private final ResultContainer result = new ResultContainer();

	/** Client side. */
	public KnappingMenu(int id, Inventory inventory) {
		this(id, inventory, ItemStack.EMPTY);
	}

	/** Server side: with the rocks the player held. */
	public KnappingMenu(int id, Inventory inventory, ItemStack held) {
		super(Knapping.MENU, id);
		this.inventory = inventory;
		this.server = !inventory.player.level().isClientSide();
		this.chips.set(Knapping.FULL);
		this.addSlot(new Slot(rocks, 0, 14, 34) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return Knapping.isRock(stack);
			}
		});
		this.addSlot(new Slot(result, 0, 142, 34) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack carried) {
				if (!player.getAbilities().instabuild) {
					rocks.removeItem(0, 1);
				}
				chips.set(Knapping.FULL);
				sound(player, SoundEvents.STONE_BREAK, 1.0F);
				super.onTake(player, carried);
				update();
			}
		});
		this.addStandardInventorySlots(inventory, 8, 84);
		this.addDataSlot(chips);
		if (server && !held.isEmpty()) {
			rocks.setItem(0, held);
		}
	}

	/** Bit {@code cell} (row * 3 + column) is set while that chip is still on. */
	public int chips() {
		return chips.get();
	}

	public boolean chipOn(int cell) {
		return (chips.get() >> cell & 1) != 0;
	}

	public ItemStack rock() {
		return rocks.getItem(0);
	}

	@Override
	public boolean clickMenuButton(Player player, int button) {
		if (!server || rocks.getItem(0).isEmpty()) {
			return false;
		}
		if (button == START_OVER) {
			if (chips.get() == Knapping.FULL) {
				return false;
			}
			spoil(player);
			return true;
		}
		if (button < 0 || button >= Knapping.CELLS || !chipOn(button)) {
			return false;
		}
		chips.set(chips.get() & ~(1 << button));
		sound(player, SoundEvents.STONE_HIT, PITCH_LOW + player.getRandom().nextFloat() * PITCH_RANGE);
		if (chips.get() == 0) {
			// every chip gone and no head to show for it
			spoil(player);
		} else {
			update();
		}
		return true;
	}

	/** The rock is ruined: one goes, and the next starts whole. */
	private void spoil(Player player) {
		if (!player.getAbilities().instabuild) {
			rocks.removeItem(0, 1);
		}
		chips.set(Knapping.FULL);
		sound(player, SoundEvents.STONE_BREAK, 0.8F);
		update();
	}

	private void sound(Player player, net.minecraft.sounds.SoundEvent event, float pitch) {
		if (server) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), event, SoundSource.PLAYERS, 0.8F, pitch);
		}
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (container == rocks) {
			update();
		}
	}

	private void update() {
		if (!server) {
			return;
		}
		ItemStack rock = rocks.getItem(0);
		ItemStack out = ItemStack.EMPTY;
		if (!rock.isEmpty()) {
			Knapping.Shape shape = Knapping.match(chips.get(), Knapping.material(rock));
			ItemStack made = shape == null ? null : shape.result(Knapping.material(rock));
			if (made != null) {
				out = made;
			}
		}
		result.setItem(0, out);
		broadcastChanges();
	}

	@Override
	public boolean stillValid(Player player) {
		return player.isAlive();
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
			ItemStack out = stack.copy();
			player.getInventory().placeItemBackInInventory(out, Prediction.SERVER_ONLY);
			slot.onTake(player, moved);
			return ItemStack.EMPTY;
		}
		if (index == ROCK) {
			if (!this.moveItemStackTo(stack, 2, PLAYER_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!Knapping.isRock(stack) || !this.moveItemStackTo(stack, ROCK, ROCK + 1, false)) {
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
		this.clearContainer(player, rocks);
	}
}
