package com.jaymoskitchen.mill;

import com.jaymoskitchen.mill.power.ShaftDriven;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A mill machine's workings (see {@link MachineKind}): what's loaded in it and what it's made, the press's tank,
 * and the work done so far. Worked by hand (turn, chop, pull: one effort each) or by a horse tied to it, walking
 * circles round it (a lap per effort), or by a turning shaft from a windmill or waterwheel (see {@link ShaftDriven}),
 * as if a horse were walking it. Hoppers can feed it from above or the sides and empty it from below.
 */
public class MachineEntity extends BlockEntity implements WorldlyContainer, ShaftDriven {
	public static final int INPUT = 0, OUTPUT = 1;
	/** The press's tank, in millibuckets. */
	public static final int TANK = 4000;
	/** A worked horse walks round at this radius, and is let go further away than this. */
	private static final double WALK_RADIUS = 3.0, SNAP_DISTANCE = 12.0;
	/** About how long a horse takes to walk a lap, in ticks (what a turning shaft is measured against). */
	public static final int HORSE_LAP_TICKS = 100;
	/** A shaft at speed 1 (a windmill in clear weather, a waterwheel) works this fast next to a horse. */
	public static final float WIND_WORK_RATE = 0.75F;

	private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
	private MachineFluid fluid = MachineFluid.NONE;
	private int fluidAmount;
	/** Work done on the current item or load: hand efforts, or radians walked round by the horse. */
	private float progress;
	/** Every hand effort ever (clients turn the handle by it). */
	private int efforts;
	private @Nullable UUID workerUuid;
	/** Synced for clients: the horse's entity id, or -1. */
	private int workerId = -1;
	private float lastAngle = Float.NaN;
	private int walkTicks;
	private int syncTicks;
	/** A turning shaft's speed, and how many more ticks it's good for (the source renews it while it turns). */
	private float shaftRate;
	private int shaftTicks;

	public MachineEntity(BlockPos pos, BlockState state) {
		super(Mill.MACHINE_ENTITY, pos, state);
	}

	public MachineKind kind() {
		return getBlockState().getBlock() instanceof MachineBlock block ? block.kind : MachineKind.HAND_GRINDSTONE;
	}

	public ItemStack input() {
		return items.get(INPUT);
	}

	public ItemStack output() {
		return items.get(OUTPUT);
	}

	public MachineFluid fluid() {
		return fluid;
	}

	public int fluidAmount() {
		return fluidAmount;
	}

	public int efforts() {
		return efforts;
	}

	public int workerId() {
		return workerId;
	}

	/** How far through the current item or load, 0..1. */
	public float workFraction() {
		float needed = kind().horse ? Mth.TWO_PI * kind().effort : kind().effort;
		return Mth.clamp(progress / needed, 0.0F, 1.0F);
	}

	// --- using it ---

