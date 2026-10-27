package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** (Farmer's Delight's cabinet, MIT.) What's in a {@link CabinetBlock}: three rows of storage, opened with the barrel's sounds. */
public class CabinetBlockEntity extends RandomizableContainerBlockEntity {
	/** Three rows of nine. */
	private static final int SIZE = 27;
	/** Pitch wobble of the door's sound. */
	private static final float PITCH_VARIATION = 0.1F;

	private NonNullList<ItemStack> contents = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos pos, BlockState state) {
			playSound(state, SoundEvents.BARREL_OPEN);
			setOpen(state, true);
		}

		@Override
		protected void onClose(Level level, BlockPos pos, BlockState state) {
			playSound(state, SoundEvents.BARREL_CLOSE);
			setOpen(state, false);
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int before, int after) {
		}

		@Override
		public boolean isOwnContainer(Player player) {
			if (player.containerMenu instanceof ChestMenu menu) {
				Container container = menu.getContainer();
				return container == CabinetBlockEntity.this;
			}
			return false;
		}
	};

	public CabinetBlockEntity(BlockPos pos, BlockState state) {
		super(DelightHome.CABINET_ENTITY, pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, contents);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		contents = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		if (!tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, contents);
		}
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return contents;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		contents = items;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.jaymoskitchen.cabinet");
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return ChestMenu.threeRows(id, inventory, this);
	}

	@Override
	public void startOpen(ContainerUser user) {
		if (level != null && !remove && !user.getLivingEntity().isSpectator()) {
			openers.incrementOpeners(user.getLivingEntity(), level, getBlockPos(), getBlockState(), user.getContainerInteractionRange());
		}
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (level != null && !remove && !user.getLivingEntity().isSpectator()) {
			openers.decrementOpeners(user.getLivingEntity(), level, getBlockPos(), getBlockState());
		}
	}

	public void recheckOpen() {
		if (level != null && !remove) {
			openers.recheckOpeners(level, getBlockPos(), getBlockState());
		}
	}

	private void setOpen(BlockState state, boolean open) {
		if (level != null) {
			level.setBlock(getBlockPos(), state.setValue(CabinetBlock.OPEN, open), 3);
		}
	}

	/** The door's sound, from the cabinet's front. */
	private void playSound(BlockState state, SoundEvent sound) {
		Vec3i front = state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).getUnitVec3i();
		double x = worldPosition.getX() + 0.5 + front.getX() / 2.0;
		double y = worldPosition.getY() + 0.5 + front.getY() / 2.0;
		double z = worldPosition.getZ() + 0.5 + front.getZ() / 2.0;
		level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * PITCH_VARIATION + 0.9F);
	}
}
