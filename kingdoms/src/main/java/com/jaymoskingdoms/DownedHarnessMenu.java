package com.jaymoskingdoms;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-25): "the downed loot screen should have a harness section". The loot screen for someone still alive and
 * down: their harness ({@link Harness}), the thirteen places laid out as {@link HarnessMenu} lays them round the figure, and
 * your inventory below. Opened from the body menu's "Take their harness" ({@link Downed}). Take only: nothing goes onto them.
 * <p>
 * The screen holds no copy of the harness. Its slots read the body's own saved harness ({@link Harness#worn}) and write it
 * through {@link Harness#set} (so their armour, what every client draws on them, and the body's look when they die all
 * follow at once), which is why a second person looking at the same body sees the same thing and a piece is never taken
 * twice. It stays open only while they are alive, still down and within reach, and you are up and alive.
 * <p>
 * Their weapons and pockets keep their own rows ({@code disarm} and {@code pickpocket}), which carry rules this screen would
 * bypass; their armour (helm to boots) is not on offer here.
 */
public class DownedHarnessMenu extends AbstractContainerMenu {
	/** How far (blocks) past reach the body may be before the screen closes. */
	private static final double REACH_SLACK = 2.0;
	private static final int OWN = Harness.SLOTS;

	/** The body: on the client, found by its network id (null if it isn't there). */
	public final @Nullable LivingEntity body;

	/** Client side. */
	public DownedHarnessMenu(int id, Inventory inventory, Integer bodyId) {
		this(id, inventory, inventory.player.level().getEntity(bodyId) instanceof LivingEntity living ? living : null, new SimpleContainer(Harness.SLOTS));
	}

	/** Server side. */
	public DownedHarnessMenu(int id, Inventory inventory, Mob body) {
		this(id, inventory, body, new Pieces(body));
	}

	private DownedHarnessMenu(int id, Inventory inventory, @Nullable LivingEntity body, Container pieces) {
		super(Harness.LOOT_MENU, id);
		this.body = body;
		for (Harness.Slot slot : Harness.Slot.values()) {
			int[] at = HarnessMenu.AT[slot.ordinal()];
			addSlot(new Slot(pieces, slot.ordinal(), at[0], at[1]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false;
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		addStandardInventorySlots(inventory, 8, HarnessMenu.INVENTORY_Y);
	}

	/** Opens the screen on {@code body} for {@code player}: the body menu's row has checked they may. */
	static void open(ServerPlayer player, Mob body) {
		player.openMenu(new ExtendedMenuProvider<Integer>() {
			@Override
			public Integer getScreenOpeningData(ServerPlayer serverPlayer) {
				return body.getId();
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable("container.jaymoskingdoms.downed_harness", body.getDisplayName());
			}

			@Override
			public AbstractContainerMenu createMenu(int menuId, Inventory inventory, Player menuPlayer) {
				return new DownedHarnessMenu(menuId, inventory, body);
			}
		});
	}

	@Override
	public boolean stillValid(Player player) {
		if (this.body == null || !this.body.isAlive() || this.body.isRemoved() || !player.isAlive()) {
			return false;
		}
		// (their downed state is a scoreboard tag, which only the server has)
		return player.level().isClientSide() || this.body.level() == player.level() && Downed.isDowned(this.body) && !Downed.isDowned(player)
			&& player.isWithinEntityInteractionRange(this.body, REACH_SLACK);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index >= OWN) {
			// nothing goes onto them
			return ItemStack.EMPTY;
		}
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		if (!moveItemStackTo(stack, OWN, this.slots.size(), true)) {
			return ItemStack.EMPTY;
		}
		// (what is left of it, if any, goes back as it is: the piece is never in two places)
		slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
		return moved;
	}

	/** Their harness as a container: every read and write goes to the body ({@link Harness#WORN}). A piece taken marks the body. */
	private static final class Pieces implements Container {
		private final Mob body;

		Pieces(Mob body) {
			this.body = body;
		}

		@Override
		public int getContainerSize() {
			return Harness.SLOTS;
		}

		@Override
		public boolean isEmpty() {
			return Harness.worn(this.body).stream().allMatch(ItemStack::isEmpty);
		}

		@Override
		public ItemStack getItem(int slot) {
			return slot >= 0 && slot < Harness.SLOTS ? Harness.worn(this.body).get(slot) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			ItemStack stack = getItem(slot);
			if (stack.isEmpty() || count <= 0) {
				return ItemStack.EMPTY;
			}
			ItemStack taken = stack.copy();
			setItem(slot, ItemStack.EMPTY);
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack stack = getItem(slot);
			if (stack.isEmpty()) {
				return ItemStack.EMPTY;
			}
			ItemStack taken = stack.copy();
			setItem(slot, ItemStack.EMPTY);
			return taken;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			if (slot < 0 || slot >= Harness.SLOTS) {
				return;
			}
			Harness.Slot place = Harness.Slot.values()[slot];
			boolean took = stack.isEmpty() && !Harness.get(this.body, place).isEmpty();
			Harness.set(this.body, place, stack.copy());
			if (took) {
				Harness.taken(this.body, place);
			}
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(Player player) {
			return this.body.isAlive();
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return false;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public void clearContent() {
			// their harness isn't cleared from here
		}
	}
}
