package com.jaymoskitchen.mill.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * The two power sources (after Better With Mods' windmill and waterwheel; MIT, our own code): a big four-sailed
 * windmill and a small paddle wheel, each set on the end of a level shaft, turning in the upright plane across it.
 */
public enum Rotor {
	/** 13 by 13: four sails on a hub. */
	WINDMILL("windmill", 6, 0.6F),
	/** 5 by 5: eight paddles. */
	WATERWHEEL("waterwheel", 2, 2.0F);

	/** A windmill turns only above sea level, with at least this much sky light in front of its hub. */
	static final int WINDMILL_MIN_SKY = 12;
	/** A windmill's speed in clear weather, rain and a thunderstorm. */
	static final float WIND_CLEAR = 1.0F, WIND_RAIN = 1.5F, WIND_THUNDER = 2.0F;
	/** A waterwheel's speed while flowing water runs past its lower half. */
	static final float WATER_SPEED = 1.0F;

	public final String id;
	/** Blocks from the hub to the tip of a sail or paddle: the sweep is a square this far each way. */
	public final int radius;
	/** Degrees a turn the rotor shows per tick at speed 1. */
	public final float degreesPerTick;

	Rotor(String id, int radius, float degreesPerTick) {
		this.id = id;
		this.radius = radius;
		this.degreesPerTick = degreesPerTick;
	}

	/** One way across the sweep (level, along the plane): the shaft's axis turned a quarter. */
	public static Direction across(Direction facing) {
		return facing.getClockWise();
	}

	/**
	 * How fast it turns now, with its hub at {@code pos} and its shaft {@code facing} from it: 0 when it can't
	 * (blocked, no wind, no flowing water), negative for the other way round (a waterwheel follows the current).
	 */
	public float speed(Level level, BlockPos pos, Direction facing) {
		if (!sweepClear(level, pos, facing)) {
			return 0.0F;
		}
		return switch (this) {
			case WINDMILL -> wind(level, pos, facing);
			case WATERWHEEL -> water(level, pos, facing);
		};
	}

	/** Every spot the sails or paddles pass through is open (air, plants, snow; water too, for a waterwheel). */
	public boolean sweepClear(Level level, BlockPos pos, Direction facing) {
		Direction across = across(facing);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int a = -radius; a <= radius; a++) {
			for (int b = -radius; b <= radius; b++) {
				if (a == 0 && b == 0) {
					continue;
				}
				at.setWithOffset(pos, across.getStepX() * a, b, across.getStepZ() * a);
				if (!level.isLoaded(at)) {
					return false;
				}
				BlockState state = level.getBlockState(at);
				if (!state.canBeReplaced() && !(this == WATERWHEEL && state.getFluidState().is(FluidTags.WATER))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Above sea level, open to the sky in front, not in the Nether or the End; faster in rain and storms. */
	private static float wind(Level level, BlockPos pos, Direction facing) {
		if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling() || pos.getY() <= level.getSeaLevel()
			|| level.getBrightness(LightLayer.SKY, pos.relative(facing.getOpposite())) < WINDMILL_MIN_SKY) {
			return 0.0F;
		}
		return level.isThundering() ? WIND_THUNDER : level.isRaining() ? WIND_RAIN : WIND_CLEAR;
	}

	/**
	 * Flowing (not still) water in the lower half of the wheel turns it, the way the current runs past the bottom.
	 * Checks the bottom row and the two lowest spots of each side.
	 */
	private float water(Level level, BlockPos pos, Direction facing) {
		Direction across = across(facing);
		Vec3 sideways = new Vec3(across.getStepX(), 0, across.getStepZ());
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int flowing = 0;
		double push = 0.0;
		for (int a = -radius; a <= radius; a++) {
			for (int b = -radius; b < 0; b++) {
				boolean edge = b == -radius || Math.abs(a) == radius;
				if (!edge) {
					continue;
				}
				at.setWithOffset(pos, across.getStepX() * a, b, across.getStepZ() * a);
				FluidState fluid = level.getFluidState(at);
				if (!fluid.is(FluidTags.WATER)) {
					continue;
				}
				Vec3 flow = fluid.getFlow(level, at);
				if (fluid.isSource() && flow.lengthSqr() < 1.0E-4) {
					continue;
				}
				flowing++;
				// at the bottom the current pushes the rim along; down one side it pulls that side down
				push += b == -radius ? flow.dot(sideways) : (a > 0 ? 1.0 : -1.0);
			}
		}
		if (flowing == 0) {
			return 0.0F;
		}
		return push < 0.0 ? -WATER_SPEED : WATER_SPEED;
	}
}
