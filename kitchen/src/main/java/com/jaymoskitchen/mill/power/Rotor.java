package com.jaymoskitchen.mill.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
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
	/**
	 * (GT New Horizons picks, patch 8) A windmill's speed is the wind's strength ({@link Wind#strength}, about 0.45 to
	 * 1.5) times this, so a fresh breeze (about 0.7) square onto good sails, high and in the open, turns it at about 1.
	 */
	static final float WIND_RATE = 1.5F;
	/** Its speed with the wind edge-on to its sails, as a share of head-on (either side is head-on). */
	static final float FACING_LEAST = 0.6F;
	/**
	 * The hub's height above sea level: at sea level it gets {@link #HEIGHT_LOW} of the wind, at {@link #HEIGHT_FULL}
	 * blocks up all of it, and up to {@link #HEIGHT_BEST} of it at {@link #HEIGHT_TOP} blocks up.
	 */
	static final float HEIGHT_LOW = 0.7F, HEIGHT_BEST = 1.25F;
	static final int HEIGHT_FULL = 20, HEIGHT_TOP = 60;
	/**
	 * Open air round it: spots sampled every {@link #OPEN_STEP} blocks across the sweep, 2 and {@link #OPEN_DEPTH} blocks
	 * before and behind it; boxed in by walls and trees it gets {@link #OPEN_LEAST} of the wind.
	 */
	static final int OPEN_STEP = 3, OPEN_DEPTH = 5;
	static final float OPEN_LEAST = 0.4F;
	/** The most a windmill turns at, in a gale (as fast as it ever turned in a thunderstorm before). */
	static final float WIND_MOST = 2.0F;
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
				if (!com.jaymoskitchen.Loaded.made(level, at)) {
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

	/**
	 * Whether a windmill's hub here is out in the weather at all: above sea level, open to the sky in front, not in the
	 * Nether or the End.
	 */
	public static boolean exposed(Level level, BlockPos pos, Direction facing) {
		return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling() && pos.getY() > level.getSeaLevel()
			&& level.getBrightness(LightLayer.SKY, pos.relative(facing.getOpposite())) >= WINDMILL_MIN_SKY;
	}

	/**
	 * (GT New Horizons picks, patch 8) The wind at the hub ({@link Wind}: Kingdoms' own when it's loaded), how square it
	 * blows onto the sails, how high the hub is and how open the air round it. The sails' own pull is the rotor's
	 * ({@link RotorEntity}).
	 */
	private float wind(Level level, BlockPos pos, Direction facing) {
		if (!exposed(level, pos, facing)) {
			return 0.0F;
		}
		float square = Mth.lerp(Wind.along(level, facing), FACING_LEAST, 1.0F);
		int up = pos.getY() - level.getSeaLevel();
		float height = up <= HEIGHT_FULL ? Mth.clampedMap(up, 0.0F, HEIGHT_FULL, HEIGHT_LOW, 1.0F)
			: Mth.clampedMap(up, HEIGHT_FULL, HEIGHT_TOP, 1.0F, HEIGHT_BEST);
		float open = Mth.lerp(openAir(level, pos, facing), OPEN_LEAST, 1.0F);
		return Math.min(WIND_MOST, WIND_RATE * Wind.strength(level, pos) * square * height * open);
	}

	/** The share of the air before and behind the sweep that's open (sampled; unloaded spots count as shut). */
	private float openAir(Level level, BlockPos pos, Direction facing) {
		Direction across = across(facing);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int open = 0, all = 0;
		for (int depth : new int[] {2, OPEN_DEPTH}) {
			for (int side = -1; side <= 1; side += 2) {
				for (int a = -radius; a <= radius; a += OPEN_STEP) {
					for (int b = -radius; b <= radius; b += OPEN_STEP) {
						at.setWithOffset(pos, across.getStepX() * a + facing.getStepX() * depth * side, b,
							across.getStepZ() * a + facing.getStepZ() * depth * side);
						all++;
						if (com.jaymoskitchen.Loaded.made(level, at) && level.getBlockState(at).canBeReplaced()) {
							open++;
						}
					}
				}
			}
		}
		return all == 0 ? 0.0F : (float) open / all;
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
