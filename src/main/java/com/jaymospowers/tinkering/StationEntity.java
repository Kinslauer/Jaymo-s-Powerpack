package com.jaymospowers.tinkering;

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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * What's left on a Part Builder, Tool Station, Tool Forge or Crafting Station: like the pack's crafting tables (Jaymo's Kitchen), the inputs stay
 * on the table when you close it, everyone can see them lying on top, and only one player works it at a time.
 */
public class StationEntity extends BlockEntity {
	/**
	 * Enough for any table: the Part Builder uses 2 (pattern, material), the Tool Station 4, the Tool Forge 5, the Crafting
	 * Station 9. (Old saves hold up to 4 and load into the first slots.)
	 */
	public static final int SIZE = 9;
	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	/** Who's working the table right now (never saved). */
	private @Nullable UUID user;

	public StationEntity(BlockPos pos, BlockState state) {
		super(Tinkering.STATION_ENTITY, pos, state);
	}

	public List<ItemStack> items() {
		return items;
	}

	/** Lays out what the screen has, and shows everyone. */
	public void store(List<ItemStack> from) {
		for (int i = 0; i < SIZE; i++) {
			items.set(i, i < from.size() ? from.get(i).copy() : ItemStack.EMPTY);
		}
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	/** Someone else is working the table (and still has its screen open). */
	public boolean busyFor(Player player) {
		if (user == null || user.equals(player.getUUID())) {
			return false;
		}
		ServerPlayer other = level instanceof ServerLevel server ? server.getServer().getPlayerList().getPlayer(user) : null;
		AbstractContainerMenu open = other == null ? null : other.containerMenu;
		if (!(open instanceof PartBuilderMenu) && !(open instanceof ToolStationMenu) && !(open instanceof com.jaymospowers.tinkering.table.CraftingStationMenu)) {
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

	/** A table's screen opens: lay out what was left on it, and hold it for this player. */
	public static void open(ContainerLevelAccess access, Player player, Container inputs) {
		access.execute((level, pos) -> {
			if (level.getBlockEntity(pos) instanceof StationEntity table) {
				table.lock(player);
				List<ItemStack> left = table.items().stream().map(ItemStack::copy).toList();
				for (int i = 0; i < inputs.getContainerSize() && i < left.size(); i++) {
					inputs.setItem(i, left.get(i));
				}
			}
		});
	}

	/** The screen's inputs changed: keep the table in step. */
	public static void changed(ContainerLevelAccess access, Container inputs) {
		access.execute((level, pos) -> {
			if (level.getBlockEntity(pos) instanceof StationEntity table && !table.isRemoved()) {
				table.store(inputs(inputs));
			}
		});
	}

	/** The screen closes: the inputs stay on the table, or come back to the player if the table is gone. */
	public static void close(ContainerLevelAccess access, Player player, Container inputs, Runnable handBack) {
		access.execute((level, pos) -> {
			if (level.getBlockEntity(pos) instanceof StationEntity table && !table.isRemoved()) {
				table.store(inputs(inputs));
				for (int i = 0; i < inputs.getContainerSize(); i++) {
					inputs.removeItemNoUpdate(i);
				}
				table.unlock(player);
			} else {
				handBack.run();
			}
		});
	}

	private static List<ItemStack> inputs(Container inputs) {
		NonNullList<ItemStack> list = NonNullList.withSize(inputs.getContainerSize(), ItemStack.EMPTY);
		for (int i = 0; i < list.size(); i++) {
			list.set(i, inputs.getItem(i));
		}
		return list;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// if someone has it open, closing it hands them the items instead
		if (level != null && user == null) {
			Containers.dropContents(level, pos, items);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.clear();
		ContainerHelper.loadAllItems(input, items);
		if (level != null && level.isClientSide()) {
			// redraw the chunk section, so a table made by this update is drawn with what's on it
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items, true);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		ContainerHelper.saveAllItems(output, items, true);
		return output.buildResult();
	}
}
