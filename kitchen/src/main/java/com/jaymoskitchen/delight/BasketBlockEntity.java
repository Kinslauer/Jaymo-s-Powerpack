package com.jaymoskitchen.delight;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's basket, MIT.) What's in a {@link BasketBlock}: 27 slots, and a reach. Each time it's off cooldown
 * it takes in one item lying in the block its open side faces (and the one behind it), as a hopper would.
 */
public class BasketBlockEntity extends RandomizableContainerBlockEntity {
	/** Three rows of nine. */
	private static final int SIZE = 27;
	/** Ticks between pick-ups. */
	private static final int COOLDOWN = 8;
	/** Where it reaches, for each way it can face (down, up, north, south, west, east), in 1/16ths of a block. */
	private static final VoxelShape[] REACH = {
		Block.box(0.0, -16.0, 0.0, 16.0, 16.0, 16.0),
		Block.box(0.0, 0.0, 0.0, 16.0, 32.0, 16.0),
		Block.box(0.0, 0.0, -16.0, 16.0, 16.0, 16.0),
		Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 32.0),
		Block.box(-16.0, 0.0, 0.0, 16.0, 16.0, 16.0),
		Block.box(0.0, 0.0, 0.0, 32.0, 16.0, 16.0)
	};

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int cooldown = -1;

	public BasketBlockEntity(BlockPos pos, BlockState state) {
		super(DelightHome.BASKET_ENTITY, pos, state);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		if (!tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, items);
		}
		cooldown = input.getIntOr("TransferCooldown", -1);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, items);
		}
		output.putInt("TransferCooldown", cooldown);
	}

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public ItemStack removeItem(int index, int count) {
		unpackLootTable(null);
		return ContainerHelper.removeItem(getItems(), index, count);
	}

	@Override
	public void setItem(int index, ItemStack stack) {
		unpackLootTable(null);
		getItems().set(index, stack);
		if (stack.getCount() > getMaxStackSize()) {
			stack.setCount(getMaxStackSize());
		}
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.jaymoskitchen.basket");
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
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return ChestMenu.threeRows(id, inventory, this);
	}

	private boolean isFull() {
		for (ItemStack stack : items) {
			if (stack.isEmpty() || stack.getCount() != stack.getMaxStackSize()) {
				return false;
			}
		}
		return true;
	}

	public static void pushItemsTick(Level level, BlockPos pos, BlockState state, BasketBlockEntity basket) {
		if (--basket.cooldown > 0) {
			return;
		}
		basket.cooldown = 0;
		if (!state.getValue(BasketBlock.ENABLED) || basket.isFull()) {
			return;
		}
		int facing = state.getValue(BasketBlock.FACING).get3DDataValue();
		if (basket.collectItems(level, pos, facing)) {
			basket.cooldown = COOLDOWN;
			basket.setChanged();
		}
	}

	/** Takes in the first item lying in reach; whether it took one. */
	private boolean collectItems(Level level, BlockPos pos, int facing) {
		for (ItemEntity entity : itemsInReach(level, pos, facing)) {
			ItemStack rest = insert(entity.getItem().copy());
			if (rest.isEmpty()) {
				entity.discard();
				return true;
			}
			entity.setItem(rest);
		}
		return false;
	}

	private static List<ItemEntity> itemsInReach(Level level, BlockPos pos, int facing) {
		return REACH[facing].toAabbs().stream()
			.flatMap(box -> level.getEntitiesOfClass(ItemEntity.class, box.move(pos.getX(), pos.getY(), pos.getZ()), EntitySelector.ENTITY_STILL_ALIVE).stream())
			.toList();
	}

	/** Puts as much of {@code stack} as fits into the slots; what's left over. */
	private ItemStack insert(ItemStack stack) {
		for (int slot = 0; slot < items.size() && !stack.isEmpty(); slot++) {
			ItemStack held = items.get(slot);
			if (!canPlaceItem(slot, stack)) {
				continue;
			}
			if (held.isEmpty()) {
				setItem(slot, stack);
				stack = ItemStack.EMPTY;
			} else if (ItemStack.isSameItemSameComponents(held, stack)) {
				int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - held.getCount());
				stack.shrink(moved);
				held.grow(moved);
			}
		}
		setChanged();
		return stack;
	}
}
