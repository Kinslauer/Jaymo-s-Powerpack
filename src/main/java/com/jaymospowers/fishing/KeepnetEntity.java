package com.jaymospowers.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Track D: the keepnet (Reel to Fish's idea, our own code). A net hung in the water by your swim: fish put in it stay
 * alive as long as it's in the water, so the day's catch can be weighed, shown off at a contest, or let go at the end of
 * the day, and none has to die for it. Nine fish.
 */
public class KeepnetEntity extends BlockEntity {
	public static final int SIZE = 9;
	static final int CHECK_EVERY = 20;

	private final SimpleContainer items = new SimpleContainer(SIZE) {
		@Override
		public void setChanged() {
			super.setChanged();
			KeepnetEntity.this.setChanged();
		}
	};

	public KeepnetEntity(BlockPos pos, BlockState state) {
		super(FishingBlocks.KEEPNET_ENTITY, pos, state);
	}

	InteractionResult open(Player player) {
		if (level != null && !level.isClientSide()) {
			player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, items, 1),
				Component.translatable("block.jaymospowers.keepnet")));
		}
		return InteractionResult.SUCCESS;
	}

	/** In the water, every fish in the net is kept alive (its time out of the water starts over). */
	public static void serverTick(Level level, BlockPos pos, BlockState state, KeepnetEntity net) {
		if (level.getGameTime() % CHECK_EVERY != 0 || !state.getValue(FishingBlocks.WATERLOGGED)) {
			return;
		}
		long until = level.getGameTime() + FishCatch.ALIVE_TICKS;
		for (int i = 0; i < SIZE; i++) {
			ItemStack stack = net.items.getItem(i);
			FishCatch.Info info = FishCatch.info(stack);
			if (info != null && info.alive(level.getGameTime()) && info.aliveUntil() < until - CHECK_EVERY) {
				stack.set(FishCatch.CATCH, info.withAliveUntil(until));
			}
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropContents(level, pos, items);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.clearContent();
		ContainerHelper.loadAllItems(input, items.getItems());
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items.getItems());
	}
}
