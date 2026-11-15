package com.jaymospowers.tinkering.coke;

import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.dangers.ForgeDangers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The brick blast furnace (after Railcraft's and GregTech's, our own code): a tower of firebricks three wide, four high
 * and three deep, its middle hollow from the second layer up and open at the top, its mouth ({@link BlastFurnaceBlock}) in
 * the middle of one side on the second layer. Formed and loaded with iron ingots and coal coke, it slowly burns the coke
 * into the iron: {@link #COKE_PER_STEEL} coke and an ingot make a steel ingot every {@link #STEEL_TICKS} ticks, leaving
 * {@link #ASH_PER_STEEL} ash. The second road to steel, beside the smeltery's iron and pig iron. (GTNH picks, the
 * forge's dangers) Rain down its open top slows it to half speed, a thunderstorm stops it and loses the steel under way,
 * and its walls burn while it works.
 */
public class BlastFurnaceEntity extends BlockEntity {
	/** Ticks for one steel ingot (two minutes), the coke it burns and the ash it leaves. */
	public static final int STEEL_TICKS = 2400, COKE_PER_STEEL = 1, ASH_PER_STEEL = 1;
	/** The tower: width and depth, and height. */
	public static final int SIDE = 3, HEIGHT = 4;
	static final int CHECK_TICKS = 20, SMOKE_TICKS = 6;

	private ItemStack iron = ItemStack.EMPTY, coke = ItemStack.EMPTY, steel = ItemStack.EMPTY, ash = ItemStack.EMPTY;
	private int progress;
	private boolean formed;
	/** (the forge's dangers) Rain, and a thunderstorm, down its top as last looked; the counter for working at half speed. */
	private boolean wet, storm;
	private int wetTicks;

	public BlastFurnaceEntity(BlockPos pos, BlockState state) {
		super(CokeAndSteel.BLAST_FURNACE_ENTITY, pos, state);
	}

	/** The tower's bottom middle, under the hollow. */
	static BlockPos base(BlockPos mouth, Direction out) {
		return mouth.relative(out.getOpposite()).below();
	}

	/** Whether the tower stands: firebricks all round, the middle empty from the second layer to the top, the mouth in its place. */
	static boolean formed(Level level, BlockPos mouth, Direction out) {
		BlockPos base = base(mouth, out);
		for (BlockPos at : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, HEIGHT - 1, 1))) {
			if (!level.isLoaded(at)) {
				return false;
			}
			boolean hollow = at.getX() == base.getX() && at.getZ() == base.getZ() && at.getY() > base.getY();
			if (hollow) {
				if (!level.isEmptyBlock(at)) {
					return false;
				}
			} else if (!at.equals(mouth) && !level.getBlockState(at).is(CokeAndSteel.FIREBRICKS)) {
				return false;
			}
		}
		return true;
	}

	boolean accepts(ItemStack held) {
		ItemStack slot = slotFor(held);
		return slot != null && (slot.isEmpty() || ItemStack.isSameItemSameComponents(slot, held) && slot.getCount() < slot.getMaxStackSize());
	}

	private @Nullable ItemStack slotFor(ItemStack held) {
		return held.is(Items.IRON_INGOT) ? iron : held.is(CokeAndSteel.COAL_COKE) ? coke : null;
	}

	void insert(ItemStack held, boolean creative) {
		if (!accepts(held)) {
			return;
		}
		ItemStack slot = slotFor(held);
		int moved = Math.min(held.getCount(), held.getMaxStackSize() - slot.getCount());
		if (slot.isEmpty()) {
			if (held.is(Items.IRON_INGOT)) {
				iron = held.copyWithCount(moved);
			} else {
				coke = held.copyWithCount(moved);
			}
		} else {
			slot.grow(moved);
		}
		if (!creative) {
			held.shrink(moved);
		}
		changed();
	}

	/** The steel, then the ash; or ({@code waiting}) the iron, then the coke; nothing if those are empty. */
	ItemStack take(boolean waiting) {
		ItemStack out;
		if (waiting) {
			if (!iron.isEmpty()) {
				out = iron;
				iron = ItemStack.EMPTY;
			} else {
				out = coke;
				coke = ItemStack.EMPTY;
			}
			progress = 0;
		} else if (!steel.isEmpty()) {
			out = steel;
			steel = ItemStack.EMPTY;
		} else {
			out = ash;
			ash = ItemStack.EMPTY;
		}
		changed();
		return out;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, BlastFurnaceEntity furnace) {
		furnace.tick((ServerLevel) level, pos, state);
	}

	private void tick(ServerLevel level, BlockPos pos, BlockState state) {
		Direction out = state.getValue(BlastFurnaceBlock.FACING);
		BlockPos base = base(pos, out);
		boolean check = level.getGameTime() % CHECK_TICKS == Math.floorMod(pos.asLong(), CHECK_TICKS);
		if (check) {
			formed = formed(level, pos, out);
			// (GTNH picks, the forge's dangers) rain down its open top
			wet = ForgeDangers.wet(level, base.above(HEIGHT - 1));
			storm = ForgeDangers.storm(level, base.above(HEIGHT - 1));
		}
		boolean working = formed && !iron.isEmpty() && coke.getCount() >= COKE_PER_STEEL && room(steel, Tinkering.STEEL_INGOT, 1)
			&& room(ash, CokeAndSteel.ASH, ASH_PER_STEEL);
		if (working && storm) {
			// a thunderstorm's downpour down the stack puts it out: the steel under way is lost to it
			if (progress > 0) {
				progress = 0;
				ForgeDangers.hiss(level, base.above(HEIGHT - 1), true);
				setChanged();
			}
			working = false;
		}
		if (check) {
			if (working) {
				ForgeDangers.working(level, pos, base.offset(-1, 0, -1), base.offset(1, HEIGHT - 1, 1));
				if (wet) {
					ForgeDangers.hiss(level, base.above(HEIGHT - 1), false);
				}
			} else {
				ForgeDangers.cooled(level, pos);
			}
		}
		// rain down it works at half speed
		if (working && (!wet || (++wetTicks & 1) == 0)) {
			if (++progress >= STEEL_TICKS) {
				progress = 0;
				iron.shrink(1);
				coke.shrink(COKE_PER_STEEL);
				steel = grown(steel, Tinkering.STEEL_INGOT, 1);
				ash = grown(ash, CokeAndSteel.ASH, ASH_PER_STEEL);
				changed();
			} else if (progress % 100 == 0) {
				setChanged();
			}
			if (level.getGameTime() % SMOKE_TICKS == 0) {
				BlockPos top = base(pos, out).above(HEIGHT);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 2, 0.15, 0.0, 0.15, 0.02);
			}
		}
		if (state.getValue(BlastFurnaceBlock.LIT) != working) {
			level.setBlock(pos, state.setValue(BlastFurnaceBlock.LIT, working), Block.UPDATE_CLIENTS);
		}
	}

	private static boolean room(ItemStack stack, Item item, int more) {
		return stack.isEmpty() || stack.is(item) && stack.getCount() + more <= stack.getMaxStackSize();
	}

	private static ItemStack grown(ItemStack stack, Item item, int more) {
		if (stack.isEmpty()) {
			return new ItemStack(item, more);
		}
		stack.grow(more);
		return stack;
	}

	private void changed() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			for (ItemStack stack : new ItemStack[] {iron, coke, steel, ash}) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		iron = in.read("iron", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		coke = in.read("coke", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		steel = in.read("steel", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		ash = in.read("ash", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		progress = in.getIntOr("progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput o) {
		super.saveAdditional(o);
		o.store("iron", ItemStack.OPTIONAL_CODEC, iron);
		o.store("coke", ItemStack.OPTIONAL_CODEC, coke);
		o.store("steel", ItemStack.OPTIONAL_CODEC, steel);
		o.store("ash", ItemStack.OPTIONAL_CODEC, ash);
		o.putInt("progress", progress);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		saveAdditional(out);
		return out.buildResult();
	}
}
