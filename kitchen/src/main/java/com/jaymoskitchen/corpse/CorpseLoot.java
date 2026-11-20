package com.jaymoskitchen.corpse;

import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a body has on it, as the loot screen ({@link CorpseLootMenu}) sees it, on the server: its gear (worn and held,
 * {@link #GEAR} slots, written back onto the body on every change so it's drawn without what's been taken) and its
 * pockets (the first {@link #POCKETS} stacks of its loot, read from the body's own list, so two people looking at once
 * see the same thing and nothing is taken twice). Only taken from, never put into. One per body while anyone's looking.
 */
final class CorpseLoot implements Container {
	static final int GEAR = 7, POCKETS = 12, SIZE = GEAR + POCKETS;
	/** How far away (blocks) the screen stays open. */
	private static final double REACH = 8.0;

	final CorpseEntity corpse;
	private final ItemStack[] gear;
	/** The look this last wrote: changed by anything else (an arm cut off, a blood mark), the screen closes. */
	private String look;
	private int users;

	CorpseLoot(CorpseEntity corpse, ItemStack[] gear) {
		this.corpse = corpse;
		this.gear = gear;
		this.look = corpse.look();
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (int i = 0; i < SIZE; i++) {
			if (!getItem(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		if (slot < GEAR) {
			return slot >= 0 ? gear[slot] : ItemStack.EMPTY;
		}
		List<ItemStack> pockets = corpse.pockets();
		int i = slot - GEAR;
		return i < pockets.size() && i < POCKETS ? pockets.get(i) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack stack = getItem(slot);
		if (stack.isEmpty() || count <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = stack.split(count);
		setChanged();
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack stack = getItem(slot);
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		setItem(slot, ItemStack.EMPTY);
		return stack;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot < 0) {
			return;
		}
		if (slot < GEAR) {
			gear[slot] = stack;
		} else {
			List<ItemStack> pockets = corpse.pockets();
			int i = slot - GEAR;
			if (i < pockets.size() && i < POCKETS) {
				pockets.set(i, stack);
			}
		}
		setChanged();
	}

	@Override
	public void setChanged() {
		corpse.putGear(gear);
		look = corpse.look();
	}

	@Override
	public boolean stillValid(Player player) {
		return !corpse.isRemoved() && corpse.look().equals(look) && corpse.carrier() == null && corpse.dragger() == null
			&& player.distanceToSqr(corpse) <= REACH * REACH;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return false;
	}

	@Override
	public void startOpen(ContainerUser user) {
		users++;
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (--users <= 0) {
			corpse.lootClosed(this);
		}
	}

	@Override
	public void clearContent() {
		// a body's things aren't cleared from here
	}
}
