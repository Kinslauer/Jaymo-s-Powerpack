package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.Tinkering;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The smeltery's brain, in its controller. It checks the structure behind it (a seared floor and walls around an open
 * space up to {@link #MAX_SIZE} across and {@link #MAX_HEIGHT} high), melts what's put in (one item a slot, a slot a
 * block of space up to {@link #MAX_SLOTS}), burns lava for heat, alloys what it can, and holds the molten pool for the
 * drains' faucets. Things that fall in get melted too: items go into free slots, creatures cook into blood.
 */
public class SmelteryEntity extends BlockEntity implements Container {
	/** Interior size across (each way) and up. */
	public static final int MAX_SIZE = 7, MAX_HEIGHT = 8;
	/** Item slots at most (one for each block of space inside). */
	public static final int MAX_SLOTS = 24;
	public static final int FUEL_SLOT = MAX_SLOTS, BUCKET_SLOT = MAX_SLOTS + 1, SIZE = MAX_SLOTS + 2;
	/** Molten metal each block of space holds (a block's worth). */
	static final int CAPACITY_PER_BLOCK = Molten.BLOCK;
	/** Lava it can hold for fuel (mB), what a bucket adds, and how many ticks of melting 1 mB lasts. */
	public static final int FUEL_MAX = 4000, FUEL_BUCKET = 1000, TICKS_PER_FUEL = 8;
	/** Alloy batches a tick at most. */
	static final int ALLOY_BATCHES = 2;
	/** How often the structure is checked (ticks), and the pool sent to players at most. */
	static final int CHECK_TICKS = 20, SYNC_TICKS = 5;
	/** Creatures inside: damage every second, and the blood each hit cooks out of them. */
	static final float COOK_DAMAGE = 2.0F;
	static final int COOK_BLOOD = 20;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private final int[] progress = new int[MAX_SLOTS];
	private final Tank tank = new Tank();
	private int fuel;
	private int fuelTicks;
	private boolean formed;
	/** Inside corners (inclusive); valid while formed. */
	private BlockPos low = BlockPos.ZERO, high = BlockPos.ZERO;
	private int slots;
	private int ticks;
	private boolean dirty;

	/** What the screen shows: each slot's melting progress (0-100, or -1 when it can't melt), fuel, slots in use. */
	public final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			if (index < MAX_SLOTS) {
				return meltPercent(index);
			}
			return index == MAX_SLOTS ? fuel : slots;
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return MAX_SLOTS + 2;
		}
	};

	public SmelteryEntity(BlockPos pos, BlockState state) {
		super(Tinkering.SMELTERY_ENTITY, pos, state);
	}

	public Tank tank() {
		return tank;
	}

	public boolean formed() {
		return formed;
	}

	public BlockPos low() {
		return low;
	}

	public BlockPos high() {
		return high;
	}

	public int fuel() {
		return fuel;
	}

	private int meltPercent(int slot) {
		ItemStack stack = items.get(slot);
		Melting.Result result = stack.isEmpty() ? null : Melting.of(stack);
		if (result == null) {
			return stack.isEmpty() ? 0 : -1;
		}
		return Math.min(100, progress[slot] * 100 / result.ticks());
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SmelteryEntity smeltery) {
		smeltery.tick((ServerLevel) level, state);
	}

	private void tick(ServerLevel level, BlockState state) {
		if (ticks++ % CHECK_TICKS == 0) {
			checkStructure(level, state);
		}
		takeFuel();
		boolean melting = false;
		if (formed && fuel > 0) {
			for (int i = 0; i < slots; i++) {
				melting |= melt(i);
			}
			if (ticks % 20 == 0) {
				cookInside(level);
			}
		}
		if (melting && ++fuelTicks >= TICKS_PER_FUEL) {
			fuelTicks = 0;
			fuel--;
			dirty = true;
		}
		if (formed) {
			alloy();
		}
		boolean lit = formed && fuel > 0;
		if (state.getValue(SmelteryBlock.LIT) != lit) {
			level.setBlock(worldPosition, state.setValue(SmelteryBlock.LIT, lit), Block.UPDATE_ALL);
		}
		if (dirty && ticks % SYNC_TICKS == 0) {
			dirty = false;
			setChanged();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** A lava bucket in the fuel slot goes in if there's room, and the empty bucket comes out. */
	private void takeFuel() {
		ItemStack in = items.get(FUEL_SLOT);
		ItemStack out = items.get(BUCKET_SLOT);
		if (in.is(Items.LAVA_BUCKET) && fuel + FUEL_BUCKET <= FUEL_MAX
			&& (out.isEmpty() || out.is(Items.BUCKET) && out.getCount() < out.getMaxStackSize())) {
			fuel += FUEL_BUCKET;
			items.set(FUEL_SLOT, ItemStack.EMPTY);
			if (out.isEmpty()) {
				items.set(BUCKET_SLOT, new ItemStack(Items.BUCKET));
			} else {
				out.grow(1);
			}
			dirty = true;
		}
	}

	/** One tick of melting a slot; true if it's heating something. */
	private boolean melt(int slot) {
		ItemStack stack = items.get(slot);
		Melting.Result result = stack.isEmpty() ? null : Melting.of(stack);
		if (result == null) {
			progress[slot] = 0;
			return false;
		}
		if (progress[slot] < result.ticks()) {
			progress[slot]++;
			return true;
		}
		// (0.13) every liquid it holds (iron and gold for a paladin's helmet) goes in at once, or it waits for room
		if (tank.space() >= result.total()) {
			for (Melting.Input part : result.parts()) {
				tank.fill(part.molten(), part.amount(), false);
			}
			stack.shrink(1);
			progress[slot] = 0;
			dirty = true;
		}
		return false;
	}

	private void alloy() {
		for (Melting.Alloy alloy : Melting.ALLOYS) {
			for (int batch = 0; batch < ALLOY_BATCHES; batch++) {
				boolean ready = true;
				int used = 0;
				for (Melting.Input input : alloy.inputs()) {
					ready &= tank.amount(input.molten()) >= input.amount();
					used += input.amount();
				}
				if (!ready || alloy.amount() - used > tank.space()) {
					break;
				}
				for (Melting.Input input : alloy.inputs()) {
					tank.drain(input.molten(), input.amount(), false);
				}
				tank.fill(alloy.output(), alloy.amount(), false);
				dirty = true;
			}
		}
	}

	/** Items that fall in go into free slots; creatures inside cook, and what they lose becomes blood. */
	private void cookInside(ServerLevel level) {
		AABB inside = new AABB(low.getX(), low.getY(), low.getZ(), high.getX() + 1, high.getY() + 1, high.getZ() + 1);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, inside)) {
			ItemStack stack = item.getItem();
			for (int i = 0; i < slots && !stack.isEmpty(); i++) {
				if (items.get(i).isEmpty() && Melting.of(stack) != null) {
					items.set(i, stack.split(1));
					progress[i] = 0;
				}
			}
			if (stack.isEmpty()) {
				item.discard();
			}
		}
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, inside, LivingEntity::isAlive)) {
			if (living.fireImmune()) {
				continue;
			}
			if (living.hurtServer(level, level.damageSources().lava(), COOK_DAMAGE) && !(living instanceof Player)) {
				tank.fill(Molten.BLOOD, COOK_BLOOD, false);
				dirty = true;
			}
		}
	}

	// --- the structure ---

	private void checkStructure(ServerLevel level, BlockState state) {
		Direction inward = state.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
		Direction side = inward.getClockWise();
		BlockPos start = worldPosition.relative(inward);
		boolean ok = level.isEmptyBlock(start);
		int left = 0, right = 0, depth = 0;
		if (ok) {
			left = reach(level, start, side.getOpposite());
			right = reach(level, start, side);
			depth = reach(level, start, inward) + 1;
			ok = left + right + 1 <= MAX_SIZE && depth <= MAX_SIZE;
		}
		int height = 0;
		List<BlockPos> drains = new ArrayList<>();
		if (ok) {
			// the floor under the bottom layer
			for (int d = 0; d < depth && ok; d++) {
				for (int s = -left; s <= right && ok; s++) {
					ok = level.getBlockState(start.relative(inward, d).relative(side, s).below()).is(Tinkering.SEARED_BRICKS);
				}
			}
			while (ok && height < MAX_HEIGHT && layer(level, start.above(height), inward, side, left, right, depth, drains)) {
				height++;
			}
			ok = height > 0;
		}
		boolean was = formed;
		formed = ok;
		if (ok) {
			BlockPos a = start.relative(side, -left), b = start.relative(inward, depth - 1).relative(side, right).above(height - 1);
			low = new BlockPos(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()));
			high = new BlockPos(Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()));
			int volume = (left + right + 1) * depth * height;
			slots = Math.min(volume, MAX_SLOTS);
			tank.setCapacity(volume * CAPACITY_PER_BLOCK);
			for (BlockPos drain : drains) {
				if (level.getBlockEntity(drain) instanceof DrainEntity entity) {
					entity.setController(worldPosition);
				}
			}
		} else {
			slots = 0;
		}
		if (was != formed || ok) {
			dirty = true;
		}
	}

	/** Open blocks from here that way (not counting this one), up to the size limit. */
	private static int reach(Level level, BlockPos from, Direction way) {
		int n = 0;
		while (n < MAX_SIZE && level.isEmptyBlock(from.relative(way, n + 1))) {
			n++;
		}
		return n;
	}

	/** One layer: open inside, seared walls all round (corners don't matter). */
	private boolean layer(Level level, BlockPos start, Direction inward, Direction side, int left, int right, int depth,
		List<BlockPos> drains) {
		for (int d = 0; d < depth; d++) {
			for (int s = -left; s <= right; s++) {
				if (!level.isEmptyBlock(start.relative(inward, d).relative(side, s))) {
					return false;
				}
			}
			if (!wall(level, start.relative(inward, d).relative(side, -left - 1), drains)
				|| !wall(level, start.relative(inward, d).relative(side, right + 1), drains)) {
				return false;
			}
		}
		for (int s = -left; s <= right; s++) {
			if (!wall(level, start.relative(inward, -1).relative(side, s), drains)
				|| !wall(level, start.relative(inward, depth).relative(side, s), drains)) {
				return false;
			}
		}
		return true;
	}

	private boolean wall(Level level, BlockPos pos, List<BlockPos> drains) {
		if (pos.equals(worldPosition)) {
			return true;
		}
		BlockState state = level.getBlockState(pos);
		if (state.is(Tinkering.DRAIN)) {
			drains.add(pos);
			return true;
		}
		return state.is(Tinkering.SEARED_BRICKS) || state.is(Tinkering.SEARED_GLASS);
	}

	// --- the screen and faucets ---

	public int slots() {
		return slots;
	}

	/** A player clicked a liquid in the screen: it goes to the bottom, so the faucets pour it next. */
	public void toBottom(int index) {
		tank.toBottom(index);
		dirty = true;
	}

	/** A faucet takes some of the bottom liquid. */
	public int drain(Molten molten, int amount) {
		int taken = tank.drain(molten, amount, false);
		if (taken > 0) {
			dirty = true;
		}
		return taken;
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
		tank.load(input);
		fuel = input.getIntOr("fuel", 0);
		formed = input.getBooleanOr("formed", false);
		low = BlockPos.of(input.getLongOr("low", 0L));
		high = BlockPos.of(input.getLongOr("high", 0L));
		slots = input.getIntOr("slots", 0);
		int[] saved = input.getIntArray("progress").orElse(new int[0]);
		System.arraycopy(saved, 0, progress, 0, Math.min(saved.length, progress.length));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items, true);
		saveShared(output);
		output.putIntArray("progress", progress.clone());
	}

	private void saveShared(ValueOutput output) {
		tank.save(output);
		output.putInt("fuel", fuel);
		output.putBoolean("formed", formed);
		output.putLong("low", low.asLong());
		output.putLong("high", high.asLong());
		output.putInt("slots", slots);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		saveShared(output);
		return output.buildResult();
	}

	// --- as a container (the screen's slots, and hoppers) ---

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
		if (slot < MAX_SLOTS && items.get(slot).isEmpty()) {
			progress[slot] = 0;
		}
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
		if (slot < MAX_SLOTS) {
			progress[slot] = 0;
		}
		setChanged();
	}

	@Override
	public int getMaxStackSize() {
		return 64;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot < MAX_SLOTS) {
			return slot < slots && items.get(slot).isEmpty() && Melting.of(stack) != null;
		}
		return slot == FUEL_SLOT && stack.is(Items.LAVA_BUCKET);
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
	}

	/** For the client: the pool as last sent. */
	public Tank.@Nullable Layer layer(int index) {
		return index >= 0 && index < tank.layers().size() ? tank.layers().get(index) : null;
	}
}
