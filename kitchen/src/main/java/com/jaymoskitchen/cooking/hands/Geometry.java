package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.delight.CookwareBlock;
import net.blay09.mods.cookingforblockheads.block.OvenBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where things are in the world at a station, for the pointing games (round a pot, round a grinder): the same on the
 * server, which judges, and on the client, which aims.
 */
public final class Geometry {
	/** Closest and furthest from the centre (blocks) the pointer counts as stirring; nearer or further it is off the pot. */
	public static final float MIN_RADIUS = 0.15F, MAX_RADIUS = 1.6F;

	private Geometry() {
	}

	/** The middle of the pot's top: the point to circle. A pot set down, a pot on an oven's hob, or the top of a furnace or smoker. */
	public static Vec3 potCenter(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		double y = pos.getY() + 1.05;
		if (state.getBlock() instanceof CookwareBlock) {
			y = pos.getY() + 0.65;
		} else if (state.getBlock() instanceof OvenBlock) {
			y = pos.getY() + 1.25;
		}
		return new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
	}

	/** The middle of a worktop's top (a board, a counter, a slab). */
	public static Vec3 topCenter(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		double top = state.getShape(level, pos).isEmpty() ? 1.0 : state.getShape(level, pos).max(Direction.Axis.Y);
		return new Vec3(pos.getX() + 0.5, pos.getY() + top + 0.02, pos.getZ() + 0.5);
	}

	/**
	 * The angle (radians) of where the look ray from {@code eye} along {@code look} meets the level plane through
	 * {@code center}, round that centre; NaN if it points away from the plane or off the circle.
	 */
	public static float pointedAngle(Vec3 eye, Vec3 look, Vec3 center) {
		if (Math.abs(look.y) < 1.0E-4) {
			return Float.NaN;
		}
		double t = (center.y - eye.y) / look.y;
		if (t <= 0.0 || t > 12.0) {
			return Float.NaN;
		}
		double dx = eye.x + look.x * t - center.x;
		double dz = eye.z + look.z * t - center.z;
		double radius = Math.sqrt(dx * dx + dz * dz);
		if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
			return Float.NaN;
		}
		return (float) Mth.atan2(dz, dx);
	}

	/** Where along the block (0 to 1, west to east) the look ray meets the plane through {@code center}; NaN if it misses the block. */
	public static float pointedAcross(Vec3 eye, Vec3 look, Vec3 center) {
		if (Math.abs(look.y) < 1.0E-4) {
			return Float.NaN;
		}
		double t = (center.y - eye.y) / look.y;
		if (t <= 0.0 || t > 12.0) {
			return Float.NaN;
		}
		double u = eye.x + look.x * t - (center.x - 0.5);
		double v = eye.z + look.z * t - (center.z - 0.5);
		if (v < -0.2 || v > 1.2 || u < -0.2 || u > 1.2) {
			return Float.NaN;
		}
		return (float) Mth.clamp(u, 0.0, 1.0);
	}

	/** The signed turn (radians, -pi to pi) from angle {@code from} to {@code to}. */
	public static float turn(float from, float to) {
		return Mth.wrapDegrees((to - from) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
	}
}
