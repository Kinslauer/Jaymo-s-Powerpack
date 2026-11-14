package com.jaymospowers.tinkering.smeltery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** A block that holds molten metal of its own (the seared tank, the melter, the channel): the pool, saved and sent to players. */
public abstract class MoltenEntity extends BlockEntity implements MoltenHolder {
	/** The pool is sent to players at most this often (ticks). */
	static final int SYNC_TICKS = 5;

	protected final Tank tank = new Tank();
	private boolean dirty;
	private int ticks;

	protected MoltenEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity) {
		super(type, pos, state);
		tank.setCapacity(capacity);
	}

	public Tank tank() {
		return tank;
	}

	/** The liquid shown in it, or null when empty. */
	public Tank.@Nullable Layer shown() {
		return tank.bottom();
	}

	protected void changed() {
		dirty = true;
	}

	/** A server tick's own work (melting, flowing). */
	protected void work(ServerLevel level, BlockState state) {
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, MoltenEntity entity) {
		entity.work((ServerLevel) level, state);
		if (++entity.ticks % SYNC_TICKS == 0 && entity.dirty) {
			entity.dirty = false;
			entity.setChanged();
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public @Nullable Molten offers() {
		Tank.Layer bottom = tank.bottom();
		return bottom == null ? null : bottom.molten;
	}

	@Override
	public int drain(Molten molten, int amount) {
		int taken = tank.drain(molten, amount, false);
		if (taken > 0) {
			changed();
		}
		return taken;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		int capacity = tank.capacity();
		tank.load(input);
		tank.setCapacity(capacity);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		tank.save(output);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		tank.save(out);
		return out.buildResult();
	}
}
