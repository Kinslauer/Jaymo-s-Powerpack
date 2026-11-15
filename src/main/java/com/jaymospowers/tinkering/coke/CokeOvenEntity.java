package com.jaymospowers.tinkering.coke;

import com.jaymospowers.ground.StoneAge;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
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
 * The coke oven (after Railcraft's and GregTech's, our own code): a hollow 3x3x3 of coke oven bricks, its mouth
 * ({@link CokeOvenBlock}) in the middle of one wall and the middle of it empty. Formed, it bakes what it's loaded with
 * one at a time, with no fuel: a log into charcoal, coal into coal coke, a block of coal into a block of coal coke
 * ({@link #bakes}). Each bake sweats creosote into its tank ({@link #TANK} mB); a full tank stops it until some is drawn
 * off into a bucket or a clay bucket ({@link #BUCKET} mB each).
 */
public class CokeOvenEntity extends BlockEntity {
	/** Ticks to bake a log, a coal, a block of coal; creosote each leaves (mB). */
	public static final int LOG_TICKS = 900, COAL_TICKS = 1800, COAL_BLOCK_TICKS = COAL_TICKS * 9;
	public static final int LOG_CREOSOTE = 250, COAL_CREOSOTE = 500, COAL_BLOCK_CREOSOTE = COAL_CREOSOTE * 9;
	/** The tank, and what a bucket draws off. */
	public static final int TANK = 8000, BUCKET = 1000;
	/** Ticks between looks at the walls, and between puffs of smoke from the top while it bakes. */
	static final int CHECK_TICKS = 20, SMOKE_TICKS = 10;

	/** One bake: what goes in, what comes out, how long, and the creosote it leaves. */
	public record Bake(Predicate<ItemStack> input, Item output, int ticks, int creosote) {
	}

	private static final List<Bake> BAKES = new ArrayList<>();

	private ItemStack input = ItemStack.EMPTY, output = ItemStack.EMPTY;
	private int tank, progress;
	private boolean formed;

	public CokeOvenEntity(BlockPos pos, BlockState state) {
		super(CokeAndSteel.COKE_OVEN_ENTITY, pos, state);
	}

	/** From {@link CokeAndSteel#init}. */
	static void bakes() {
		BAKES.add(new Bake(s -> s.is(ItemTags.LOGS_THAT_BURN), Items.CHARCOAL, LOG_TICKS, LOG_CREOSOTE));
		BAKES.add(new Bake(s -> s.is(Items.COAL), CokeAndSteel.COAL_COKE, COAL_TICKS, COAL_CREOSOTE));
		BAKES.add(new Bake(s -> s.is(Items.COAL_BLOCK), CokeAndSteel.COAL_COKE_BLOCK.asItem(), COAL_BLOCK_TICKS, COAL_BLOCK_CREOSOTE));
	}

	/** Every bake, for JEI. */
	public static List<Bake> all() {
		return BAKES;
	}

	static @Nullable Bake bake(ItemStack stack) {
		for (Bake bake : BAKES) {
			if (!stack.isEmpty() && bake.input().test(stack)) {
				return bake;
			}
		}
		return null;
	}

	/** Whether the oven's walls stand: every block of the 3x3x3 round the hollow middle is a coke oven brick, but the mouth. */
	static boolean formed(Level level, BlockPos mouth, Direction out) {
		BlockPos middle = mouth.relative(out.getOpposite());
		for (BlockPos at : BlockPos.betweenClosed(middle.offset(-1, -1, -1), middle.offset(1, 1, 1))) {
			if (!level.isLoaded(at)) {
				return false;
			}
			if (at.equals(middle)) {
				if (!level.isEmptyBlock(at)) {
					return false;
				}
			} else if (!at.equals(mouth) && !level.getBlockState(at).is(CokeAndSteel.COKE_OVEN_BRICKS)) {
				return false;
			}
		}
		return true;
	}

	/** True when {@code held} can be baked and fits beside what's waiting. */
	boolean accepts(ItemStack held) {
		return bake(held) != null && (input.isEmpty() || ItemStack.isSameItemSameComponents(input, held) && input.getCount() < input.getMaxStackSize());
	}

	/** Loads as much of {@code held} as fits. */
	void insert(ItemStack held, boolean creative) {
		if (!accepts(held)) {
			return;
		}
		int moved = Math.min(held.getCount(), held.getMaxStackSize() - input.getCount());
		if (input.isEmpty()) {
			input = held.copyWithCount(moved);
			progress = 0;
		} else {
			input.grow(moved);
		}
		if (!creative) {
			held.shrink(moved);
		}
		changed();
	}

	/** What drawing creosote into {@code held} gives (a bucket's worth into a bucket or an empty clay bucket), or null. */
	@Nullable ItemStack filled(ItemStack held) {
		if (tank < BUCKET) {
			return null;
		}
		if (held.is(Items.BUCKET)) {
			return new ItemStack(CokeAndSteel.CREOSOTE_BUCKET);
		}
		if (held.is(StoneAge.CLAY_BUCKET)) {
			return held.transmuteCopy(CokeAndSteel.CLAY_CREOSOTE_BUCKET, 1);
		}
		return null;
	}

	/** A bucket's worth drawn off. */
	void draw() {
		tank = Math.max(0, tank - BUCKET);
		changed();
	}

	/** What's baked (or, {@code waiting}, what's still to bake) taken out; nothing if that's empty. */
	ItemStack take(boolean waiting) {
		ItemStack out;
		if (waiting) {
			out = input;
			input = ItemStack.EMPTY;
			progress = 0;
		} else {
			out = output;
			output = ItemStack.EMPTY;
		}
		changed();
		return out;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CokeOvenEntity oven) {
		oven.tick((ServerLevel) level, pos, state);
	}

	private void tick(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getGameTime() % CHECK_TICKS == Math.floorMod(pos.asLong(), CHECK_TICKS)) {
			formed = formed(level, pos, state.getValue(CokeOvenBlock.FACING));
		}
		Bake bake = bake(input);
		boolean baking = formed && bake != null && tank + bake.creosote() <= TANK
			&& (output.isEmpty() || output.is(bake.output()) && output.getCount() < output.getMaxStackSize());
		if (baking) {
			if (++progress >= bake.ticks()) {
				progress = 0;
				input.shrink(1);
				if (output.isEmpty()) {
					output = new ItemStack(bake.output());
				} else {
					output.grow(1);
				}
				tank += bake.creosote();
				changed();
			} else if (progress % 100 == 0) {
				setChanged();
			}
			if (level.getGameTime() % SMOKE_TICKS == 0) {
				BlockPos top = pos.relative(state.getValue(CokeOvenBlock.FACING).getOpposite()).above(2);
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, top.getX() + 0.5, top.getY() + 0.1, top.getZ() + 0.5, 1, 0.2, 0.0, 0.2, 0.01);
			}
		}
		int fill = Math.min(4, (tank * 4 + TANK / 2) / TANK);
		if (state.getValue(CokeOvenBlock.LIT) != baking || state.getValue(CokeOvenBlock.FILL) != fill) {
			level.setBlock(pos, state.setValue(CokeOvenBlock.LIT, baking).setValue(CokeOvenBlock.FILL, fill), Block.UPDATE_CLIENTS);
		}
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
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), input);
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), output);
		}
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		input = in.read("input", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		output = in.read("output", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		tank = in.getIntOr("creosote", 0);
		progress = in.getIntOr("progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.store("input", ItemStack.OPTIONAL_CODEC, input);
		out.store("output", ItemStack.OPTIONAL_CODEC, output);
		out.putInt("creosote", tank);
		out.putInt("progress", progress);
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
