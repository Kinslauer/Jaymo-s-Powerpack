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
 * <p>
 * A player's body carries everything they had ({@link CorpseEntity#inventory}, laid out as their own inventory): its
 * armour and off hand in the gear slots, the rest in {@link #PLAYER_POCKETS} pockets (main inventory, then the hotbar),
 * then what other mods left on it (harness, baubles and the like: {@link CorpseEntity#EXTRA_SLOTS}).
 * <p>
 * A mob's body shows the harness it wore ({@link CorpseHarness}) in those same last slots ({@link #HARNESS}), kept in the
 * body's look like its gear, so a take is written back at once: it is drawn without it, and nothing is taken twice.
 */
final class CorpseLoot implements Container {
	static final int GEAR = 7, POCKETS = 12, PLAYER_POCKETS = 36, SIZE = GEAR + PLAYER_POCKETS + CorpseEntity.EXTRA_SLOTS;
	/** Where the last slots start: a player's handed-over pieces, a mob's harness. */
	static final int HARNESS = GEAR + PLAYER_POCKETS;
	/** A player's body: the inventory slot each gear slot shows (head, chest, legs, feet, off hand), -1 for none. */
	private static final int[] PLAYER_GEAR = {39, 38, 37, 36, -1, -1, 40};
	/** How far away (blocks) the screen stays open. */
	private static final double REACH = 8.0;

	final CorpseEntity corpse;
	private final ItemStack[] gear;
	/** A mob's harness by place ({@link CorpseHarness}); no slots at all for a body that wore none. */
	private final ItemStack[] harness;
	/** The look this last wrote: changed by anything else (an arm cut off, a blood mark), the screen closes. */
	private String look;
	private int users;

	CorpseLoot(CorpseEntity corpse, ItemStack[] gear, ItemStack[] harness) {
		this.corpse = corpse;
		this.gear = gear;
		this.harness = harness;
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
		if (corpse.isPlayerCorpse()) {
			int at = inventorySlot(slot);
			return at >= 0 ? corpse.inventory().get(at) : ItemStack.EMPTY;
		}
		if (slot < GEAR) {
			return slot >= 0 ? gear[slot] : ItemStack.EMPTY;
		}
		if (slot >= HARNESS) {
			return slot - HARNESS < harness.length ? harness[slot - HARNESS] : ItemStack.EMPTY;
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
		if (corpse.isPlayerCorpse()) {
			int at = inventorySlot(slot);
			if (at >= 0) {
				corpse.inventory().set(at, stack);
			}
			return;
		}
		if (slot < GEAR) {
			gear[slot] = stack;
		} else if (slot >= HARNESS) {
			if (slot - HARNESS < harness.length) {
				harness[slot - HARNESS] = stack;
			}
		} else {
			List<ItemStack> pockets = corpse.pockets();
			int i = slot - GEAR;
			if (i < pockets.size() && i < POCKETS) {
				pockets.set(i, stack);
			}
		}
		setChanged();
	}

	/** A player's body: where in its inventory {@code slot} is (pockets: main inventory, then hotbar), or -1. */
	private static int inventorySlot(int slot) {
		if (slot < 0 || slot >= SIZE) {
			return -1;
		}
		if (slot < GEAR) {
			return PLAYER_GEAR[slot];
		}
		int i = slot - GEAR;
		return i < 27 ? 9 + i : i < PLAYER_POCKETS ? i - 27 : CorpseEntity.INVENTORY_SLOTS + i - PLAYER_POCKETS;
	}

	@Override
	public void setChanged() {
		if (corpse.isPlayerCorpse()) {
			// (a player's things aren't drawn from its look)
			return;
		}
		corpse.putGear(gear);
		if (harness.length > 0) {
			corpse.putHarness(harness);
		}
		look = corpse.look();
	}

	/** The body changed under it (a part cut off, the gear on it gone with the part): what's on it read again. */
	void reload() {
		if (!corpse.isPlayerCorpse()) {
			ItemStack[] now = corpse.wornGear();
			System.arraycopy(now, 0, gear, 0, Math.min(now.length, gear.length));
		}
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
