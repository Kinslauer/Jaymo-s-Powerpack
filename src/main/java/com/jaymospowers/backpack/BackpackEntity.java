package com.jaymospowers.backpack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A set-down backpack's contents. They travel with it as the item's container component when it is picked up. */
public class BackpackEntity extends BaseContainerBlockEntity {
	private NonNullList<ItemStack> items = NonNullList.withSize(Backpack.ROWS * 9, ItemStack.EMPTY);

	public BackpackEntity(BlockPos pos, BlockState state) {
		super(Backpack.ENTITY, pos, state);
	}

	@Override
	public int getContainerSize() {
		return Backpack.ROWS * 9;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.jaymospowers.backpack");
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return BackpackMenu.placed(id, inventory, this);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return BackpackMenu.fits(stack);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items, false);
	}

	/** The backpack as an item, holding what this one held; this one is left empty (so removing it spills nothing). */
	ItemStack pickUp() {
		ItemStack stack = new ItemStack(Backpack.ITEM);
		stack.applyComponents(this.collectComponents());
		this.clearContent();
		this.setChanged();
		return stack;
	}
}
