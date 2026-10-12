package com.jaymoskingdoms.equestrian;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.38) The tack screen ({@link Bonding}): the horse's saddle and barding (vanilla's equipment slots), its bridle and
 * its blanket (the {@link Bonding#TACK} attachment) down the left, and what it thinks of you and has learnt on the right
 * ({@code TackScreen} draws that from the synced {@link Bond}). Opened from the "Tack" button on the horse's own screen.
 */
public class TackMenu extends AbstractContainerMenu {
	public static final int SLOT_X = 8, SADDLE_Y = 18, BRIDLE_Y = 36, BLANKET_Y = 54, BARDING_Y = 72, INVENTORY_Y = 102;
	private static final int OWN = 4;
	private static final Identifier SADDLE_SPRITE = Identifier.withDefaultNamespace("container/slot/saddle");
	private static final Identifier ARMOR_SPRITE = Identifier.withDefaultNamespace("container/slot/horse_armor");
	public final @Nullable AbstractHorse horse;

	/** Client side: the horse by its network id. */
	public TackMenu(int id, Inventory inventory, Integer horseId) {
		this(id, inventory, inventory.player.level().getEntity(horseId) instanceof AbstractHorse h ? h : null, new SimpleContainer(2));
	}

	/** Server side. */
	public TackMenu(int id, Inventory inventory, AbstractHorse horse) {
		this(id, inventory, horse, new TackContainer(horse));
	}

	private TackMenu(int id, Inventory inventory, @Nullable AbstractHorse horse, Container tack) {
		super(Bonding.TACK_MENU, id);
		this.horse = horse;
		if (horse != null) {
			addSlot(new ArmorSlot(horse.createEquipmentSlotContainer(EquipmentSlot.SADDLE), horse, EquipmentSlot.SADDLE, 0, SLOT_X, SADDLE_Y, SADDLE_SPRITE));
		} else {
			addSlot(new Slot(new SimpleContainer(1), 0, SLOT_X, SADDLE_Y));
		}
		addSlot(new Slot(tack, 0, SLOT_X, BRIDLE_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Tackroom.BRIDLE);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addSlot(new Slot(tack, 1, SLOT_X, BLANKET_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Tackroom.HORSE_BLANKET);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		if (horse != null) {
			addSlot(new ArmorSlot(horse.createEquipmentSlotContainer(EquipmentSlot.BODY), horse, EquipmentSlot.BODY, 0, SLOT_X, BARDING_Y, ARMOR_SPRITE) {
				@Override
				public boolean isActive() {
					return horse.canUseSlot(EquipmentSlot.BODY);
				}
			});
		} else {
			addSlot(new Slot(new SimpleContainer(1), 0, SLOT_X, BARDING_Y));
		}
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	@Override
	public boolean stillValid(Player player) {
		return this.horse != null && this.horse.isAlive() && (player.getVehicle() == this.horse || player.isWithinEntityInteractionRange(this.horse, 4.0));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < OWN) {
			if (!moveItemStackTo(stack, OWN, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean moved = false;
			for (int i = 0; i < OWN && !moved; i++) {
				Slot own = this.slots.get(i);
				if (own.isActive() && !own.hasItem() && own.mayPlace(stack)) {
					moved = moveItemStackTo(stack, i, i + 1, false);
				}
			}
			if (!moved) {
				int hotbar = OWN + 27;
				if (index < hotbar ? !moveItemStackTo(stack, hotbar, this.slots.size(), false) : !moveItemStackTo(stack, OWN, hotbar, false)) {
					return ItemStack.EMPTY;
				}
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	/** The bridle and blanket, kept on the horse itself. */
	private static final class TackContainer implements Container {
		private final AbstractHorse horse;

		TackContainer(AbstractHorse horse) {
			this.horse = horse;
		}

		@Override
		public int getContainerSize() {
			return 2;
		}

		@Override
		public boolean isEmpty() {
			return Bonding.tack(this.horse).empty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return Bonding.tack(this.horse).get(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			ItemStack stack = getItem(slot);
			if (stack.isEmpty()) {
				return ItemStack.EMPTY;
			}
			ItemStack taken = stack.copy().split(count);
			ItemStack left = stack.copy();
			left.shrink(count);
			setItem(slot, left);
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack stack = getItem(slot);
			setItem(slot, ItemStack.EMPTY);
			return stack;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			this.horse.setAttached(Bonding.TACK, Bonding.tack(this.horse).with(slot, stack.copy()));
			if (!stack.isEmpty()) {
				this.horse.setPersistenceRequired();
			}
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(Player player) {
			return this.horse.isAlive();
		}

		@Override
		public void clearContent() {
			this.horse.setAttached(Bonding.TACK, Tack.NONE);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
