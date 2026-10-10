package com.jaymoskitchen.table;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * What's laid out on a crafting table: the 3x3 grid stays put when you close the table (until it's crafted or taken
 * off), everyone can see it lying on top, and only one player can work the table at a time. The vanilla crafting
 * table gets this through {@code CraftingTableBlockMixin} and {@code CraftingMenuMixin}.
 */
public class CraftingTableEntity extends BlockEntity implements Container {
	private final NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
	/** Who's working the table right now (never saved). */
	private @Nullable UUID user;

	public CraftingTableEntity(BlockPos pos, BlockState state) {
		super(Tables.CRAFTING_TABLE, pos, state);
	}

	public List<ItemStack> grid() {
		return grid;
	}

	/** Lays out the grid as the crafting screen has it, and shows everyone. */
	public void store(List<ItemStack> items) {
		for (int i = 0; i < grid.size(); i++) {
			grid.set(i, i < items.size() ? items.get(i).copy() : ItemStack.EMPTY);
		}
		markUpdated();
	}

	/** Someone else is working the table (and still has it open). */
	public boolean busyFor(Player player) {
		if (user == null || user.equals(player.getUUID())) {
			return false;
		}
		ServerPlayer other = level instanceof ServerLevel server ? server.getServer().getPlayerList().getPlayer(user) : null;
		if (other == null || !(other.containerMenu instanceof CraftingMenu)) {
			// they left, or closed it some way we didn't hear about
			user = null;
			return false;
		}
		return true;
	}

	public void lock(Player player) {
		user = player.getUUID();
	}

	public void unlock(Player player) {
		if (player.getUUID().equals(user)) {
			user = null;
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// if someone has it open, closing it hands them the grid instead
		if (level != null && user == null) {
			Containers.dropContents(level, pos, grid);
		}
	}

	private void markUpdated() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		grid.clear();
		ContainerHelper.loadAllItems(input, grid);
		if (level != null && level.isClientSide()) {
			// redraw the chunk section, so the table is drawn with what's on it (a table made by this update wasn't)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, grid, true);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		ContainerHelper.saveAllItems(output, grid, true);
		return output.buildResult();
	}

	// --- as a container (so the grid spills when the table's broken) ---

	@Override
	public int getContainerSize() {
		return grid.size();
	}

	@Override
	public boolean isEmpty() {
		return grid.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return grid.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack removed = ContainerHelper.removeItem(grid, slot, count);
		markUpdated();
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(grid, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		grid.set(slot, stack);
		markUpdated();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		grid.clear();
	}
}
