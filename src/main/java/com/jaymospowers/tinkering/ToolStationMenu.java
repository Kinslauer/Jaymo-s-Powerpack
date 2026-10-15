package com.jaymospowers.tinkering;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Tool Station: parts in, a tool out, in any slot order. A tool and its head material repairs it (a broken tool
 * too); a tool and a modifier's items adds a level of it ({@link Modifier}); a tool and one part it uses swaps that part
 * in, the old one is lost. (0.11.77) A weapon head and the parts its kind takes (a Tool Handle and a Tool Binding, mostly) make that
 * weapon ({@link WeaponParts}).
 */
public class ToolStationMenu extends AbstractContainerMenu {
	public static final int INPUTS = 4, RESULT = 4;
	/** Where the part slots sit, around the big result slot of Tinkers' screen. */
	public static final int[][] SLOT_AT = {{53, 22}, {15, 60}, {33, 41}, {15, 22}};
	private final ContainerLevelAccess access;
	/** For writing a modifier's enchantment onto the result. */
	private final HolderLookup.Provider registries;
	private final Container inputs = new SimpleContainer(INPUTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			ToolStationMenu.this.slotsChanged(this);
		}
	};
	private final ResultContainer result = new ResultContainer();
	/** How many of each input taking the result uses up. */
	private int[] uses = new int[INPUTS];
	/** What the station is doing, for the screen: null (nothing), or a line saying so. */
	private @Nullable Component status;

	/** Client side. */
	public ToolStationMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public ToolStationMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(Tinkering.TOOL_STATION_MENU, id);
		this.access = access;
		this.registries = inventory.player.level().registryAccess();
		for (int i = 0; i < INPUTS; i++) {
			this.addSlot(new Slot(inputs, i, SLOT_AT[i][0], SLOT_AT[i][1]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.getItem() instanceof ToolPartItem || stack.getItem() instanceof WeaponPartItem || Modifier.takes(stack) || TinkerMaterial.of(stack) != null
						|| Modifier.ingredient(stack);
				}
			});
		}
		this.addSlot(new Slot(result, RESULT, 114, 38) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack carried) {
				int[] used = uses;
				for (int i = 0; i < INPUTS; i++) {
					if (used[i] > 0) {
						inputs.removeItem(i, used[i]);
					}
				}
				access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.2F));
				super.onTake(player, carried);
			}
		});
		this.addStandardInventorySlots(inventory, 8, 102);
		StationEntity.open(access, inventory.player, inputs);
	}

	public @Nullable Component status() {
		return status;
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
		uses = new int[INPUTS];
		status = null;
		int toolSlot = -1;
		int filled = 0;
		for (int i = 0; i < INPUTS; i++) {
			ItemStack stack = inputs.getItem(i);
			if (!stack.isEmpty()) {
				filled++;
				if (Modifier.takes(stack)) {
					toolSlot = toolSlot == -1 ? i : -2;
				}
			}
		}
		ItemStack out = ItemStack.EMPTY;
		if (toolSlot >= 0 && filled > 1) {
			out = repair(toolSlot);
			if (out.isEmpty()) {
				out = modify(toolSlot);
			}
			if (out.isEmpty() && status == null) {
				out = swap(toolSlot);
			}
		} else if (toolSlot == -1 && filled > 0) {
			out = assemble();
		}
		result.setItem(RESULT, out);
		broadcastChanges();
	}

	/** Parts only: the tool whose parts they are. */
	private ItemStack assemble() {
		ItemStack weapon = assembleWeapon();
		if (weapon != null) {
			return weapon;
		}
		List<Integer> parts = new ArrayList<>();
		for (int i = 0; i < INPUTS; i++) {
			ItemStack stack = inputs.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (!(stack.getItem() instanceof ToolPartItem) || ToolPartItem.material(stack) == null) {
				return ItemStack.EMPTY;
			}
			parts.add(i);
		}
		for (ToolType type : ToolType.values()) {
			if (type.parts.size() != parts.size()) {
				continue;
			}
			List<Integer> left = new ArrayList<>(parts);
			List<TinkerMaterial> materials = new ArrayList<>();
			for (ToolType.Slot slot : type.parts) {
				Integer match = left.stream().filter(i -> ((ToolPartItem) inputs.getItem(i).getItem()).part == slot.part()).findFirst().orElse(null);
				if (match == null) {
					break;
				}
				left.remove(match);
				materials.add(ToolPartItem.material(inputs.getItem(match)));
			}
			if (materials.size() == type.parts.size()) {
				for (int i : parts) {
					uses[i] = 1;
				}
				return ToolStats.build(type, materials);
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * A weapon head with exactly what its kind takes: the weapon (null when there's no head; empty when the rest is
	 * wrong). The rod and binding can be any material.
	 */
	private @Nullable ItemStack assembleWeapon() {
		int head = -1;
		List<PartType> others = new ArrayList<>();
		for (int i = 0; i < INPUTS; i++) {
			ItemStack stack = inputs.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() instanceof WeaponPartItem) {
				if (head != -1) {
					return ItemStack.EMPTY;
				}
				head = i;
			} else if (stack.getItem() instanceof ToolPartItem part && ToolPartItem.material(stack) != null) {
				others.add(part.part);
			} else {
				others.add(null);
			}
		}
		if (head == -1) {
			return null;
		}
		List<PartType> needed = new ArrayList<>(((WeaponPartItem) inputs.getItem(head).getItem()).kind.assembly().parts);
		ItemStack out = WeaponPartItem.weapon(inputs.getItem(head));
		for (PartType part : others) {
			if (!needed.remove(part)) {
				return ItemStack.EMPTY;
			}
		}
		if (out == null || !needed.isEmpty()) {
			return ItemStack.EMPTY;
		}
		for (int i = 0; i < INPUTS; i++) {
			if (!inputs.getItem(i).isEmpty()) {
				uses[i] = 1;
			}
		}
		return out;
	}

	/** A damaged tool and its head material: as much repair as the material covers. */
	private ItemStack repair(int toolSlot) {
		ItemStack tool = inputs.getItem(toolSlot);
		if (!tool.isDamaged() || !(tool.getItem() instanceof TinkerToolItem item)) {
			return ItemStack.EMPTY;
		}
		List<TinkerMaterial> materials = ToolStats.materials(tool);
		List<TinkerMaterial> heads = new ArrayList<>();
		for (int i = 0; i < item.type.parts.size(); i++) {
			if (item.type.parts.get(i).part().kind == PartType.Kind.HEAD) {
				heads.add(materials.get(i));
			}
		}
		int perUnit = ToolStats.repairPerUnit(tool);
		int damage = tool.getDamageValue();
		TinkerMaterial used = null;
		for (int i = 0; i < INPUTS && damage > 0; i++) {
			if (i == toolSlot || inputs.getItem(i).isEmpty()) {
				continue;
			}
			ItemStack stack = inputs.getItem(i);
			TinkerMaterial material = TinkerMaterial.of(stack);
			if (material == null || material.stats == null || heads.stream().noneMatch(h -> h.stats == material.stats)) {
				uses = new int[INPUTS];
				return ItemStack.EMPTY;
			}
			used = material;
			int units = TinkerMaterial.units(stack);
			while (uses[i] < stack.getCount() && damage > 0) {
				uses[i]++;
				damage -= units * perUnit;
			}
		}
		if (used == null) {
			return ItemStack.EMPTY;
		}
		ItemStack out = tool.copy();
		out.setDamageValue(Math.max(0, damage));
		ToolStats.apply(out);
		uses[toolSlot] = 1;
		status = Component.translatable("gui.jaymospowers.tool_station.repair", used.displayName());
		return out;
	}

	/** A tool and a modifier's items: one more level of it, if the tool has a slot free. */
	private ItemStack modify(int toolSlot) {
		ItemStack tool = inputs.getItem(toolSlot);
		List<Item> others = new ArrayList<>();
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < INPUTS; i++) {
			if (i != toolSlot && !inputs.getItem(i).isEmpty()) {
				others.add(inputs.getItem(i).getItem());
				slots.add(i);
			}
		}
		for (Modifier modifier : Modifier.values()) {
			if (others.size() != modifier.recipe.size() || !others.containsAll(modifier.recipe) || !modifier.recipe.containsAll(others)) {
				continue;
			}
			Component cannot = Modifier.cannotAdd(tool, modifier);
			if (cannot != null) {
				status = cannot;
				return ItemStack.EMPTY;
			}
			ItemStack out = Modifier.add(tool, modifier);
			Modifier.enchant(out, registries);
			for (int i : slots) {
				uses[i] = 1;
			}
			uses[toolSlot] = 1;
			status = Component.translatable("gui.jaymospowers.tool_station.modify", modifier.name(Modifier.level(out, modifier)),
				Modifier.slots(out) - Modifier.used(out));
			return out;
		}
		return ItemStack.EMPTY;
	}

	/** A tool and one part it uses: that part swapped in (the first of its kind with another material). */
	private ItemStack swap(int toolSlot) {
		ItemStack tool = inputs.getItem(toolSlot);
		if (!(tool.getItem() instanceof TinkerToolItem item)) {
			return ItemStack.EMPTY;
		}
		int partSlot = -1;
		for (int i = 0; i < INPUTS; i++) {
			if (i != toolSlot && !inputs.getItem(i).isEmpty()) {
				if (partSlot != -1 || !(inputs.getItem(i).getItem() instanceof ToolPartItem)) {
					return ItemStack.EMPTY;
				}
				partSlot = i;
			}
		}
		ItemStack partStack = inputs.getItem(partSlot);
		PartType part = ((ToolPartItem) partStack.getItem()).part;
		TinkerMaterial material = ToolPartItem.material(partStack);
		List<TinkerMaterial> materials = new ArrayList<>(ToolStats.materials(tool));
		int target = -1;
		for (int i = 0; i < item.type.parts.size(); i++) {
			if (item.type.parts.get(i).part() == part && materials.get(i) != material && target == -1) {
				target = i;
			}
		}
		if (material == null || target == -1) {
			return ItemStack.EMPTY;
		}
		materials.set(target, material);
		ItemStack out = tool.copy();
		out.set(Tinkering.TOOL_MATERIALS, materials.stream().map(m -> m.id).toList());
		ToolStats.apply(out);
		uses[toolSlot] = 1;
		uses[partSlot] = 1;
		status = Component.translatable("gui.jaymospowers.tool_station.swap", Component.translatable("item.jaymospowers." + part.id));
		return out;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, Tinkering.TOOL_STATION);
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
			if (!this.moveItemStackTo(stack, RESULT + 1, end, true)) {
				return ItemStack.EMPTY;
			}
			slot.onTake(player, moved);
			return moved;
		}
		if (index < INPUTS) {
			if (!this.moveItemStackTo(stack, RESULT + 1, end, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!slots.get(0).mayPlace(stack) || !this.moveItemStackTo(stack, 0, INPUTS, false)) {
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
