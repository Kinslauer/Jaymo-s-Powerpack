package com.jaymospowers.tinkering.table;

import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.jaymospowers.tinkering.smeltery.CastItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * (Tinkers 2's chests) The Pattern Chest holds only patterns and casts, the Part Chest only tool parts and weapon heads;
 * hoppers and shift-clicking respect that too. What's inside drops when it is broken.
 */
public class TinkerChestEntity extends BaseContainerBlockEntity {
	public static final int ROWS = 6, SIZE = ROWS * 9;
	private final boolean parts;
	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	public TinkerChestEntity(BlockPos pos, BlockState state) {
		super(Tables.CHEST_ENTITY, pos, state);
		this.parts = state.is(Tables.PART_CHEST);
	}

	public static boolean isPattern(ItemStack stack) {
		return stack.is(Tinkering.PATTERN) || stack.getItem() instanceof CastItem;
	}

	public static boolean isPart(ItemStack stack) {
		return stack.getItem() instanceof ToolPartItem || stack.getItem() instanceof WeaponPartItem;
	}

	/** Which {@link TableExtras} filter this chest is. */
	public int filterId() {
		return parts ? TableExtras.FILTER_PARTS : TableExtras.FILTER_PATTERNS;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return parts ? isPart(stack) : isPattern(stack);
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable(parts ? "container.jaymospowers.part_chest" : "container.jaymospowers.pattern_chest");
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x6, id, inventory, this, ROWS);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropContents(level, pos, items);
		}
	}
}
