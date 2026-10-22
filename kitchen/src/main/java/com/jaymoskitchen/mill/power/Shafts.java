package com.jaymoskitchen.mill.power;

import com.jaymoskitchen.mill.MachineFillerBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Following a shaft run from its source to the machine at the far end. */
public final class Shafts {
	/** The most straight shafts in a run (the one corner doesn't count). */
	public static final int SHAFT_MAX = 8;

	private Shafts() {
	}

	/**
	 * Follows the shaft that starts next to {@code from}, heading {@code dir}: straight shafts along the way, at most
	 * one corner, to the machine at the end. Every shaft piece passed is added to {@code run}.
	 *
	 * @return the machine at the end if it takes a shaft on that side, else null (no machine, too long, a second
	 * corner, a shaft that's turned the wrong way, or the run leaves the loaded world)
	 */
	public static @Nullable ShaftDriven follow(Level level, BlockPos from, Direction dir, List<BlockPos> run) {
		BlockPos.MutableBlockPos pos = from.mutable().move(dir);
		int shafts = 0;
		boolean cornered = false;
		while (level.isLoaded(pos)) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof ShaftBlock) {
				if (state.getValue(ShaftBlock.AXIS) != dir.getAxis() || ++shafts > SHAFT_MAX) {
					return null;
				}
			} else if (state.getBlock() instanceof ShaftCornerBlock) {
				Direction out = cornered ? null : ShaftCornerBlock.otherArm(state, dir.getOpposite());
				if (out == null) {
					return null;
				}
				cornered = true;
				dir = out;
			} else {
				ShaftDriven driven = driven(level, pos);
				return driven != null && driven.takesShaftFrom(dir.getOpposite()) ? driven : null;
			}
			run.add(pos.immutable());
			pos.move(dir);
		}
		return null;
	}

	/** The shaft-driven machine at this spot: its own block entity, or the machine under a tall machine's top half. */
	public static @Nullable ShaftDriven driven(Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof ShaftDriven driven) {
			return driven;
		}
		if (level.getBlockState(pos).getBlock() instanceof MachineFillerBlock && level.getBlockEntity(pos.below()) instanceof ShaftDriven driven) {
			return driven;
		}
		return null;
	}

	/** Sets a shaft piece turning or still (just for the look), if it is one and isn't already. */
	static void setTurning(Level level, BlockPos pos, boolean turning) {
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if ((state.getBlock() instanceof ShaftBlock || state.getBlock() instanceof ShaftCornerBlock)
			&& state.getValue(ShaftBlock.TURNING) != turning) {
			level.setBlock(pos, state.setValue(ShaftBlock.TURNING, turning), Block.UPDATE_CLIENTS);
		}
	}
}