	public InteractionResult use(Player player, InteractionHand hand, ItemStack held) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		MachineKind kind = kind();
		if (kind.horse) {
			// tie on a horse you're leading
			if (worker(server) == null) {
				for (AbstractHorse led : server.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(10.0),
					horse -> horse.getLeashHolder() == player)) {
					led.removeLeash();
					attach(led);
					play(SoundEvents.LEAD_TIED, 1.0F);
					player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.mill.attached"));
					return InteractionResult.SUCCESS_SERVER;
				}
			} else if (player.isSecondaryUseActive() && held.isEmpty()) {
				detach(server, player);
				return InteractionResult.SUCCESS_SERVER;
			}
		}
		if (kind.work == MachineKind.Work.PRESS && collectFluid(player, hand, held)) {
			return InteractionResult.SUCCESS_SERVER;
		}
		if (kind == MachineKind.CHOPPING_BLOCK && held.is(ItemTags.AXES) && !input().isEmpty()) {
			held.hurtAndBreak(1, player, hand);
			effort(server, player);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!held.isEmpty()) {
			if (accepts(held)) {
				insert(held, player);
				play(SoundEvents.ITEM_PICKUP, 0.8F);
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		}
		// empty hand: take what's made, work it by hand, or take the load back
		if (!output().isEmpty()) {
			give(player, output().copy());
			items.set(OUTPUT, ItemStack.EMPTY);
			markUpdated();
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!kind.horse && kind.work != MachineKind.Work.CHOP && !player.isSecondaryUseActive() && canWork()) {
			effort(server, player);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!input().isEmpty()) {
			give(player, input().copy());
			items.set(INPUT, ItemStack.EMPTY);
			progress = 0.0F;
			markUpdated();
			return InteractionResult.SUCCESS_SERVER;
		}
		if (kind.work == MachineKind.Work.PRESS && fluidAmount > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.mill.tank",
				Component.translatable("fluid.jaymoskitchen." + fluid.name().toLowerCase(java.util.Locale.ROOT)), fluidAmount, TANK));
		} else if (kind.horse) {
			player.sendOverlayMessage(Component.translatable(worker(server) == null && shaftTicks <= 0
				? "message.jaymoskitchen.mill.need_power" : "message.jaymoskitchen.mill.empty"));
		} else if (kind == MachineKind.CHOPPING_BLOCK) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.mill.need_log"));
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/** One turn of the handle, chop of the axe or pull of the press. */
	private void effort(ServerLevel level, Player player) {
		if (!canWork()) {
			return;
		}
		efforts++;
		progress += 1.0F;
		play(switch (kind().work) {
			case GRIND -> SoundEvents.GRINDSTONE_USE;
			case CHOP -> SoundEvents.AXE_STRIP.value();
			case PRESS -> SoundEvents.PISTON_EXTEND;
		}, 0.9F + level.getRandom().nextFloat() * 0.2F);
		if (progress >= kind().effort) {
			finish(level);
		}
		markUpdated();
	}

	private static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	/** Bucket or bottle on the press: draws off water or oil. */
	private boolean collectFluid(Player player, InteractionHand hand, ItemStack held) {
		ItemStack filled = ItemStack.EMPTY;
		int cost = 0;
		if (held.is(Items.BUCKET) && fluid == MachineFluid.WATER && fluidAmount >= 1000) {
			filled = new ItemStack(Items.WATER_BUCKET);
			cost = 1000;
		} else if (held.is(Items.GLASS_BOTTLE) && fluidAmount >= 250) {
			if (fluid == MachineFluid.WATER) {
				filled = PotionContents.createItemStack(Items.POTION, Potions.WATER);
			} else if (fluid == MachineFluid.OIL) {
				filled = MachineRecipes.oilBottle();
			}
			cost = 250;
		}
		if (filled.isEmpty()) {
			return false;
		}
		player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, filled));
		fluidAmount -= cost;
		if (fluidAmount <= 0) {
			fluidAmount = 0;
			fluid = MachineFluid.NONE;
		}
		play(held.is(Items.BUCKET) ? SoundEvents.BUCKET_FILL : SoundEvents.BOTTLE_FILL, 1.0F);
		markUpdated();
		return true;
	}

	/** Whether this can be loaded in (on top of what's already loaded). */
	public boolean accepts(ItemStack stack) {
		ItemStack current = input();
		if (!current.isEmpty() && (!ItemStack.isSameItemSameComponents(current, stack) || current.getCount() >= current.getMaxStackSize()
			|| kind() == MachineKind.CHOPPING_BLOCK)) {
			return false;
		}
		return switch (kind().work) {
			case GRIND -> MachineRecipes.grind(stack) != null;
			case CHOP -> MachineRecipes.chop(stack, kind().horse) != null;
			case PRESS -> {
				MachineRecipes.Pressing pressing = MachineRecipes.press(stack);
				yield pressing != null && (pressing.fluid() == MachineFluid.NONE || fluid == MachineFluid.NONE || fluid == pressing.fluid());
			}
		};
	}

	private void insert(ItemStack held, Player player) {
		int room = kind() == MachineKind.CHOPPING_BLOCK ? 1 : held.getMaxStackSize() - input().getCount();
		int moved = Math.min(room, held.getCount());
		if (input().isEmpty()) {
			items.set(INPUT, held.copyWithCount(moved));
		} else {
			input().grow(moved);
		}
		held.consume(moved, player);
		markUpdated();
	}

	// --- the work ---

	/** Whether there's a load it can work, and room for what it makes. */
	private boolean canWork() {
		ItemStack in = input();
		if (in.isEmpty()) {
			return false;
		}
		return switch (kind().work) {
			case GRIND -> fits(MachineRecipes.grind(in));
			case CHOP -> kind() == MachineKind.CHOPPING_BLOCK || fits(MachineRecipes.chop(in, true));
			case PRESS -> {
				MachineRecipes.Pressing p = MachineRecipes.press(in);
				if (p == null || in.getCount() < p.load()) {
					yield false;
				}
				yield p.fluid() == MachineFluid.NONE ? fits(p.item())
					: (fluid == MachineFluid.NONE || fluid == p.fluid()) && fluidAmount + p.amount() <= TANK;
			}
		};
	}

	private boolean fits(@Nullable ItemStack made) {
		if (made == null || made.isEmpty()) {
			return false;
		}
		ItemStack out = output();
		return out.isEmpty() || ItemStack.isSameItemSameComponents(out, made) && out.getCount() + made.getCount() <= out.getMaxStackSize();
	}

	/** The item or load is done: out comes what it makes. */
	private void finish(ServerLevel level) {
		ItemStack in = input();
		switch (kind().work) {
			case GRIND -> {
				output(MachineRecipes.grind(in));
				in.shrink(1);
			}
			case CHOP -> {
				ItemStack planks = MachineRecipes.chop(in, kind().horse);
				if (kind() == MachineKind.CHOPPING_BLOCK) {
					// they fly off the block
					Block.popResource(level, worldPosition.above(), planks);
					level.playSound(null, worldPosition, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
				} else {
					output(planks);
				}
				in.shrink(1);
			}
			case PRESS -> {
				MachineRecipes.Pressing p = MachineRecipes.press(in);
				if (p != null) {
					if (p.fluid() == MachineFluid.NONE) {
						output(p.item().copy());
					} else {
						fluid = p.fluid();
						fluidAmount += p.amount();
					}
					in.shrink(p.load());
				}
			}
		}
		progress = 0.0F;
		markUpdated();
	}

	private void output(@Nullable ItemStack made) {
		if (made == null || made.isEmpty()) {
			return;
		}
		if (output().isEmpty()) {
			items.set(OUTPUT, made);
		} else {
			output().grow(made.getCount());
		}
	}

	// --- the horse ---

	private @Nullable AbstractHorse worker(ServerLevel level) {
		if (workerUuid == null) {
			return null;
		}
		return level.getEntity(workerUuid) instanceof AbstractHorse horse ? horse : null;
	}

	private void attach(AbstractHorse horse) {
		workerUuid = horse.getUUID();
		workerId = horse.getId();
		lastAngle = Float.NaN;
		markUpdated();
	}

	/** Lets the horse go, handing back the lead it was tied on with. */
	private void detach(ServerLevel level, @Nullable Player player) {
		workerUuid = null;
		workerId = -1;
		ItemStack lead = new ItemStack(Items.LEAD);
		if (player != null) {
			give(player, lead);
		} else {
			Block.popResource(level, worldPosition.above(), lead);
		}
		play(SoundEvents.LEAD_UNTIED, 1.0F);
		markUpdated();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, MachineEntity machine) {
		if (level instanceof ServerLevel server && machine.kind().horse) {
			machine.tickShaft(server);
			machine.tickHorse(server);
		}
	}

	// --- wind and water ---

	@Override
	public boolean takesShaftFrom(Direction side) {
		// into the top (an upright shaft, or a corner above) or any side; never from underneath
		return kind().horse && side != Direction.DOWN;
	}

	@Override
	public void turnFromShaft(float rate, int ticks) {
		shaftRate = rate;
		shaftTicks = Math.max(shaftTicks, ticks);
	}

	/** A turning shaft works it as a horse walking round would, at {@link #WIND_WORK_RATE} of a horse's pace. */
	private void tickShaft(ServerLevel level) {
		if (shaftTicks <= 0) {
			return;
		}
		shaftTicks--;
		if (!canWork()) {
			return;
		}
		progress += Mth.TWO_PI / HORSE_LAP_TICKS * WIND_WORK_RATE * shaftRate;
		if (progress >= Mth.TWO_PI * kind().effort) {
			finish(level);
			play(switch (kind().work) {
				case GRIND -> SoundEvents.GRINDSTONE_USE;
				case CHOP -> SoundEvents.AXE_STRIP.value();
				case PRESS -> SoundEvents.PISTON_EXTEND;
			}, 0.8F + level.getRandom().nextFloat() * 0.2F);
		} else if (++syncTicks >= 5) {
			syncTicks = 0;
			markUpdated();
		}
	}

	/** Keeps a tied horse walking round, counting how far it's gone; every lap or so finishes an item. */
	private void tickHorse(ServerLevel level) {
		if (workerUuid == null) {
			return;
		}
		AbstractHorse horse = worker(level);
		if (horse == null) {
			// not loaded yet (or gone): give it a while
			if (++walkTicks > 600) {
				detach(level, null);
			}
			return;
		}
		Vec3 centre = Vec3.atBottomCenterOf(worldPosition);
		if (!horse.isAlive() || horse.distanceToSqr(centre) > SNAP_DISTANCE * SNAP_DISTANCE || horse.isLeashed()) {
			detach(level, null);
			return;
		}
		if (workerId != horse.getId()) {
			workerId = horse.getId();
			markUpdated();
		}
		float angle = (float) Mth.atan2(horse.getZ() - centre.z, horse.getX() - centre.x);
		if (!Float.isNaN(lastAngle) && canWork()) {
			float walked = Mth.wrapDegrees((angle - lastAngle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
			if (walked > 0.0F && walked < 1.0F) {
				progress += walked;
				if (progress >= Mth.TWO_PI * kind().effort) {
					finish(level);
				} else if (++syncTicks >= 5) {
					syncTicks = 0;
					markUpdated();
				}
			}
		}
		lastAngle = angle;
		if (++walkTicks >= 10) {
			// round it goes (unless there's nothing to do)
			walkTicks = 0;
			if (canWork() && horse.getControllingPassenger() == null) {
				// aim well round the circle: a horse is too big to path to a spot just ahead (it counts as there already)
				for (float ahead : new float[] {1.3F, 2.0F, 2.6F}) {
					float to = angle + ahead;
					if (horse.getNavigation().moveTo(centre.x + Mth.cos(to) * WALK_RADIUS, centre.y, centre.z + Mth.sin(to) * WALK_RADIUS, 1.0)) {
						break;
					}
				}
			} else if (horse.getControllingPassenger() == null) {
				horse.getNavigation().stop();
			}
		}
	}

	/** The machine's going: let the horse go, and spill what's in it. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel server) {
			if (workerUuid != null) {
				detach(server, null);
			}
			Containers.dropContents(level, pos, items);
		}
	}

	private void play(SoundEvent sound, float pitch) {
		if (level != null) {
			level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1.0F, pitch);
		}
	}

	private void markUpdated() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	// --- saving and syncing ---

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.clear();
		ContainerHelper.loadAllItems(input, items);
		fluid = MachineFluid.byName(input.getStringOr("Fluid", "NONE"));
		fluidAmount = input.getIntOr("FluidAmount", 0);
		progress = input.getFloatOr("Progress", 0.0F);
		efforts = input.getIntOr("Efforts", 0);
		workerUuid = input.read("Worker", UUIDUtil.CODEC).orElse(null);
		workerId = input.getIntOr("WorkerId", -1);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		writeShared(output);
		output.storeNullable("Worker", UUIDUtil.CODEC, workerUuid);
	}

	private void writeShared(ValueOutput output) {
		ContainerHelper.saveAllItems(output, items, true);
		output.putString("Fluid", fluid.name());
		output.putInt("FluidAmount", fluidAmount);
		output.putFloat("Progress", progress);
		output.putInt("Efforts", efforts);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		writeShared(output);
		output.putInt("WorkerId", workerId);
		return output.buildResult();
	}

	// --- hoppers ---

	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? new int[] {OUTPUT} : new int[] {INPUT};
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return slot == INPUT && accepts(stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot == OUTPUT;
	}

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		return input().isEmpty() && output().isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, count);
		markUpdated();
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.limitSize(getMaxStackSize(stack));
		markUpdated();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
	}
}
