package com.jaymoskitchen.forage;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What lives and is kept in a {@link HarvestHiveBlock}: up to three vanilla bees, and nine slots of what they bring
 * home (hoppers may take it; nothing goes in but by the bees).
 * <p>
 * Every second the hive takes a step. By day, and not in rain, the bees work: one is let out to a ripe crop within
 * {@link #RANGE_H} blocks, picks it and plants it again ({@link HiveWorker}), and flies the harvest home. How fast the
 * steps add up to a trip depends on the flowers in range: with none the bees work at a third of the pace
 * ({@link #speed}). Each trip home may pollinate, putting a copy of a flower nearby where it stands free, so a worked
 * hive makes its own flowers and works faster. A hive that is worked, with flowers about, also fills with honey.
 */
public class HarvestHiveEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
	public static final int CAPACITY = 3;
	private static final int SIZE = 9;
	/** The hive steps once in this many ticks. */
	private static final int STEP_TICKS = 20;
	/** Steps (at full pace) between one trip and the next. */
	private static final float STEPS_PER_TRIP = 10.0F;
	/** Pace with no flower in range, with fewer than {@link #FEW_FLOWERS}, and with enough. */
	private static final float PACE_NONE = 0.34F, PACE_FEW = 0.67F, PACE_FULL = 1.0F;
	private static final int FEW_FLOWERS = 3;
	/** Where the bees look: blocks out sideways, and up and down. */
	public static final int RANGE_H = 10, RANGE_V = 4;
	/** Steps between looks at the flowers in range. */
	private static final int FLOWER_LOOK_STEPS = 10;
	/** At most this many flowers are counted, and pollination stops when there are this many. */
	private static final int FLOWER_CAP = 32;
	/** Chance a trip home pollinates a flower. */
	private static final float POLLINATE_CHANCE = 0.4F;
	/** Honey points a trip home gives, steps an idle worked hive takes to make one, and points to a level of honey. */
	private static final int POINTS_PER_TRIP = 1, IDLE_STEPS_PER_POINT = 60, POINTS_PER_LEVEL = 3;
	/** A bee not seen this many ticks is counted lost and its place freed. */
	private static final int LOST_TICKS = 3600;

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private final List<Occupant> inside = new ArrayList<>();
	private final List<UUID> workers = new ArrayList<>();
	private final Map<UUID, Integer> unseen = new HashMap<>();
	private final Map<UUID, BlockPos> targets = new HashMap<>();
	private float charge;
	private int honeyPoints;
	private int idleSteps;
	private int flowers;
	private int lookIn;
	private final List<BlockPos> smallFlowers = new ArrayList<>();

	public HarvestHiveEntity(BlockPos pos, BlockState state) {
		super(Forage.HIVE_ENTITY, pos, state);
	}

	/** By day and not in rain. */
	public static boolean workable(Level level) {
		return level.isBrightOutside() && !level.isRaining();
	}

	public int bees() {
		return inside.size() + workers.size();
	}

	public boolean hasRoom() {
		return bees() < CAPACITY;
	}

	/** A bee in a jar, or one that came home, goes in. */
	public boolean add(Occupant bee) {
		if (!hasRoom()) {
			return false;
		}
		inside.add(bee);
		setChanged();
		return true;
	}

	/** Lets every bee inside out where it stands (the hive is broken). */
	public void releaseAll(ServerLevel level, BlockPos pos) {
		for (Occupant bee : inside) {
			Entity entity = bee.createEntity(level, pos);
			if (entity != null) {
				entity.snapTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
				level.addFreshEntity(entity);
			}
		}
		inside.clear();
	}

	/** A worker home: the harvest goes into the slots (what doesn't fit falls), and the bee goes inside. */
	public void takeIn(Bee bee, HiveWorker.Job job) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		for (ItemStack stack : job.load()) {
			ItemStack rest = insert(stack.copy());
			if (!rest.isEmpty()) {
				Block.popResource(server, worldPosition.above(), rest);
			}
		}
		bee.removeAttached(HiveWorker.JOB);
		Occupant occupant = Occupant.of(bee);
		bee.discard();
		inside.add(occupant);
		workers.remove(bee.getUUID());
		targets.remove(bee.getUUID());
		unseen.remove(bee.getUUID());
		addHoney(POINTS_PER_TRIP);
		pollinate(server);
		setChanged();
	}

	/** Puts as much of the stack into the slots as fits; what is left. */
	private ItemStack insert(ItemStack stack) {
		for (int slot = 0; slot < items.size() && !stack.isEmpty(); slot++) {
			ItemStack held = items.get(slot);
			if (held.isEmpty()) {
				items.set(slot, stack);
				return ItemStack.EMPTY;
			}
			if (ItemStack.isSameItemSameComponents(held, stack)) {
				int moved = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
				held.grow(moved);
				stack.shrink(moved);
			}
		}
		return stack;
	}

	private void addHoney(int points) {
		honeyPoints += points;
		BlockState state = getBlockState();
		int level = state.getValue(HarvestHiveBlock.HONEY);
		while (honeyPoints >= POINTS_PER_LEVEL) {
			honeyPoints -= POINTS_PER_LEVEL;
			if (level < HarvestHiveBlock.MAX_HONEY) {
				level++;
			}
		}
		if (this.level != null && level != state.getValue(HarvestHiveBlock.HONEY)) {
			this.level.setBlock(worldPosition, state.setValue(HarvestHiveBlock.HONEY, level), Block.UPDATE_ALL);
		}
	}

	/** How fast the bees work: with no flowers about, slowly. */
	private float speed() {
		return flowers == 0 ? PACE_NONE : flowers < FEW_FLOWERS ? PACE_FEW : PACE_FULL;
	}

	/** Counts the flowers in range, and remembers where some of the small ones stand. */
	private void lookAtFlowers(ServerLevel level) {
		flowers = 0;
		smallFlowers.clear();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = -RANGE_H; x <= RANGE_H && flowers < FLOWER_CAP; x++) {
			for (int z = -RANGE_H; z <= RANGE_H && flowers < FLOWER_CAP; z++) {
				for (int y = -RANGE_V; y <= RANGE_V && flowers < FLOWER_CAP; y++) {
					at.set(worldPosition.getX() + x, worldPosition.getY() + y, worldPosition.getZ() + z);
					BlockState state = level.getBlockState(at);
					if (state.is(BlockTags.FLOWERS)) {
						flowers++;
						if (state.is(BlockTags.SMALL_FLOWERS)) {
							smallFlowers.add(at.immutable());
						}
					}
				}
			}
		}
	}

	/** A bee that has been among the flowers leaves a copy of one where it stands free beside it. */
	private void pollinate(ServerLevel level) {
		if (smallFlowers.isEmpty() || flowers >= FLOWER_CAP || level.getRandom().nextFloat() >= POLLINATE_CHANCE) {
			return;
		}
		BlockPos from = smallFlowers.get(level.getRandom().nextInt(smallFlowers.size()));
		BlockState flower = level.getBlockState(from);
		if (!flower.is(BlockTags.SMALL_FLOWERS)) {
			return;
		}
		for (int i = 0; i < 8; i++) {
			BlockPos spot = from.offset(level.getRandom().nextInt(5) - 2, level.getRandom().nextInt(3) - 1, level.getRandom().nextInt(5) - 2);
			if (level.isEmptyBlock(spot) && flower.canSurvive(level, spot)) {
				level.setBlock(spot, flower, Block.UPDATE_ALL);
				flowers++;
				smallFlowers.add(spot);
				return;
			}
		}
	}

	/** A ripe crop in range that no worker is already going to, picked at random, or null. */
	private @Nullable BlockPos ripeCrop(ServerLevel level) {
		List<BlockPos> ripe = new ArrayList<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = -RANGE_H; x <= RANGE_H; x++) {
			for (int z = -RANGE_H; z <= RANGE_H; z++) {
				for (int y = -RANGE_V; y <= RANGE_V; y++) {
					at.set(worldPosition.getX() + x, worldPosition.getY() + y, worldPosition.getZ() + z);
					if (HiveWorker.ripe(level.getBlockState(at)) && !targets.containsValue(at)) {
						ripe.add(at.immutable());
					}
				}
			}
		}
		return ripe.isEmpty() ? null : ripe.get(level.getRandom().nextInt(ripe.size()));
	}

	/** Lets the first bee out to the crop. */
	private void send(ServerLevel level, BlockPos crop) {
		Occupant occupant = inside.get(0);
		Entity entity = occupant.createEntity(level, worldPosition);
		if (!(entity instanceof Bee bee)) {
			inside.remove(0);
			return;
		}
		inside.remove(0);
		Direction front = getBlockState().getValue(HarvestHiveBlock.FACING);
		Vec3 door = Vec3.atCenterOf(worldPosition).relative(front, 0.75);
		bee.snapTo(door.x, door.y, door.z, front.toYRot(), 0.0F);
		bee.setAttached(HiveWorker.JOB, new HiveWorker.Job(worldPosition, Optional.of(crop), false, List.of(), level.getGameTime()));
		level.addFreshEntity(bee);
		workers.add(bee.getUUID());
		targets.put(bee.getUUID(), crop);
		setChanged();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, HarvestHiveEntity hive) {
		if (!(level instanceof ServerLevel server) || level.getGameTime() % STEP_TICKS != 0) {
			return;
		}
		hive.step(server);
	}

	private void step(ServerLevel level) {
		workers.removeIf(id -> {
			if (level.getEntity(id) != null) {
				unseen.remove(id);
				return false;
			}
			int gone = unseen.merge(id, STEP_TICKS, Integer::sum);
			if (gone > LOST_TICKS) {
				unseen.remove(id);
				targets.remove(id);
				return true;
			}
			return false;
		});
		if (lookIn-- <= 0) {
			lookIn = FLOWER_LOOK_STEPS;
			lookAtFlowers(level);
		}
		if (bees() == 0) {
			return;
		}
		if (flowers > 0 && ++idleSteps >= IDLE_STEPS_PER_POINT) {
			idleSteps = 0;
			addHoney(1);
			setChanged();
		}
		if (inside.isEmpty() || !workable(level)) {
			return;
		}
		charge += speed();
		if (charge < STEPS_PER_TRIP) {
			return;
		}
		BlockPos crop = ripeCrop(level);
		// nothing ripe: look again in half a trip
		charge = crop == null ? STEPS_PER_TRIP / 2.0F : 0.0F;
		if (crop != null) {
			send(level, crop);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		if (!tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, items);
		}
		inside.clear();
		inside.addAll(input.read("Bees", Occupant.LIST_CODEC).orElse(List.of()));
		workers.clear();
		workers.addAll(input.read("Workers", UUIDUtil.CODEC.listOf()).orElse(List.of()));
		honeyPoints = input.getIntOr("HoneyPoints", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, items);
		}
		output.store("Bees", Occupant.LIST_CODEC, List.copyOf(inside));
		output.store("Workers", Codec.list(UUIDUtil.CODEC), List.copyOf(workers));
		output.putInt("HoneyPoints", honeyPoints);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel server) {
			releaseAll(server, pos);
			for (UUID id : workers) {
				if (server.getEntity(id) instanceof Bee bee) {
					HiveWorker.abandon(bee);
				}
			}
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	public int getContainerSize() {
		return SIZE;
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
	protected Component getDefaultName() {
		return Component.translatable("container.jaymoskitchen.harvest_hive");
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return new ChestMenu(MenuType.GENERIC_9x1, id, inventory, this, 1);
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		int[] all = new int[SIZE];
		for (int i = 0; i < SIZE; i++) {
			all[i] = i;
		}
		return all;
	}

	/** The bees fill it; nothing is fed in from outside. */
	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return false;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return true;
	}

}
