package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.HandsOnSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** What's lying on a cutting board waiting for the knife (one item; everyone sees it, see CuttingBoardRenderer).
 * Also stores per-player hands-on cooking sessions for CHOP. */
public class CuttingBoardEntity extends BlockEntity {
	private ItemStack item = ItemStack.EMPTY;
	/** Active hands-on cooking sessions on this board, keyed by player UUID. */
	private final Map<UUID, HandsOnSession> handsOnSessions = new HashMap<>();

	public CuttingBoardEntity(BlockPos pos, BlockState state) {
		super(Delight.CUTTING_BOARD_ENTITY, pos, state);
	}

	public ItemStack item() {
		return item;
	}

	public void setItem(ItemStack stack) {
		item = stack;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	/** Takes what's on the board off it. */
	public ItemStack take() {
		ItemStack taken = item;
		setItem(ItemStack.EMPTY);
		return taken;
	}

	/** Returns the hands-on sessions on this board. */
	public Map<UUID, HandsOnSession> getHandsOnSessions() {
		return handsOnSessions;
	}

	/** Adds or updates a hands-on session. */
	public void setHandsOnSession(UUID playerId, HandsOnSession session) {
		handsOnSessions.put(playerId, session);
		setChanged();
	}

	/** Removes a hands-on session. */
	public void removeHandsOnSession(UUID playerId) {
		handsOnSessions.remove(playerId);
		setChanged();
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !item.isEmpty()) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, item);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		item = input.read("item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		// Note: hands-on sessions are not persisted (they're short-lived)
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!item.isEmpty()) {
			output.store("item", ItemStack.OPTIONAL_CODEC, item);
		}
		// Note: hands-on sessions are not persisted (they're short-lived)
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		output.store("item", ItemStack.OPTIONAL_CODEC, item);
		return output.buildResult();
	}
}
