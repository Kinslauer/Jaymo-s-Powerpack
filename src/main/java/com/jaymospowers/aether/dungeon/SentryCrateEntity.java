package com.jaymospowers.aether.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A sentry crate's contents: three rows like a chest, filled from a loot table the first time it is opened, or a mimic in waiting. */
public class SentryCrateEntity extends RandomizableContainerBlockEntity {
	public static final int ROWS = 3;

	private NonNullList<ItemStack> items = NonNullList.withSize(ROWS * 9, ItemStack.EMPTY);
	/** Opened (or broken), this crate is a mimic. */
	private boolean mimic;

	public SentryCrateEntity(BlockPos pos, BlockState state) {
		super(DungeonBlocks.CRATE_ENTITY, pos, state);
	}

	public boolean isMimic() {
		return this.mimic;
	}

	public void setMimic(boolean mimic) {
		this.mimic = mimic;
		this.setChanged();
	}

	@Override
	public int getContainerSize() {
		return ROWS * 9;
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
		return Component.translatable("block.jaymospowers.sentry_crate");
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return ChestMenu.threeRows(id, inventory, this);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		if (!this.tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, this.items);
		}
		this.mimic = input.getBooleanOr("mimic", false);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!this.trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, this.items);
		}
		output.putBoolean("mimic", this.mimic);
	}
}
