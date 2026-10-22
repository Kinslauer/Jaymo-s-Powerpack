package com.jaymospowers.tinkering.table;

import com.jaymospowers.tinkering.StationBlock;
import com.jaymospowers.tinkering.StationEntity;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The Crafting Station: a 3x3 crafting table with vanilla's recipes that keeps its grid on the table ({@link StationEntity}),
 * with the side inventory and tabs of every Tinkers table ({@link TableExtras}).
 */
public class CraftingStationMenu extends AbstractContainerMenu implements TinkerTableMenu {
	public static final int RESULT = 0, GRID = 9, GRID_START = 1, PLAYER_START = 10, PLAYER_END = 46;
	private final ContainerLevelAccess access;
	private final Player player;
	private final TableExtras extras;
	private final TransientCraftingContainer grid = new TransientCraftingContainer(this, 3, 3);
	private final ResultContainer result = new ResultContainer();

	/** Client side. */
	public CraftingStationMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public CraftingStationMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(Tables.CRAFTING_STATION_MENU, id);
		this.access = access;
		this.player = inventory.player;
		this.addSlot(new ResultSlot(player, grid, result, 0, 124, 35));
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				this.addSlot(new Slot(grid, column + row * 3, 30 + column * 18, 17 + row * 18));
			}
		}
		this.addStandardInventorySlots(inventory, 8, 84);
		this.extras = new TableExtras(access, player, StationBlock.Kind.CRAFTING_STATION);
		extras.slots().forEach(this::addSlot);
		extras.data().forEach(this::addDataSlot);
		StationEntity.open(access, player, grid);
	}

	@Override
	public TableExtras extras() {
		return extras;
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (container == grid) {
			update();
			StationEntity.changed(access, grid);
		}
	}

	/** The recipe the grid makes (vanilla's crafting recipes), as a crafting table does. */
	@SuppressWarnings("unchecked")
	private void update() {
		if (!(player.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer server)) {
			return;
		}
		CraftingInput input = grid.asCraftInput();
		ItemStack out = ItemStack.EMPTY;
		Optional<RecipeHolder<CraftingRecipe>> found = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level,
			(RecipeHolder<CraftingRecipe>) result.getRecipeUsed());
		if (found.isPresent() && result.setRecipeUsed(server, found.get())) {
			ItemStack made = found.get().value().assemble(input);
			if (made.isItemEnabled(level.enabledFeatures())) {
				out = made;
			}
		}
		result.setItem(0, out);
		broadcastChanges();
	}

	@Override
	public boolean clickMenuButton(Player player, int button) {
		return extras.clickTab(player, button);
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, StationBlock.Kind.CRAFTING_STATION.block()) && extras.stillValid(player);
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
		if (index == RESULT) {
			stack.getItem().onCraftedBy(stack, player);
			if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, moved);
		} else if (index >= extras.first()) {
			// from the side inventory: into the grid, else to the player
			if (!this.moveItemStackTo(stack, GRID_START, GRID_START + GRID, false) && !this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (index < PLAYER_START) {
			if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, GRID_START, GRID_START + GRID, false)
			&& !(extras.size() > 0 && this.moveItemStackTo(stack, extras.first(), extras.end(), false))) {
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
		slot.onTake(player, stack);
		if (index == RESULT) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
		return moved;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		extras.removed(player);
		StationEntity.close(access, player, grid, () -> this.clearContainer(player, grid));
	}
}
