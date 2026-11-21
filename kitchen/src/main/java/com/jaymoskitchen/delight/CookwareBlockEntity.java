package com.jaymoskitchen.delight;

import com.jaymoskitchen.cooking.HandsOnSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Block entity for the Cooking Pot and Frying Pan (CookwareBlock).
 * Stores per-player hands-on cooking sessions for STIR (pot) and GRILL/SEAR (pan).
 */
public class CookwareBlockEntity extends BlockEntity {
	/** Active hands-on cooking sessions on this cookware, keyed by player UUID. */
	private final Map<UUID, HandsOnSession> handsOnSessions = new HashMap<>();

	public CookwareBlockEntity(BlockPos pos, BlockState state) {
		super(Delight.COOKWARE_ENTITY, pos, state);
	}

	/** Returns the hands-on sessions on this cookware. */
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
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		// Note: hands-on sessions are not persisted (they're short-lived)
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		// Note: hands-on sessions are not persisted (they're short-lived)
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		return output.buildResult();
	}
}