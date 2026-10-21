package com.jaymospowers.tinkering.kiln;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A kiln load: one stack of something to fire. While its kiln stands and the fire under it is hot enough it fires
 * step by step (cracks show how far along it is, smoke rises, the fire crackles), then turns into what it makes,
 * waiting to be taken out.
 */
public class KilnLoadEntity extends BlockEntity {
	/** Ticks between checks while the kiln is cold or broken. */
	static final int IDLE_DELAY = 20;
	/** Smoke every this many ticks while firing. */
	static final int SMOKE_TICKS = 8;

	private ItemStack stack = ItemStack.EMPTY;
	private int step;
	private int wait = Kiln.MIN_DELAY;
	private boolean firing;

	public KilnLoadEntity(BlockPos pos, BlockState state) {
		super(Kiln.KILN_LOAD_ENTITY, pos, state);
	}

	public ItemStack stack() {
		return stack;
	}

	/** True when {@code held} can go in: the load is empty or unfired and holds the same, with room. */
	public boolean accepts(ItemStack held) {
		Kiln.Recipe recipe = Kiln.recipe(held);
		return recipe != null && (stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, held) && stack.getCount() < recipe.capacity());
	}

	/** Puts one (or, {@code all}, as many as fit) of {@code held} in. */
	public void insert(ItemStack held, boolean all, boolean creative) {
		if (!accepts(held)) {
			return;
		}
		Kiln.Recipe recipe = Kiln.recipe(held);
		int room = recipe.capacity() - stack.getCount();
		int moved = Math.min(all ? held.getCount() : 1, room);
		if (stack.isEmpty()) {
			stack = held.copyWithCount(moved);
		} else {
			stack.grow(moved);
		}
		if (!creative) {
			held.shrink(moved);
		}
		step = 0;
		clearCracks();
		changed();
	}

	/** Takes everything out. */
	public ItemStack take() {
		ItemStack out = stack;
		stack = ItemStack.EMPTY;
		clearCracks();
		changed();
		return out;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, KilnLoadEntity load) {
		load.tick((ServerLevel) level, pos);
	}

	private void tick(ServerLevel level, BlockPos pos) {
		if (stack.isEmpty()) {
			level.removeBlock(pos, false);
			return;
		}
		Kiln.Recipe recipe = Kiln.recipe(stack);
		if (recipe == null) {
			// fired: waits to be taken out
			return;
		}
		if (firing && level.getGameTime() % SMOKE_TICKS == 0) {
			level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
		}
		if (--wait > 0) {
			return;
		}
		boolean hot = Kiln.walled(level, pos) && Kiln.heat(level, pos) >= recipe.heat();
		if (!hot) {
			wait = IDLE_DELAY;
			firing = false;
			return;
		}
		firing = true;
		if (++step >= Kiln.STEPS) {
			stack = recipe.fire(stack.getCount(), level.getRandom());
			step = 0;
			firing = false;
			clearCracks();
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.2, 0.15, 0.2, 0.01);
			changed();
			return;
		}
		level.destroyBlockProgress(crackId(), pos, step + 1);
		level.playSound(null, pos, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		wait = Kiln.stepDelay(level, pos);
		setChanged();
	}

	/** The id the cracks are shown under: one of its own, so nobody's mining cracks are disturbed. */
	private int crackId() {
		return -1000 - (int) (worldPosition.asLong() & 0x3FFFFFFF);
	}

	private void clearCracks() {
		if (level != null && !level.isClientSide()) {
			level.destroyBlockProgress(crackId(), worldPosition, -1);
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
		clearCracks();
		if (level != null) {
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		stack = input.read("stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		step = input.getIntOr("step", 0);
		wait = input.getIntOr("wait", Kiln.MIN_DELAY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("stack", ItemStack.OPTIONAL_CODEC, stack);
		output.putInt("step", step);
		output.putInt("wait", wait);
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
