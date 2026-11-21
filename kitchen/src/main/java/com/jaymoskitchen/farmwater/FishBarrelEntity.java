package com.jaymoskitchen.farmwater;

import com.jaymoskitchen.fresh.Freshness;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
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

/** What's in a {@link FishBarrelBlock}: three rows of storage; the fish in it keep longer, and its top shows the catch. */
public class FishBarrelEntity extends RandomizableContainerBlockEntity {
	private static final int SIZE = 27;
	/** Fish packed in the barrel age at this share of the usual speed (the way a cold cellar's food does). */
	static final float BARREL_RATE = 0.5F;
	/** Fish in the barrel (count of items) that make the top show a few, and a good many. */
	static final int FEW = 1, MANY = 64;

	private NonNullList<ItemStack> contents = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	/** The day the barrel was last opened, for the slower ageing; saved. */
	private long seen = -1L;
	private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos pos, BlockState state) {
			playSound(SoundEvents.BARREL_OPEN);
		}

		@Override
		protected void onClose(Level level, BlockPos pos, BlockState state) {
			playSound(SoundEvents.BARREL_CLOSE);
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int before, int after) {
		}

		@Override
		public boolean isOwnContainer(Player player) {
			return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == FishBarrelEntity.this;
		}
	};

	public FishBarrelEntity(BlockPos pos, BlockState state) {
		super(FarmWater.FISH_BARREL_ENTITY, pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("seen", seen);
		if (!trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, contents);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		seen = input.getLongOr("seen", -1L);
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
		return Component.translatable("container.jaymoskitchen.fish_barrel");
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return ChestMenu.threeRows(id, inventory, this);
	}

	private static boolean fish(ItemStack stack) {
		return stack.is(ConventionalItemTags.RAW_FISH_FOODS) || stack.is(ConventionalItemTags.COOKED_FISH_FOODS);
	}

	/** The top shows none, a few or a good many fish. */
	@Override
	public void setChanged() {
		super.setChanged();
		if (level == null || level.isClientSide() || !getBlockState().is(FarmWater.FISH_BARREL)) {
			return;
		}
		int fish = 0;
		for (ItemStack stack : contents) {
			if (fish(stack)) {
				fish += stack.getCount();
			}
		}
		int shown = fish >= MANY ? 2 : fish >= FEW ? 1 : 0;
		if (getBlockState().getValue(FishBarrelBlock.CATCH) != shown) {
			level.setBlock(worldPosition, getBlockState().setValue(FishBarrelBlock.CATCH, shown), 3);
		}
	}

	/** The days since it was last opened count at {@link #BARREL_RATE} for its fish, the rest carried to the next opening. */
	private void age() {
		if (level == null) {
			return;
		}
		long today = Freshness.today(level);
		if (seen < 0 || seen > today) {
			seen = today;
			return;
		}
		long credit = (long)((today - seen) * (1.0F - BARREL_RATE));
		if (credit <= 0) {
			return;
		}
		for (ItemStack stack : contents) {
			Long made = stack.get(Freshness.MADE);
			if (made != null && fish(stack) && Freshness.perishable(stack)) {
				stack.set(Freshness.MADE, Math.min(today, made + credit));
			}
		}
		seen += Math.round(credit / (1.0F - BARREL_RATE));
		super.setChanged();
	}

	@Override
	public void startOpen(ContainerUser user) {
		if (level != null && !remove && !user.getLivingEntity().isSpectator()) {
			age();
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

	private void playSound(SoundEvent sound) {
		level.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, sound, SoundSource.BLOCKS, 0.5F,
			level.getRandom().nextFloat() * 0.1F + 0.9F);
	}
}
