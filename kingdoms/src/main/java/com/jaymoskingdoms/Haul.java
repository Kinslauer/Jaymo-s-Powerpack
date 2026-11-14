package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A siege engine being pushed along the ground by its crew ({@link SiegeTrain}): a {@link Mangonel}, or a rich realm's
 * field {@link Cannon}. It rolls toward {@link #to} only while one of its crew is within {@link #PUSH_REACH} of it,
 * follows the ground up and down a block at a time, steers round what stops it, and is lifted over what it can't get
 * round after {@link #STUCK_TICKS}. Never saved: an engine in the field goes when its land unloads.
 */
final class Haul {
	/** How fast it's pushed (blocks per tick), and how fast it turns to its heading (degrees per tick). */
	static final double SPEED = 0.13;
	static final float TURN = 5.0F;
	/** A crewman this near it is pushing it; it stops this near where it's going. */
	static final double PUSH_REACH = 5.0, ARRIVE = 1.5;
	/** The ways it tries round something in its path (degrees off its line). */
	static final float[] ROUND = {0.0F, 35.0F, -35.0F, 70.0F, -70.0F};
	/** It climbs this much at a step and drops this far (blocks). */
	static final double STEP_UP = 1.05;
	static final int DROP = 4;
	/** Stopped this long (ticks), it's lifted this far on, if the ground there is within this of its own height. */
	static final int STUCK_TICKS = 100;
	static final double LIFT_AHEAD = 3.0, LIFT_HEIGHT = 6.0;

	/** The soldiers who push it and work it; the first works it. */
	final List<UUID> crew = new ArrayList<>();
	/** Where it's being pushed to, or null when it's set down. */
	@Nullable Vec3 to;
	private int stuck;

	/** Its crew still alive and in the world. */
	List<PathfinderMob> crew(ServerLevel level) {
		List<PathfinderMob> out = new ArrayList<>();
		for (UUID id : this.crew) {
			if (level.getEntity(id) instanceof PathfinderMob m && m.isAlive() && !Downed.isDowned(m)) {
				out.add(m);
			}
		}
		return out;
	}

	/** The crewman who works it (the first still standing), or null. */
	@Nullable PathfinderMob operator(ServerLevel level) {
		List<PathfinderMob> crew = this.crew(level);
		return crew.isEmpty() ? null : crew.getFirst();
	}

	/** One tick of pushing {@code engine} toward {@link #to}. True if it moved. */
	boolean step(ServerLevel level, Entity engine) {
		if (this.to == null) {
			return false;
		}
		double dx = this.to.x - engine.getX(), dz = this.to.z - engine.getZ();
		double far = Math.sqrt(dx * dx + dz * dz);
		if (far < ARRIVE) {
			return false;
		}
		boolean pushed = false;
		for (PathfinderMob m : this.crew(level)) {
			pushed |= m.distanceToSqr(engine) < PUSH_REACH * PUSH_REACH;
		}
		if (!pushed) {
			return false;
		}
		float line = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		for (float off : ROUND) {
			float heading = line + off;
			double x = engine.getX() - Mth.sin(heading * Mth.DEG_TO_RAD) * SPEED, z = engine.getZ() + Mth.cos(heading * Mth.DEG_TO_RAD) * SPEED;
			double y = ground(level, x, engine.getY(), z);
			if (Double.isNaN(y)) {
				continue;
			}
			engine.setYRot(engine.getYRot() + Mth.clamp(Mth.wrapDegrees(heading - engine.getYRot()), -TURN, TURN));
			engine.setPos(x, y, z);
			this.stuck = 0;
			return true;
		}
		if (++this.stuck > STUCK_TICKS) {
			this.stuck = 0;
			BlockPos column = BlockPos.containing(engine.getX() + dx / far * LIFT_AHEAD, engine.getY(), engine.getZ() + dz / far * LIFT_AHEAD);
			if (com.jaymoskingdoms.Loaded.made(level, column)) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				if (Math.abs(top.getY() - engine.getY()) <= LIFT_HEIGHT && level.getFluidState(top).isEmpty() && level.getFluidState(top.below()).isEmpty()) {
					engine.setPos(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
					return true;
				}
			}
		}
		return false;
	}

	/** The height of the ground an engine at {@code y} would stand on at this column, or NaN if it can't roll there (a step too high, a drop too deep, water). */
	private static double ground(ServerLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int top = Mth.floor(y + 1.0);
		for (int by = top; by >= top - DROP; by--) {
			pos.set(Mth.floor(x), by, Mth.floor(z));
			BlockState state = level.getBlockState(pos);
			if (!state.getFluidState().isEmpty()) {
				return Double.NaN;
			}
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				double height = by + shape.max(Direction.Axis.Y);
				return height > y + STEP_UP ? Double.NaN : height;
			}
		}
		return Double.NaN;
	}
}
