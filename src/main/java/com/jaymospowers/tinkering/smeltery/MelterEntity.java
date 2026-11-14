package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.kiln.Heat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The melter: a one-block smeltery for a small start. It melts up to {@link #SLOTS} items at a time over a fire set under it
 * (anything the kiln counts as heat: a campfire, fire, lava, magma, a lit forge hearth; hotter melts faster), holds
 * {@link #CAPACITY} of metal and gives it to a faucet on its side. It does not alloy: the metals lie in it one over another.
 */
public class MelterEntity extends MoltenEntity {
	public static final int SLOTS = 3;
	/** What it holds (twelve ingots' worth). */
	public static final int CAPACITY = Molten.INGOT * 12;
	/** Heat of 1 (a campfire, fire) melts one tick in this many; each point more, one more. */
	static final int SLOW = 2;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	private final int[] progress = new int[SLOTS];
	private int ticks;

	public MelterEntity(BlockPos pos, BlockState state) {
		super(SmelteryExtras.MELTER_ENTITY, pos, state, CAPACITY);
	}

	public NonNullList<ItemStack> items() {
		return items;
	}

	/** Sets one item in a free slot; false when it can't be melted or the melter is full. */
	public boolean insert(ItemStack stack) {
		if (Melting.of(stack) == null) {
			return false;
		}
		for (int i = 0; i < SLOTS; i++) {
			if (items.get(i).isEmpty()) {
				items.set(i, stack.split(1));
				progress[i] = 0;
				changed();
				return true;
			}
		}
		return false;
	}

	/** Takes back the last item not yet melted (empty when there is none). */
	public ItemStack take() {
		for (int i = SLOTS - 1; i >= 0; i--) {
			if (!items.get(i).isEmpty()) {
				ItemStack taken = items.get(i);
				items.set(i, ItemStack.EMPTY);
				progress[i] = 0;
				changed();
				return taken;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	protected void work(ServerLevel level, BlockState state) {
		int heat = Heat.at(level, worldPosition.below());
		int speed = heat <= 0 ? 0 : heat >= SLOW ? heat - SLOW + 1 : ++ticks % SLOW == 0 ? 1 : 0;
		boolean melting = false;
		for (int i = 0; i < SLOTS; i++) {
			ItemStack stack = items.get(i);
			Melting.Result result = stack.isEmpty() ? null : Melting.of(stack);
			if (result == null) {
				continue;
			}
			melting |= heat > 0;
			if (progress[i] < result.ticks()) {
				progress[i] = Math.min(result.ticks(), progress[i] + speed);
			} else if (tank.space() >= result.total()) {
				for (Melting.Input part : result.parts()) {
					tank.fill(part.molten(), part.amount(), false);
				}
				stack.shrink(1);
				progress[i] = 0;
				changed();
			}
		}
		if (state.getValue(MelterBlock.LIT) != melting) {
			level.setBlock(worldPosition, state.setValue(MelterBlock.LIT, melting), Block.UPDATE_ALL);
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
		items.clear();
		ContainerHelper.loadAllItems(input, items);
		int[] saved = input.getIntArray("progress").orElse(new int[0]);
		System.arraycopy(saved, 0, progress, 0, Math.min(saved.length, progress.length));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items, true);
		output.putIntArray("progress", progress.clone());
	}
}
