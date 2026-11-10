package com.jaymoskingdoms.astronomy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity behind an armillary sphere: it remembers when the sky was
 * last read here (world time, day). The readings themselves are written in the
 * reader's Astronomer's Journal ({@link Measurements}).
 */
public final class ArmillaryBlockEntity extends BlockEntity {
    /** World time of day of the last reading. */
    public long measureTime;
    /** Which in-game day the last reading was taken. */
    public long measureDay;

    public ArmillaryBlockEntity(BlockPos pos, BlockState state) {
        super(ArmillarySphereBlock.ENTITY, pos, state);
    }

    /** Record when the sky was read here. */
    public void record(ServerLevel level) {
        this.measureTime = level.getGameTime();
        this.measureDay = level.getGameTime() / 24000L;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("MeasureTime", measureTime);
        output.putLong("MeasureDay", measureDay);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.measureTime = input.getLongOr("MeasureTime", 0L);
        this.measureDay = input.getLongOr("MeasureDay", 0L);
    }
}
