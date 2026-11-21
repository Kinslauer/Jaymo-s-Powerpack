package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The tackle bench's screen: a rod, a hook, a line and a float (any dye) on the left, the rod as fitted on the right.
 * Take it and the fittings used are spent; whatever the rod had in that place is replaced. Drawn by the client's
 * TackleBenchScreen.
 */
public class TackleBenchMenu extends AbstractContainerMenu {
	public static MenuType<TackleBenchMenu> TYPE;
	/** Where the slots are: rod, hook, line, float, then the result. */
	public static final int ROD = 0, HOOK = 1, LINE = 2, BEAD = 3, RESULT = 4, INPUTS = 4;
	public static final int[][] AT = {{26, 35}, {62, 17}, {62, 35}, {62, 53}, {134, 35}};
	public static final int INVENTORY_Y = 84;

	private final ContainerLevelAccess access;
	private final Container inputs = new SimpleContainer(INPUTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			TackleBenchMenu.this.slotsChanged(this);
		}
	};
	private final Container result = new SimpleContainer(1);

	static void init() {
		TYPE = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("tackle_bench"), new MenuType<>(TackleBenchMenu::new, FeatureFlags.VANILLA_SET));
	}

	/** Client side. */
	public TackleBenchMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public TackleBenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(TYPE, id);
		this.access = access;
		this.addSlot(new Slot(inputs, ROD, AT[ROD][0], AT[ROD][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return HookBait.isRod(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		this.addSlot(new Slot(inputs, HOOK, AT[HOOK][0], AT[HOOK][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(FishTackle.BARBED_HOOK);
			}
		});
		this.addSlot(new Slot(inputs, LINE, AT[LINE][0], AT[LINE][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(FishTackle.BRAIDED_LINE) || stack.is(FishTackle.WEIGHTED_LINE);
			}
		});
		this.addSlot(new Slot(inputs, BEAD, AT[BEAD][0], AT[BEAD][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.has(DataComponents.DYE);
			}
		});
		this.addSlot(new Slot(result, 0, AT[RESULT][0], AT[RESULT][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack carried) {
				spend(player);
				super.onTake(player, carried);
			}
		});
		this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	/** The rod's fittings once the parts on the bench are fitted: what's on the bench replaces what the rod had. */
	private FishTackle.Fit fitted(FishTackle.Fit was) {
		int hook = inputs.getItem(HOOK).is(FishTackle.BARBED_HOOK) ? FishTackle.BARBED : was.hook();
		int line = inputs.getItem(LINE).is(FishTackle.BRAIDED_LINE) ? FishTackle.BRAIDED
			: inputs.getItem(LINE).is(FishTackle.WEIGHTED_LINE) ? FishTackle.WEIGHTED : was.line();
		ItemStack dye = inputs.getItem(BEAD);
		String bead = dye.isEmpty() ? was.bead() : BuiltInRegistries.ITEM.getKey(dye.getItem()).toString();
		return new FishTackle.Fit(hook, line, bead);
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		ItemStack rod = inputs.getItem(ROD);
		ItemStack out = ItemStack.EMPTY;
		if (!rod.isEmpty()) {
			FishTackle.Fit was = FishTackle.fit(rod);
			FishTackle.Fit now = fitted(was);
			if (!now.equals(was)) {
				out = rod.copy();
				out.set(FishTackle.FIT, now);
			}
		}
		result.setItem(0, out);
	}

	/** The rod is taken: each part that was fitted is spent, and the plain rod on the bench is gone. */
	private void spend(Player player) {
		FishTackle.Fit was = FishTackle.fit(inputs.getItem(ROD));
		FishTackle.Fit now = fitted(was);
		if (now.hook() != was.hook()) {
			inputs.getItem(HOOK).shrink(1);
		}
		if (now.line() != was.line()) {
			inputs.getItem(LINE).shrink(1);
		}
		if (!now.bead().equals(was.bead())) {
			inputs.getItem(BEAD).shrink(1);
		}
		inputs.setItem(ROD, ItemStack.EMPTY);
		access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4F, 1.6F));
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.access.execute((level, pos) -> this.clearContainer(player, this.inputs));
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, FishTackle.TACKLE_BENCH);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		int end = this.slots.size();
		if (index == RESULT) {
			if (!this.moveItemStackTo(stack, RESULT + 1, end, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, moved);
		} else if (index < INPUTS) {
			if (!this.moveItemStackTo(stack, RESULT + 1, end, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, 0, INPUTS, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == moved.getCount()) {
			return ItemStack.EMPTY;
		}
		if (index == RESULT) {
			slot.onTake(player, stack);
		}
		return moved;
	}
}
