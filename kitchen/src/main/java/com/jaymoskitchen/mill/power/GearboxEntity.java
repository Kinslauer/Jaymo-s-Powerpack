package com.jaymoskitchen.mill.power;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A gearbox's works. Turned through its input ({@link ShaftDriven#turnFromShaft}), it follows a shaft out of each other
 * face ({@link Shafts#follow}) and turns every machine it finds there at an equal share of the rate, so one windmill on
 * three machines runs each at a third. A gearbox never drives another gearbox (one per run, so no loop can keep itself
 * turning). While powered by redstone it takes no turn and its out-shafts stop.
 */
public class GearboxEntity extends BlockEntity implements ShaftDriven {
	/** Out-shafts still turning after the last turn in (ticks); a little over the rotor's own drive. */
	static final int RUN_ON = RotorEntity.DRIVE_TICKS;

	/** Out-shaft pieces set turning, to stop them again. */
	private List<BlockPos> turning = new ArrayList<>();
	/** Game time the last turn in runs out. */
	private long until;

	public GearboxEntity(BlockPos pos, BlockState state) {
		super(Power.GEARBOX_ENTITY, pos, state);
	}

	@Override
	public boolean takesShaftFrom(Direction side) {
		return side == getBlockState().getValue(GearboxBlock.FACING) && !getBlockState().getValue(GearboxBlock.POWERED);
	}

	@Override
	public void turnFromShaft(float rate, int ticks) {
		if (level == null || level.isClientSide() || getBlockState().getValue(GearboxBlock.POWERED)) {
			return;
		}
		Direction in = getBlockState().getValue(GearboxBlock.FACING);
		List<BlockPos> run = new ArrayList<>();
		List<ShaftDriven> machines = new ArrayList<>();
		for (Direction out : Direction.values()) {
			if (out == in) {
				continue;
			}
			List<BlockPos> leg = new ArrayList<>();
			ShaftDriven machine = Shafts.follow(level, worldPosition, out, leg);
			if (machine != null && !(machine instanceof GearboxEntity)) {
				machines.add(machine);
			}
			run.addAll(leg);
		}
		for (ShaftDriven machine : machines) {
			machine.turnFromShaft(rate / machines.size(), ticks);
		}
		until = level.getGameTime() + Math.max(ticks, RUN_ON);
		setTurning(run);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, GearboxEntity gearbox) {
		if (!gearbox.turning.isEmpty() && (level.getGameTime() >= gearbox.until || state.getValue(GearboxBlock.POWERED))) {
			gearbox.setTurning(List.of());
		}
	}

	private void setTurning(List<BlockPos> now) {
		for (BlockPos old : turning) {
			if (!now.contains(old)) {
				Shafts.setTurning(level, old, false);
			}
		}
		for (BlockPos piece : now) {
			Shafts.setTurning(level, piece, true);
		}
		if (!turning.equals(now)) {
			turning = new ArrayList<>(now);
			setChanged();
		}
	}

	/** Taken down: its out-shafts stop. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !level.isClientSide()) {
			setTurning(List.of());
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		turning = new ArrayList<>(input.read("Turning", BlockPos.CODEC.listOf()).orElse(List.of()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("Turning", BlockPos.CODEC.listOf(), turning);
	}
}
