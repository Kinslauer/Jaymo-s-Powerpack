package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * The block entity behind a scrying orb: it remembers who is using it and whether it's active.
 */
public final class ScryingOrbBlockEntity extends BlockEntity {
    /** The UUID of the player currently using this orb, or null if idle. */
    @Nullable
    private UUID user;

    public ScryingOrbBlockEntity(BlockPos pos, BlockState state) {
        super(ScryingOrbBlock.ENTITY, pos, state);
    }

    /** Whether the orb is currently idle (no one using it). */
    public boolean isIdle() {
        return user == null;
    }

    /** Mark the orb as active for the given player. */
    public void setActive(UUID player) {
        this.user = player;
        setChanged();
        // Update block state to emit light
        if (level != null && !level.isClientSide()) {
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(ScryingOrbBlock.ACTIVE, true));
        }
    }

    /** Mark the orb as idle. */
    public void setIdle() {
        this.user = null;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(ScryingOrbBlock.ACTIVE, false));
        }
    }

    @Nullable
    public UUID getUser() {
        return user;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (user != null) {
            output.putUUID(USER_KEY, user);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.user = input.getUUID(USER_KEY).orElse(null);
    }
}