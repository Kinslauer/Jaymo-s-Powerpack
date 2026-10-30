package com.jaymospowers.tinkering;

import com.jaymospowers.power.wind.SorasDagger;
import com.jaymospowers.power.wind.SorasLineage;
import com.jaymospowers.tinkering.table.TableExtras;
import com.jaymospowers.tinkering.table.Tables;
import com.jaymospowers.tinkering.table.TinkerTableMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The Tool Station: parts in, a tool out, in any slot order. A tool and its head material repairs it (a broken tool
 * too); a tool and a modifier's items adds a level of it ({@link Modifier}); a tool and one part it uses swaps that part
 * in, the old one is lost. (0.11.77) A weapon head and the parts its kind takes (a Tool Handle and a Tool Binding, mostly) make that
 * weapon ({@link WeaponParts}).
 * <p>
 * (Tool Forge) The same menu with 5 slots is the Tool Forge's ({@link #forge}): a tool of more than
 * {@link Tables#STATION_MAX_PARTS} parts (or a weapon head with more than that in all) can only be put together there.
 * Both have the side inventory and tabs ({@link TableExtras}).
 */
public class ToolStationMenu extends AbstractContainerMenu implements TinkerTableMenu {
	/** (0.8.115) Diamonds set into Sora's Dagger's gilded blade. */
	static final int EMBED_DIAMONDS = 2;
	public static final int INPUTS = 4, RESULT = 4;
	/** Where the part slots sit, around the big result slot of Tinkers' screen. */
	public static final int[][] SLOT_AT = {{53, 22}, {15, 60}, {33, 41}, {15, 22}};
	/** The Tool Forge's five: the station's four corners and middle, after Tinkers' anvil. */
	public static final int FORGE_INPUTS = 5;
	public static final int[][] FORGE_SLOT_AT = {{53, 22}, {15, 60}, {33, 41}, {15, 22}, {53, 60}};
	private final ContainerLevelAccess access;
	private final StationBlock.Kind kind;
	/** How many part slots this table has; the result slot comes next. */
	private final int inputCount;
	private final int[][] layout;
	private final TableExtras extras;
	/** For writing a modifier's enchantment onto the result. */
	private final HolderLookup.Provider registries;
	private final Container inputs;
	private final ResultContainer result = new ResultContainer();
	/** How many of each input taking the result uses up. */
	private int[] uses;
	/** What the station is doing, for the screen: null (nothing), or a line saying so. */
	private @Nullable Component status;

	/** Client side. */
	public ToolStationMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public ToolStationMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		this(Tinkering.TOOL_STATION_MENU, id, inventory, access, SLOT_AT, StationBlock.Kind.TOOL_STATION);
	}

	/** The Tool Forge's menu, client side. */
	public static ToolStationMenu forge(int id, Inventory inventory) {
		return forge(id, inventory, ContainerLevelAccess.NULL);
	}

	public static ToolStationMenu forge(int id, Inventory inventory, ContainerLevelAccess access) {
		return new ToolStationMenu(Tables.TOOL_FORGE_MENU, id, inventory, access, FORGE_SLOT_AT, StationBlock.Kind.TOOL_FORGE);
	}

	private ToolStationMenu(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access, int[][] layout, StationBlock.Kind kind) {
		super(type, id);
		this.access = access;
		this.kind = kind;
		this.layout = layout;
		this.inputCount = layout.length;
		this.uses = new int[inputCount];
		this.inputs = new SimpleContainer(inputCount) {
			@Override
			public void setChanged() {
				super.setChanged();
				ToolStationMenu.this.slotsChanged(this);
			}
		};
		this.registries = inventory.player.level().registryAccess();
		for (int i = 0; i < inputCount; i++) {
			this.addSlot(new Slot(inputs, i, layout[i][0], layout[i][1]) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.getItem() instanceof ToolPartItem || stack.getItem() instanceof WeaponPartItem || Modifier.takes(stack) || TinkerMaterial.of(stack) != null
						|| stack.getItem() instanceof SorasDagger || stack.is(Items.BREEZE_ROD) || WeaponParts.isExtra(stack)
						|| Modifier.ingredient(stack);
				}
			});
		}
		this.addSlot(new Slot(result, inputCount, 114, 38) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack carried) {
				int[] used = uses;
				for (int i = 0; i < inputCount; i++) {
					if (used[i] > 0) {
						inputs.removeItem(i, used[i]);
					}
				}
				access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.2F));
				Tinkering.toolBuilt(player, carried);
				super.onTake(player, carried);
			}
		});
		this.addStandardInventorySlots(inventory, 8, 102);
		this.extras = new TableExtras(access, inventory.player, kind);
		extras.slots().forEach(this::addSlot);
		extras.data().forEach(this::addDataSlot);
		StationEntity.open(access, inventory.player, inputs);
	}

	@Override
	public TableExtras extras() {
		return extras;
	}

	/** How many part slots there are (the result slot is the one after). */
	public int inputCount() {
		return inputCount;
	}

	public int resultSlot() {
		return inputCount;
	}

	/** Where part slot {@code i} sits. */
	public int[] slotAt(int i) {
		return layout[i];
	}

	/** Most parts a tool can have to be put together here: 3 at the Tool Station, any at the Tool Forge. */
	public int maxParts() {
		return kind == StationBlock.Kind.TOOL_FORGE ? Integer.MAX_VALUE : Tables.STATION_MAX_PARTS;
	}

	/** (Tool Forge) The line for a tool that needs the Tool Forge, or null when this table can make it. */
	private @Nullable Component forgeOnly(int parts) {
		return parts > maxParts() ? Component.translatable("gui.jaymospowers.tool_station.needs_forge") : null;
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
		uses = new int[inputCount];
		status = null;
		int toolSlot = -1;
		int filled = 0;
		for (int i = 0; i < inputCount; i++) {
			ItemStack stack = inputs.getItem(i);
			if (!stack.isEmpty()) {
				filled++;
				if (Modifier.takes(stack)) {
					toolSlot = toolSlot == -1 ? i : -2;
				}
			}
		}
		// (the dagger counts as a weapon, so it's checked before the tool recipes)
		ItemStack out = embed();
		if (!out.isEmpty()) {
			result.setItem(inputCount, out);
			broadcastChanges();
			return;
		}
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
		result.setItem(inputCount, out);
		broadcastChanges();
	}

	/**
	 * (0.8.115) Andy: Sora's Dagger's diamond stage sets diamonds into the gilded blade rather than making it of them. A
	 * gilded dagger, a breeze rod to bind them and {@link #EMBED_DIAMONDS} diamonds (stacked or spread over slots).
	 */
	private ItemStack embed() {
		int dagger = -1, rod = -1;
		int[] taking = new int[inputCount];
		int diamonds = 0;
		for (int i = 0; i < inputCount; i++) {
			ItemStack stack = inputs.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() instanceof SorasDagger d && d.stage == SorasDagger.GILDED && dagger == -1) {
				dagger = i;
			} else if (stack.is(Items.BREEZE_ROD) && rod == -1) {
				rod = i;
			} else if (stack.is(Items.DIAMOND)) {
				taking[i] = Math.min(stack.getCount(), EMBED_DIAMONDS - diamonds);
				diamonds += taking[i];
			} else {
				return ItemStack.EMPTY;
			}
		}
		if (dagger == -1 || rod == -1 || diamonds < EMBED_DIAMONDS) {
			return ItemStack.EMPTY;
		}
		uses = taking;
		uses[dagger] = 1;
		uses[rod] = 1;
		status = Component.translatable("gui.jaymospowers.tool_station.embed");
		return inputs.getItem(dagger).transmuteCopy(SorasDagger.STAGES[SorasDagger.DIAMOND], 1);
	}

	/** Parts only: the tool whose parts they are. */
	private ItemStack assemble() {
		ItemStack weapon = assembleWeapon();
		if (weapon != null) {
			return weapon;
		}
		List<Integer> parts = new ArrayList<>();
		for (int i = 0; i < inputCount; i++) {
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
			int head = -1;
			for (ToolType.Slot slot : type.parts) {
				Integer match = left.stream().filter(i -> ((ToolPartItem) inputs.getItem(i).getItem()).part == slot.part()).findFirst().orElse(null);
				if (match == null) {
					break;
				}
				if (head == -1) {
					head = match;
				}
				left.remove(match);
				materials.add(ToolPartItem.material(inputs.getItem(match)));
			}
			if (materials.size() == type.parts.size()) {
				Component forge = forgeOnly(type.parts.size());
				if (forge != null) {
					status = forge;
					return ItemStack.EMPTY;
				}
				// (Smithing) a hot head must be quenched first; the head's grade carries onto the tool
				Component refusal = refusal(parts);
				if (refusal != null) {
					status = refusal;
					return ItemStack.EMPTY;
				}
				for (int i : parts) {
					uses[i] = 1;
				}
				ItemStack built = ToolStats.build(type, materials);
				com.jaymospowers.tinkering.smithing.Grades.carry(inputs.getItem(head), built);
				built.setCount(type.ranged.yield());
				return built;
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
		// (0.18.15) the plain items beside the head (a Sealed Helm's glass block)
		List<Item> loose = new ArrayList<>();
		for (int i = 0; i < inputCount; i++) {
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
				loose.add(stack.getItem());
			}
		}
		if (head == -1) {
			return null;
		}
		Item extra = ((WeaponPartItem) inputs.getItem(head).getItem()).kind.assembly().extra;
		if (extra != null) {
			int at = loose.indexOf(extra);
			if (at < 0) {
				return ItemStack.EMPTY;
			}
			others.remove(null);
		}
		List<PartType> needed = new ArrayList<>(((WeaponPartItem) inputs.getItem(head).getItem()).kind.assembly().parts);
		ItemStack out = SorasLineage.carry(inputs.getItem(head), WeaponPartItem.weapon(inputs.getItem(head)));
		for (PartType part : others) {
			if (!needed.remove(part)) {
				return ItemStack.EMPTY;
			}
		}
		if (out == null || !needed.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Component forge = forgeOnly(others.size() + 1);
		if (forge != null) {
			status = forge;
			return ItemStack.EMPTY;
		}
		// (Smithing) a hot head must be quenched first; the head's grade carries onto the weapon
		Component refusal = com.jaymospowers.tinkering.smithing.Grades.refusal(inputs.getItem(head));
		if (refusal != null) {
			status = refusal;
			return ItemStack.EMPTY;
		}
		com.jaymospowers.tinkering.smithing.Grades.carry(inputs.getItem(head), out);
		for (int i = 0; i < inputCount; i++) {
			if (!inputs.getItem(i).isEmpty()) {
				uses[i] = 1;
			}
		}
		return out;
	}

	/** (Smithing) Why the station won't take one of these parts yet, or null. */
	private @Nullable Component refusal(List<Integer> slots) {
		for (int i : slots) {
			Component why = com.jaymospowers.tinkering.smithing.Grades.refusal(inputs.getItem(i));
			if (why != null) {
				return why;
			}
		}
		return null;
	}

	/** A damaged tool and its head material: as much repair as the material covers. */
	private ItemStack repair(int toolSlot) {
		ItemStack tool = inputs.getItem(toolSlot);
		if (!tool.isDamaged() || !(tool.getItem() instanceof TinkerTool item)) {
			return ItemStack.EMPTY;
		}
		List<TinkerMaterial> materials = ToolStats.materials(tool);
		List<TinkerMaterial> heads = new ArrayList<>();
		for (int i = 0; i < item.type().parts.size(); i++) {
			if (item.type().parts.get(i).part().kind == PartType.Kind.HEAD || item.type().parts.get(i).part().kind == PartType.Kind.LIMB) {
				heads.add(materials.get(i));
			}
		}
		int perUnit = ToolStats.repairPerUnit(tool);
		int damage = tool.getDamageValue();
		TinkerMaterial used = null;
		for (int i = 0; i < inputCount && damage > 0; i++) {
			if (i == toolSlot || inputs.getItem(i).isEmpty()) {
				continue;
			}
			ItemStack stack = inputs.getItem(i);
			TinkerMaterial material = TinkerMaterial.of(stack);
			if (material == null || material.stats == null || heads.stream().noneMatch(h -> h.stats == material.stats)) {
				uses = new int[inputCount];
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
		for (int i = 0; i < inputCount; i++) {
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
		if (!(tool.getItem() instanceof TinkerTool item)) {
			return ItemStack.EMPTY;
		}
		int partSlot = -1;
		for (int i = 0; i < inputCount; i++) {
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
		for (int i = 0; i < item.type().parts.size(); i++) {
			if (item.type().parts.get(i).part() == part && materials.get(i) != material && target == -1) {
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
		return stillValid(access, player, kind.block()) && extras.stillValid(player);
	}

	@Override
	public boolean clickMenuButton(Player player, int button) {
		return extras.clickTab(player, button);
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
		int resultAt = inputCount, playerStart = resultAt + 1, playerEnd = playerStart + 36;
		if (index == resultAt) {
			if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
				return ItemStack.EMPTY;
			}
			// a build of several (arrows) that only partly fit: the rest goes to the player (or the floor), not lost
			if (!stack.isEmpty()) {
				ItemStack rest = stack.split(stack.getCount());
				if (!player.level().isClientSide()) {
					player.getInventory().placeItemBackInInventory(rest, Prediction.SERVER_ONLY);
				}
			}
			slot.onTake(player, moved);
			return moved;
		}
		if (index < inputCount) {
			if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
				return ItemStack.EMPTY;
			}
		} else if (index >= extras.first()) {
			// from the side inventory: into the station if it takes it, else to the player
			if (!(slots.get(0).mayPlace(stack) && this.moveItemStackTo(stack, 0, inputCount, false))
				&& !this.moveItemStackTo(stack, playerStart, playerEnd, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!(slots.get(0).mayPlace(stack) && this.moveItemStackTo(stack, 0, inputCount, false))
			&& !(extras.size() > 0 && this.moveItemStackTo(stack, extras.first(), extras.end(), false))) {
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
		extras.removed(player);
		StationEntity.close(access, player, inputs, () -> this.clearContainer(player, inputs));
	}
}
