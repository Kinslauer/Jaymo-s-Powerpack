package com.jaymospowers.tinkering;

import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Part Builder: a pattern and a material in, pick a part, take it out. Each part costs its material units
 * ({@link PartType#cost}); a log is worth 4 planks, a block of metal 9 ingots, and what's left over of the last item is lost.
 * The layout is Tinkers' own screen: pattern and material on the left, the 4x4 part buttons, the result on the right.
 * (0.11.70) Weapon heads ({@link WeaponParts}) are buttons {@link #WEAPON_BUTTONS} on, shaped from stone only.
 */
public class PartBuilderMenu extends AbstractContainerMenu {
	public static final int PATTERN = 0, MATERIAL = 1, RESULT = 2;
	/** Button ids of the weapon heads: this plus the head's place in {@link WeaponParts#loaded()}. */
	public static final int WEAPON_BUTTONS = 100;
	private final ContainerLevelAccess access;
	private final DataSlot selected = DataSlot.standalone();
	private final Container inputs = new SimpleContainer(2) {
		@Override
		public void setChanged() {
			super.setChanged();
			PartBuilderMenu.this.slotsChanged(this);
		}
	};
	private final ResultContainer result = new ResultContainer();

	/** Client side. */
	public PartBuilderMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public PartBuilderMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(Tinkering.PART_BUILDER_MENU, id);
		this.access = access;
		this.selected.set(-1);
		this.addSlot(new Slot(inputs, PATTERN, 8, 43) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Tinkering.PATTERN);
			}
		});
		this.addSlot(new Slot(inputs, MATERIAL, 29, 43) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return TinkerMaterial.of(stack) != null;
			}
		});
		this.addSlot(new Slot(result, RESULT, 148, 42) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack carried) {
				int cost = selectedCost();
				if (cost > 0) {
					inputs.removeItem(PATTERN, 1);
					inputs.removeItem(MATERIAL, itemsNeeded(cost, inputs.getItem(MATERIAL)));
					access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F));
				}
				super.onTake(player, carried);
			}
		});
		this.addStandardInventorySlots(inventory, 8, 102);
		StationEntity.open(access, inventory.player, inputs);
		this.addDataSlot(selected);
	}

	public @Nullable PartType selectedPart() {
		int index = selected.get();
		return index >= 0 && index < PartType.values().length ? PartType.values()[index] : null;
	}

	/** The weapon head picked, or null. */
	public WeaponParts.@Nullable Kind selectedWeapon() {
		int index = selected.get() - WEAPON_BUTTONS;
		List<WeaponParts.Kind> kinds = WeaponParts.loaded();
		return index >= 0 && index < kinds.size() ? kinds.get(index) : null;
	}

	/** Material units the picked part or head takes (0: nothing picked). */
	private int selectedCost() {
		PartType part = selectedPart();
		WeaponParts.Kind weapon = selectedWeapon();
		return part != null ? part.cost : weapon != null ? weapon.stoneCost() : 0;
	}

	/** Whether this material shapes weapon heads: stone of any kind. */
	public static boolean shapesWeapons(@Nullable TinkerMaterial material) {
		return material != null && material.stats == TinkerMaterial.Stats.ROCK;
	}

	public ItemStack material() {
		return slots.get(MATERIAL).getItem();
	}

	public boolean hasPattern() {
		return slots.get(PATTERN).hasItem();
	}

	/** Items of this stack the part takes (0 when it isn't a material). */
	public static int itemsNeeded(PartType part, ItemStack material) {
		return itemsNeeded(part.cost, material);
	}

	public static int itemsNeeded(int cost, ItemStack material) {
		int units = TinkerMaterial.units(material);
		return units <= 0 ? 0 : (cost + units - 1) / units;
	}

	@Override
	public boolean clickMenuButton(Player player, int button) {
		boolean weapon = button >= WEAPON_BUTTONS && button - WEAPON_BUTTONS < WeaponParts.loaded().size();
		if (!weapon && (button < 0 || button >= PartType.values().length)) {
			return false;
		}
		selected.set(button);
		update();
		return true;
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (container == inputs) {
			update();
			StationEntity.changed(access, inputs);
		}
	}

	private void update() {
		PartType part = selectedPart();
		ItemStack material = inputs.getItem(MATERIAL);
		TinkerMaterial kind = TinkerMaterial.of(material);
		ItemStack out = ItemStack.EMPTY;
		if (part != null && kind != null && !kind.cast && kind.makes(part.kind) && inputs.getItem(PATTERN).is(Tinkering.PATTERN)
			&& material.getCount() >= itemsNeeded(part, material)) {
			out = ToolPartItem.of(part, kind, 1);
		}
		WeaponParts.Kind weapon = selectedWeapon();
		if (weapon != null && shapesWeapons(kind) && inputs.getItem(PATTERN).is(Tinkering.PATTERN)
			&& material.getCount() >= itemsNeeded(weapon.stoneCost(), material)) {
			out = WeaponPartItem.of(weapon, WeaponParts.Metal.STONE, 1);
		}
		result.setItem(RESULT, out);
		broadcastChanges();
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, Tinkering.PART_BUILDER);
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
		int end = this.slots.size();
		if (index == RESULT) {
			// take as many as the inputs allow, one part at a time
			int made = 0;
			while (selectedCost() > 0 && slot.hasItem() && made < 64) {
				ItemStack one = slot.getItem().copy();
				if (!this.moveItemStackTo(one, 3, end, true)) {
					break;
				}
				slot.onTake(player, slot.getItem());
				made++;
			}
			return ItemStack.EMPTY;
		}
		if (index < RESULT) {
			if (!this.moveItemStackTo(stack, 3, end, true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(Tinkering.PATTERN)) {
			if (!this.moveItemStackTo(stack, PATTERN, PATTERN + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (TinkerMaterial.of(stack) != null) {
			if (!this.moveItemStackTo(stack, MATERIAL, MATERIAL + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else {
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
		StationEntity.close(access, player, inputs, () -> this.clearContainer(player, inputs));
	}
}
