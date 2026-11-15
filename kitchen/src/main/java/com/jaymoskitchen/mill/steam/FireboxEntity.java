package com.jaymoskitchen.mill.steam;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;

/**
 * A firebox's fire (GT New Horizons picks, patch 8): a stack of one fuel, burnt one at a time, each for as long as it
 * burns in a furnace. A boiler on top asks {@link #burning}. Whatever a fuel leaves (a lava bucket's bucket) is pushed
 * out of the fire door.
 */
public class FireboxEntity extends BlockEntity {
	/** The most fuel it holds (one kind, up to a stack). */
	static final int FUEL_MAX = 64;

	private ItemStack fuel = ItemStack.EMPTY;
	/** Ticks the fuel now burning has left. */
	private int burn;

	public FireboxEntity(BlockPos pos, BlockState state) {
		super(Steam.FIREBOX_ENTITY, pos, state);
	}

	public boolean burning() {
		return burn > 0;
	}

	/** Puts as much of {@code held} in as fits (one kind of fuel at a time); false if none went in. */
	boolean stoke(ItemStack held, Player player) {
		if (!(level instanceof ServerLevel server) || burnTicks(server, held) <= 0
			|| !fuel.isEmpty() && !ItemStack.isSameItemSameComponents(fuel, held)) {
			return false;
		}
		int room = Math.min(FUEL_MAX, held.getMaxStackSize()) - fuel.getCount();
		int moved = Math.min(room, held.getCount());
		if (moved <= 0) {
			return false;
		}
		if (fuel.isEmpty()) {
			fuel = held.copyWithCount(moved);
		} else {
			fuel.grow(moved);
		}
		if (!player.hasInfiniteMaterials()) {
			held.shrink(moved);
		}
		setChanged();
		return true;
	}

	/** The fuel not yet burning, taken out. */
	ItemStack rakeOut() {
		ItemStack out = fuel;
		fuel = ItemStack.EMPTY;
		setChanged();
		return out;
	}

	/** How long {@code stack} burns in a furnace (ticks; 0: it doesn't), worked out as a furnace does. */
	int burnTicks(ServerLevel level, ItemStack stack) {
		if (!stack.has(DataComponents.COOKING_FUEL)) {
			return 0;
		}
		LootContext context = new LootContext.Builder(new LootParams.Builder(level)
			.withParameter(LootContextParams.BLOCK_STATE, getBlockState())
			.withParameter(LootContextParams.BLOCK_ENTITY, this)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
			.withParameter(LootContextParams.CONTAINER, new SimpleContainer(stack))
			.create(LootContextParamSets.CONTAINER_PROCESS)).create(Optional.empty());
		return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, FireboxEntity firebox) {
		if (firebox.burn > 0) {
			firebox.burn--;
		}
		if (firebox.burn <= 0 && !firebox.fuel.isEmpty() && level instanceof ServerLevel server) {
			int ticks = firebox.burnTicks(server, firebox.fuel);
			if (ticks > 0) {
				firebox.burn = ticks;
				ItemStackTemplate left = firebox.fuel.getItem().getCraftingRemainder();
				firebox.fuel.shrink(1);
				if (firebox.fuel.isEmpty()) {
					firebox.fuel = ItemStack.EMPTY;
				}
				if (left != null) {
					Direction front = state.getValue(FireboxBlock.FACING);
					Containers.dropItemStack(level, pos.getX() + 0.5 + front.getStepX() * 0.7, pos.getY() + 0.3,
						pos.getZ() + 0.5 + front.getStepZ() * 0.7, left.create());
				}
			} else {
				// not fuel any more (a data pack changed): out it comes
				Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, firebox.fuel);
				firebox.fuel = ItemStack.EMPTY;
			}
			firebox.setChanged();
		}
		boolean lit = firebox.burn > 0;
		if (state.getValue(FireboxBlock.LIT) != lit) {
			level.setBlock(pos, state.setValue(FireboxBlock.LIT, lit), Block.UPDATE_ALL);
		}
	}

	/** Broken: the unburnt fuel falls out. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !level.isClientSide() && !fuel.isEmpty()) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, fuel);
			fuel = ItemStack.EMPTY;
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		fuel = input.read("Fuel", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		burn = input.getIntOr("Burn", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("Fuel", ItemStack.OPTIONAL_CODEC, fuel);
		output.putInt("Burn", burn);
	}
}
