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
 * <p>
 * Andy: "when players are killed, they should keep the loot on their corpse like npcs do, now accessed with a right
 * click". A player's body has all they carried: the screen is wider, their armour and off hand where a mob's are, and
 * their inventory in a 9 by 4 grid beside the body (the hotbar its bottom row), then what else it was left wearing (harness,
 * baubles, web shooters, hidden blades) in a block beside that, your own inventory centred below.
 */
public class CorpseLootMenu extends AbstractContainerMenu {
	/** Where the slots sit (as the screen draws them). */
	public static final int ARMOUR_X = 8, HANDS_X = 98, POCKETS_X = 118, TOP = 18, INVENTORY_Y = 106;
	/** A player's body: the screen's width, and where its inventory grid and your own inventory start. */
	public static final int PLAYER_POCKETS_X = 120, PLAYER_EXTRAS_X = PLAYER_POCKETS_X + 162 + 4, PLAYER_EXTRAS_COLUMNS = 6,
		PLAYER_WIDTH = PLAYER_EXTRAS_X + PLAYER_EXTRAS_COLUMNS * 18 + 8,
		PLAYER_INVENTORY_X = (PLAYER_WIDTH - 162) / 2;
	/** The body's well, between the armour and the hands. */
	public static final int BODY_X = 26, BODY_Y = 17, BODY_W = 70, BODY_H = 72;
	private static final int ROW = 18, POCKET_COLUMNS = 3;
	/** Empty-slot outlines, in {@code CorpseEntity.GEAR} order (head, chest, legs, feet, body, main hand, off hand). */
	private static final @Nullable Identifier[] ICONS = {sprite("helmet"), sprite("chestplate"), sprite("leggings"), sprite("boots"),
		null, sprite("sword"), sprite("shield")};
	/** Where each gear slot sits, in the same order. */
	private static final int[][] GEAR_AT = {{ARMOUR_X, TOP}, {ARMOUR_X, TOP + ROW}, {ARMOUR_X, TOP + ROW * 2}, {ARMOUR_X, TOP + ROW * 3},
		{HANDS_X, TOP + ROW}, {HANDS_X, TOP + ROW * 2}, {HANDS_X, TOP + ROW * 3}};
	private static final int BODY_ARMOUR = 4, MAIN_HAND = 5;

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
		boolean player = isPlayer(corpse);
		for (int i = 0; i < CorpseLoot.GEAR; i++) {
			// (a player's held item is in its hotbar row, and a player wears no body armour)
			boolean shown = !player || i != MAIN_HAND && i != BODY_ARMOUR;
			addSlot(new Taken(container, i, GEAR_AT[i][0], GEAR_AT[i][1], ICONS[i], i == BODY_ARMOUR, shown));
		}
		for (int i = 0; i < CorpseLoot.PLAYER_POCKETS; i++) {
			if (player) {
				addSlot(new Taken(container, CorpseLoot.GEAR + i, PLAYER_POCKETS_X + (i % 9) * ROW, TOP + (i / 9) * ROW, null, false, true));
			} else {
				// a mob's body has only the first few pockets
				int shown = Math.min(i, CorpseLoot.POCKETS - 1);
				addSlot(new Taken(container, CorpseLoot.GEAR + i, POCKETS_X + (shown % POCKET_COLUMNS) * ROW, TOP + (shown / POCKET_COLUMNS) * ROW,
					null, false, i < CorpseLoot.POCKETS));
			}
		}
		for (int i = 0; i < com.jaymoskitchen.corpse.CorpseEntity.EXTRA_SLOTS; i++) {
			addSlot(new Taken(container, CorpseLoot.GEAR + CorpseLoot.PLAYER_POCKETS + i, PLAYER_EXTRAS_X + (i % PLAYER_EXTRAS_COLUMNS) * ROW,
				TOP + (i / PLAYER_EXTRAS_COLUMNS) * ROW, null, false, player));
		}
		addStandardInventorySlots(inventory, player ? PLAYER_INVENTORY_X : 8, INVENTORY_Y);
	}

	/** Whether this is a player's body (the wide screen). */
	public static boolean isPlayer(@Nullable CorpseEntity corpse) {
		return corpse != null && corpse.isPlayerCorpse();
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
		private final boolean onlyWhenFull, shown;

		Taken(Container container, int index, int x, int y, @Nullable Identifier icon, boolean onlyWhenFull, boolean shown) {
			super(container, index, x, y);
			this.icon = icon;
			this.onlyWhenFull = onlyWhenFull;
			this.shown = shown;
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
			return shown && (!onlyWhenFull || hasItem());
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
