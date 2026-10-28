package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.forge.ForgeHearthBlock;
import com.jaymospowers.tinkering.kiln.Heat;
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
 * (Smithing) What's lying in a Forge Hearth's coals: one stack of ingots, or a forging blank, brought up to heat while
 * the hearth burns ({@link HotMetal#HEAT_TICKS}, faster with bellows) and kept hot as long as it stays in the fire.
 */
public class HearthEntity extends BlockEntity {
	/** Most ingots one hearth heats at once. */
	public static final int MAX_STACK = 16;
	/** How often hot metal in the fire has its heat topped up (ticks). */
	static final int KEEP_HOT_TICKS = 20;

	private ItemStack held = ItemStack.EMPTY;
	private int progress;

	public HearthEntity(BlockPos pos, BlockState state) {
		super(Smithing.HEARTH_ENTITY, pos, state);
	}

	public ItemStack held() {
		return held;
	}

	/** Whether what's in the coals is hot through. */
	public boolean ready() {
		return level != null && (held.is(Smithing.HOT_INGOT) || held.is(Smithing.FORGING_BLANK)) && HotMetal.hot(held, level.getGameTime());
	}

	/** Lays metal in the coals (one stack; more of the same tops it up). */
	public boolean put(ItemStack stack) {
		if (!HotMetal.heatable(stack)) {
			return false;
		}
		if (held.isEmpty()) {
			held = stack.split(stack.is(Smithing.FORGING_BLANK) ? 1 : Math.min(stack.getCount(), MAX_STACK));
			progress = 0;
		} else if (ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < MAX_STACK) {
			int moved = Math.min(stack.getCount(), MAX_STACK - held.getCount());
			held.grow(moved);
			stack.shrink(moved);
		} else {
			return false;
		}
		changed();
		return true;
	}

	public ItemStack take() {
		ItemStack out = held;
		held = ItemStack.EMPTY;
		progress = 0;
		changed();
		return out;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, HearthEntity hearth) {
		if (hearth.held.isEmpty() || !state.getValue(ForgeHearthBlock.LIT)) {
			return;
		}
		long now = level.getGameTime();
		if (hearth.ready()) {
			if (now % KEEP_HOT_TICKS == 0) {
				hearth.held.set(Smithing.HEATED_AT, now);
			}
			return;
		}
		int heat = Math.max(1, Heat.at(level, pos));
		hearth.progress += 1 << Math.max(0, heat - Heat.HEARTH);
		if (hearth.progress >= HotMetal.HEAT_TICKS) {
			hearth.held = HotMetal.heated(hearth.held, now);
			hearth.progress = 0;
			level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.4F, 1.6F);
			((ServerLevel) level).sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.15, 0.0, 0.15, 0.0);
			hearth.changed();
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
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), held);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		held = input.read("held", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		progress = input.getIntOr("progress", 0);
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("held", ItemStack.OPTIONAL_CODEC, held);
		output.putInt("progress", progress);
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
