package com.jaymospowers.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The logs stood on a chopping block ({@link ChoppingBlock}), and how many blows the top one has taken. */
public class ChoppingBlockEntity extends BlockEntity {
	private ItemStack logs = ItemStack.EMPTY;
	private int blows;
	/** Game time of the last blow that counted (not saved: a reload just lets the next blow through). */
	private long lastChop = Long.MIN_VALUE / 2;

	public ChoppingBlockEntity(BlockPos pos, BlockState state) {
		super(Chopping.ENTITY, pos, state);
	}

	public ItemStack logs() {
		return logs;
	}

	/** True when {@code held} is a log with planks and the block is empty or holds the same, with room. */
	public boolean accepts(ItemStack held) {
		return Chopping.planksOf(held) != null
			&& (logs.isEmpty() || ItemStack.isSameItemSameComponents(logs, held) && logs.getCount() < Chopping.HOLDS);
	}

	/** Stands one (or, {@code all}, as many as fit) of {@code held} on the block. */
	public void insert(ItemStack held, boolean all, boolean creative) {
		if (!accepts(held)) {
			return;
		}
		int moved = Math.min(all ? held.getCount() : 1, Chopping.HOLDS - logs.getCount());
		if (logs.isEmpty()) {
			logs = held.copyWithCount(moved);
			blows = 0;
		} else {
			logs.grow(moved);
		}
		if (!creative) {
			held.shrink(moved);
		}
		changed();
	}

	/** Takes every log back off. */
	public ItemStack take() {
		ItemStack out = logs;
		logs = ItemStack.EMPTY;
		blows = 0;
		changed();
		return out;
	}

	/** One blow of {@code axe} at the top log; enough of them and it splits into planks beside the block. */
	public void chop(ServerLevel level, Player player, InteractionHand hand, ItemStack axe) {
		Item planks = Chopping.planksOf(logs);
		long now = level.getGameTime();
		if (planks == null || now - lastChop < Chopping.CHOP_GAP) {
			return;
		}
		lastChop = now;
		BlockPos pos = worldPosition;
		BlockState log = ((BlockItem) logs.getItem()).getBlock().defaultBlockState();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, log), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
			6, 0.15, 0.1, 0.15, 0.05);
		if (++blows < Chopping.chops(axe)) {
			level.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 0.7F, 1.1F + level.getRandom().nextFloat() * 0.3F);
			setChanged();
			return;
		}
		blows = 0;
		logs.shrink(1);
		if (logs.isEmpty()) {
			logs = ItemStack.EMPTY;
		}
		Block.popResource(level, pos.above(), new ItemStack(planks, Chopping.planks(axe)));
		if (level.getRandom().nextFloat() < Chopping.SAWDUST_CHANCE) {
			Block.popResource(level, pos.above(), new ItemStack(StoneAge.SAWDUST));
		}
		level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.2F);
		level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		axe.hurtAndBreak(1, player, hand);
		changed();
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
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), logs);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		logs = input.read("logs", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		blows = input.getIntOr("blows", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("logs", ItemStack.OPTIONAL_CODEC, logs);
		output.putInt("blows", blows);
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
