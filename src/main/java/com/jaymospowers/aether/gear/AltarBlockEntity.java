package com.jaymospowers.aether.gear;

import com.jaymospowers.aether.AetherItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The altar's contents: slot 0 is the input, 1 to 8 take ambrosium shards, 9 is the output. Each tick it looks up the
 * recipe for the input ({@link AltarRecipes}); with the first N shard slots filled (N the recipe's) and room for the result
 * it works, {@link #PROCESS_TICKS} ticks; otherwise the work drains away at {@link #DECAY} a tick. Changing the input
 * starts over. When done it uses one shard from each of the N slots and one input item, and puts the result in the output.
 */
public class AltarBlockEntity extends BlockEntity implements Container {
	// ---- tuning ----
	public static final int PROCESS_TICKS = 200, DECAY = 2;
	public static final int INPUT = 0, FUEL_FIRST = 1, FUEL_COUNT = 8, OUTPUT = 9, SIZE = 10;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int progress;
	/** What the work in progress is for, to notice a change. */
	private ItemStack watched = ItemStack.EMPTY;
	/** Shards the current recipe takes (0 = no recipe), for the screen. */
	private int needed;

	/** What the screen shows: work done, work needed, shards needed. */
	public final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> progress;
				case 1 -> PROCESS_TICKS;
				default -> needed;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return 3;
		}
	};

	public AltarBlockEntity(BlockPos pos, BlockState state) {
		super(AetherGear.ALTAR_ENTITY, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, AltarBlockEntity altar) {
		altar.tick(level);
	}

	private void tick(Level level) {
		ItemStack input = items.get(INPUT);
		AltarRecipes.Recipe recipe = AltarRecipes.find(input);
		int before = progress, neededBefore = needed;
		needed = recipe == null ? 0 : recipe.fuel();
		if (!ItemStack.isSameItemSameComponents(watched, input)) {
			watched = input.copy();
			progress = 0;
		}
		if (recipe == null) {
			progress = 0;
		} else if (fueled(recipe.fuel()) && room(recipe.result())) {
			progress++;
			if (progress >= PROCESS_TICKS) {
				finish(recipe);
			}
		} else {
			progress = Math.max(0, progress - DECAY);
		}
		if (progress != before || needed != neededBefore) {
			setChanged();
		}
	}

	/** Whether the first {@code n} shard slots each hold a shard. */
	private boolean fueled(int n) {
		for (int i = 0; i < n; i++) {
			ItemStack shard = items.get(FUEL_FIRST + i);
			if (shard.isEmpty() || !shard.is(AetherItems.AMBROSIUM_SHARD)) {
				return false;
			}
		}
		return true;
	}

	/** Whether the output slot can take the result. */
	private boolean room(ItemStack result) {
		ItemStack out = items.get(OUTPUT);
		return out.isEmpty() || ItemStack.isSameItemSameComponents(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize();
	}

	private void finish(AltarRecipes.Recipe recipe) {
		ItemStack out = items.get(OUTPUT);
		if (out.isEmpty()) {
			items.set(OUTPUT, recipe.result().copy());
		} else {
			out.grow(recipe.result().getCount());
		}
		items.get(INPUT).shrink(1);
		for (int i = 0; i < recipe.fuel(); i++) {
			items.get(FUEL_FIRST + i).shrink(1);
		}
		progress = 0;
		watched = items.get(INPUT).copy();
		if (level instanceof ServerLevel server) {
			BlockPos pos = getBlockPos();
			server.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 24, 0.4, 0.3, 0.4, 0.5);
			server.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.1F);
		}
		setChanged();
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
		items.replaceAll(stack -> ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		progress = input.getIntOr("progress", 0);
		watched = items.get(INPUT).copy();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items, true);
		output.putInt("progress", progress);
	}

	// --- as a container (the screen's slots) ---

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, count);
		setChanged();
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == OUTPUT) {
			return false;
		}
		return slot == INPUT || stack.is(AetherItems.AMBROSIUM_SHARD);
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.replaceAll(stack -> ItemStack.EMPTY);
		setChanged();
	}
}
