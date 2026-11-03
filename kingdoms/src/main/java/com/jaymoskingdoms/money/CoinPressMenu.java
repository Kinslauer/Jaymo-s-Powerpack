package com.jaymoskingdoms.money;

import com.jaymoskingdoms.Faction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.487) The Coin Press's screen: metal in on the left, the struck coins out on the right. The output shows what the
 * whole input would strike into (as much as the realm's mint may still strike today, and one stack at most); taking it
 * strikes it. Shift-click the output to strike and take it in one go. The screen reads the press's status, what the mint
 * has left today, and its daily cap from three synced numbers ({@link CoinPress#OK}, {@link CoinPress#NO_DIE},
 * {@link CoinPress#SPENT}).
 */
public class CoinPressMenu extends AbstractContainerMenu {
	public static final int INPUT = 0, OUTPUT = 1, FIRST_INVENTORY = 2, HOTBAR = 29, END = 38;
	public static final int STATUS = 0, REMAINING = 1, CAP = 2;

	private final ContainerLevelAccess access;
	private final ContainerData data;
	private final Container input = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			super.setChanged();
			CoinPressMenu.this.slotsChanged(this);
		}
	};
	private final Container output = new SimpleContainer(1);
	private final Slot outputSlot;
	/** What the output slot holds would strike this (server side). */
	private CoinPress.@Nullable Die die;
	private int units;

	public CoinPressMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public CoinPressMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(MoneyItems.PRESS_MENU, id);
		this.access = access;
		this.data = new SimpleContainerData(3);
		addSlot(new Slot(this.input, 0, 44, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return CoinPress.die(stack) != null;
			}
		});
		this.outputSlot = addSlot(new Slot(this.output, 0, 116, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack carried) {
				strike(player, false);
				super.onTake(player, carried);
			}
		});
		addStandardInventorySlots(inventory, 8, 84);
		addDataSlots(this.data);
		refresh(false);
	}

	/** The press's status (0 ok, 1 no die, 2 spent), the coins left to strike today, and the day's cap. */
	public int status() {
		return this.data.get(STATUS);
	}

	public int remaining() {
		return this.data.get(REMAINING);
	}

	public int cap() {
		return this.data.get(CAP);
	}

	@Override
	public void slotsChanged(Container container) {
		refresh(true);
		super.slotsChanged(container);
	}

	/** Server side: works out what the input would strike into, and the mint's state. */
	private void refresh(boolean broadcast) {
		this.access.execute((level, pos) -> {
			if (!(level instanceof ServerLevel sl)) {
				return;
			}
			Faction mint = CoinPress.mint(sl, pos);
			ItemStack in = this.input.getItem(0);
			this.die = null;
			this.units = 0;
			ItemStack result = ItemStack.EMPTY;
			if (mint == null) {
				this.data.set(STATUS, CoinPress.NO_DIE);
				this.data.set(REMAINING, 0);
				this.data.set(CAP, 0);
			} else {
				int left = CoinPress.remaining(sl.getServer(), mint);
				this.data.set(STATUS, left <= 0 ? CoinPress.SPENT : CoinPress.OK);
				this.data.set(REMAINING, left);
				this.data.set(CAP, CoinPress.cap(sl.getServer(), mint));
				CoinPress.Die die = CoinPress.die(in);
				if (die != null && left > 0) {
					int n = Math.min(in.getCount() / die.input(), Math.min(left, CoinPress.MOST_AT_ONCE) / die.coins());
					if (n > 0) {
						this.die = die;
						this.units = n;
						result = die.coin().stack(n * die.coins());
					}
				}
			}
			this.output.setItem(0, result);
			if (broadcast) {
				broadcastChanges();
			}
		});
	}

	/**
	 * Strikes what the output shows: takes the metal, counts the coins against the mint's day and pays the realm its
	 * share. With {@code give} the coins go straight to {@code player} (shift-click); otherwise the caller hands them over.
	 */
	private void strike(Player player, boolean give) {
		CoinPress.Die die = this.die;
		int n = this.units;
		if (die == null || n <= 0 || !(player instanceof ServerPlayer sp)) {
			return;
		}
		this.input.removeItem(0, n * die.input());
		int coins = n * die.coins();
		this.access.execute((level, pos) -> {
			if (level instanceof ServerLevel sl) {
				Faction mint = CoinPress.mint(sl, pos);
				if (mint != null) {
					CoinPress.struck(sl.getServer(), mint, die.coin(), coins);
				}
				sl.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.5F);
			}
		});
		if (give) {
			Money.give(sp, die.coin().stack(coins));
		}
		this.die = null;
		this.units = 0;
		refresh(true);
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, MoneyItems.COIN_PRESS);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.access.execute((level, pos) -> clearContainer(player, this.input));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		if (index == OUTPUT) {
			// strike it all and take it in one go (the client leaves it to the server, which syncs the result)
			if (!player.level().isClientSide()) {
				strike(player, true);
			}
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index == INPUT) {
			if (!moveItemStackTo(stack, FIRST_INVENTORY, END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (CoinPress.die(stack) != null) {
			if (!moveItemStackTo(stack, INPUT, INPUT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (index < HOTBAR ? !moveItemStackTo(stack, HOTBAR, END, false) : !moveItemStackTo(stack, FIRST_INVENTORY, HOTBAR, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == copy.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return copy;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return target != this.outputSlot && super.canTakeItemForPickAll(carried, target);
	}
}
