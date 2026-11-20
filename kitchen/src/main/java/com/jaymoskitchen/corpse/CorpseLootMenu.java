package com.jaymoskitchen.corpse;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-20): "a screen that opens like a chest when you loot enemies". A body's loot screen
 * ({@link CorpseEntity#openLoot}): its armour down the left as the inventory's are, the body itself drawn beside it (the
 * screen draws it), its held things and any body armour beside that, its pockets on the right, and your inventory below.
 * Everything on the body can be taken, a slot at a time or shift-clicked; nothing can be put on it.
 */
public class CorpseLootMenu extends AbstractContainerMenu {
	/** Where the slots sit (as the screen draws them). */
	public static final int ARMOUR_X = 8, HANDS_X = 98, POCKETS_X = 118, TOP = 18, INVENTORY_Y = 106;
	/** The body's well, between the armour and the hands. */
	public static final int BODY_X = 26, BODY_Y = 17, BODY_W = 70, BODY_H = 72;
	private static final int ROW = 18, POCKET_COLUMNS = 3;
	/** Empty-slot outlines, in {@code CorpseEntity.GEAR} order (head, chest, legs, feet, body, main hand, off hand). */
	private static final @Nullable Identifier[] ICONS = {sprite("helmet"), sprite("chestplate"), sprite("leggings"), sprite("boots"),
		null, sprite("sword"), sprite("shield")};
	/** Where each gear slot sits, in the same order. */
	private static final int[][] GEAR_AT = {{ARMOUR_X, TOP}, {ARMOUR_X, TOP + ROW}, {ARMOUR_X, TOP + ROW * 2}, {ARMOUR_X, TOP + ROW * 3},
		{HANDS_X, TOP + ROW}, {HANDS_X, TOP + ROW * 2}, {HANDS_X, TOP + ROW * 3}};
	private static final int BODY_ARMOUR = 4;

	private final Container container;
	/** Client: the body, by its network id, for drawing (null if it isn't here). */
	public final @Nullable CorpseEntity corpse;

	/** Client side. */
	public CorpseLootMenu(int id, Inventory inventory, Integer corpseId) {
		this(id, inventory, new SimpleContainer(CorpseLoot.SIZE),
			inventory.player.level().getEntity(corpseId) instanceof CorpseEntity c ? c : null);
	}

	/** Server side. */
	CorpseLootMenu(int id, Inventory inventory, CorpseLoot loot) {
		this(id, inventory, loot, loot.corpse);
	}

	private CorpseLootMenu(int id, Inventory inventory, Container container, @Nullable CorpseEntity corpse) {
		super(Corpses.LOOT_MENU, id);
		checkContainerSize(container, CorpseLoot.SIZE);
		this.container = container;
		this.corpse = corpse;
		container.startOpen(inventory.player);
		for (int i = 0; i < CorpseLoot.GEAR; i++) {
			addSlot(new Taken(container, i, GEAR_AT[i][0], GEAR_AT[i][1], ICONS[i], i == BODY_ARMOUR));
		}
		for (int i = 0; i < CorpseLoot.POCKETS; i++) {
			addSlot(new Taken(container, CorpseLoot.GEAR + i, POCKETS_X + (i % POCKET_COLUMNS) * ROW, TOP + (i / POCKET_COLUMNS) * ROW, null, false));
		}
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	private static Identifier sprite(String name) {
		return Identifier.withDefaultNamespace("container/slot/" + name);
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index >= CorpseLoot.SIZE) {
			// nothing goes onto a body
			return ItemStack.EMPTY;
		}
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		if (!this.moveItemStackTo(stack, CorpseLoot.SIZE, this.slots.size(), true)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		slot.onTake(player, moved);
		return moved;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.stopOpen(player);
	}

	/** A slot on the body: take only. */
	private static final class Taken extends Slot {
		private final @Nullable Identifier icon;
		private final boolean onlyWhenFull;

		Taken(Container container, int index, int x, int y, @Nullable Identifier icon, boolean onlyWhenFull) {
			super(container, index, x, y);
			this.icon = icon;
			this.onlyWhenFull = onlyWhenFull;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public @Nullable Identifier getNoItemIcon() {
			return icon;
		}

		@Override
		public boolean isActive() {
			// a horse's or a wolf's body armour, shown only when it wears some
			return !onlyWhenFull || hasItem();
		}

		@Override
		public void onTake(Player player, ItemStack stack) {
			super.onTake(player, stack);
			if (container instanceof CorpseLoot loot && !stack.isEmpty()) {
				loot.corpse.lootTaken(player, getContainerSlot() < CorpseLoot.GEAR);
			}
		}
	}
}
