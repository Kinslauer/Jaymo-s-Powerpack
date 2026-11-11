package com.jaymospowers.client.power.chrono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Marked run, the running half: from where you stand to the marked place by yourself, in a blur, along a path around
 * obstacles found on the blocks the client has loaded (a search on a grid, 8 ways, a step up of one block, drops of up to
 * {@link #MAX_DROP}). Within {@link #MAX_DISTANCE} blocks of the mark only. The client drives the run (movement is its to
 * decide, like the wall run) at {@link #RUN_SPEED}; any movement key, running out of path, getting stuck or losing Overclock
 * cancels it. Water is not crossed (the path goes round it).
 */
final class ChronoMarkRun {
	// ---- tuning ----
	/** The farthest mark that will be run to (blocks, straight line), nodes the search may open at most, the deepest drop it takes. */
	static final double MAX_DISTANCE = 128.0;
	static final int MAX_NODES = 40000, MAX_DROP = 3;
	/** Speed along the path (blocks a tick), how close to a waypoint counts as there (blocks), how fast the body turns to it (degrees a tick). */
	static final double RUN_SPEED = 0.85, REACHED = 0.65, TURN = 40.0;
	/** Stuck: ticks without moving this far (blocks). */
	static final int STUCK_TICKS = 20;
	static final double STUCK_MOVE = 0.15;

	private static List<Vec3> path = new ArrayList<>();
	private static int next;
	private static Vec3 stuckAt = Vec3.ZERO;
	private static int stuckFor;

	private ChronoMarkRun() {
	}

	static boolean running() {
		return !path.isEmpty();
	}

	static void stop() {
		path = new ArrayList<>();
		next = 0;
	}

	/** Searches a path from the player to {@code goal} and starts the run. False (with no run) if there is none. */
	static boolean start(LocalPlayer player, BlockPos goal) {
		Level level = player.level();
		if (player.distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5) > MAX_DISTANCE * MAX_DISTANCE) {
			return false;
		}
		List<BlockPos> found = search(level, player.blockPosition(), goal);
		if (found == null) {
			return false;
		}
		path = new ArrayList<>();
		for (BlockPos p : found) {
			path.add(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
		}
		next = 0;
		stuckAt = player.position();
		stuckFor = 0;
		return true;
	}

	/** Each client tick while a run is on. Returns false when the run is over (or was cancelled). */
	static boolean tick(LocalPlayer player) {
		var keys = player.input.keyPresses;
		if (path.isEmpty()) {
			return false;
		}
		if (keys.forward() || keys.backward() || keys.left() || keys.right() || keys.jump() || keys.shift() || player.isPassenger()
			|| !player.isAlive() || player.isFallFlying()) {
			stop();
			return false;
		}
		while (next < path.size() && Math.hypot(path.get(next).x - player.getX(), path.get(next).z - player.getZ()) < REACHED
			&& Math.abs(path.get(next).y - player.getY()) < 1.6) {
			next++;
		}
		if (next >= path.size()) {
			stop();
			return false;
		}
		if (++stuckFor >= STUCK_TICKS) {
			if (player.position().distanceTo(stuckAt) < STUCK_MOVE) {
				stop();
				return false;
			}
			stuckAt = player.position();
			stuckFor = 0;
		}
		Vec3 to = path.get(next);
		double dx = to.x - player.getX(), dz = to.z - player.getZ();
		double d = Math.hypot(dx, dz);
		if (d > 1.0E-4) {
			double speed = Math.min(RUN_SPEED, d + 0.2);
			player.setDeltaMovement(dx / d * speed, player.getDeltaMovement().y, dz / d * speed);
			float yaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
			player.setYRot(Mth.rotLerp((float)Mth.clamp(TURN / Math.max(1.0F, Math.abs(Mth.wrapDegrees(yaw - player.getYRot()))), 0.0, 1.0), player.getYRot(), yaw));
		}
		return true;
	}

	// ---------------------------------------------------------------- the search

	private record Node(BlockPos pos, double cost, double total) {}

	private static boolean passable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	private static boolean solidFloor(Level level, BlockPos below) {
		BlockState state = level.getBlockState(below);
		return !state.getCollisionShape(level, below).isEmpty() && state.getFluidState().isEmpty();
	}

	private static boolean standable(Level level, BlockPos pos) {
		return level.hasChunkAt(pos) && passable(level, pos) && passable(level, pos.above()) && solidFloor(level, pos.below());
	}

	private static List<BlockPos> search(Level level, BlockPos start, BlockPos goal) {
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.total, b.total));
		Map<BlockPos, BlockPos> from = new HashMap<>();
		Map<BlockPos, Double> best = new HashMap<>();
		open.add(new Node(start, 0.0, heuristic(start, goal)));
		best.put(start, 0.0);
		int opened = 0;
		while (!open.isEmpty() && opened++ < MAX_NODES) {
			Node node = open.poll();
			if (node.cost > best.getOrDefault(node.pos, Double.MAX_VALUE)) {
				continue;
			}
			BlockPos p = node.pos;
			if (Math.abs(p.getX() - goal.getX()) <= 1 && Math.abs(p.getZ() - goal.getZ()) <= 1 && Math.abs(p.getY() - goal.getY()) <= 2) {
				List<BlockPos> out = new ArrayList<>();
				for (BlockPos at = p; at != null; at = from.get(at)) {
					out.add(at);
				}
				Collections.reverse(out);
				return out;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					boolean diagonal = dx != 0 && dz != 0;
					for (int dy = 1; dy >= -MAX_DROP; dy--) {
						BlockPos to = p.offset(dx, dy, dz);
						if (Math.hypot(to.getX() - start.getX(), to.getZ() - start.getZ()) > MAX_DISTANCE + 16.0 || !standable(level, to)) {
							continue;
						}
						// the way there is clear: the column beside, at the walking height (and a step up needs room overhead)
						BlockPos side = p.offset(dx, Math.max(dy, 0), dz);
						if (!passable(level, side) || !passable(level, side.above()) || dy > 0 && !passable(level, p.above(2))) {
							continue;
						}
						if (diagonal && (!passable(level, p.offset(dx, 0, 0)) || !passable(level, p.offset(0, 0, dz))
							|| !passable(level, p.offset(dx, 1, 0)) || !passable(level, p.offset(0, 1, dz)))) {
							continue;
						}
						double cost = node.cost + (diagonal ? 1.42 : 1.0) + Math.abs(dy) * 0.3;
						if (cost < best.getOrDefault(to, Double.MAX_VALUE)) {
							best.put(to, cost);
							from.put(to, p);
							open.add(new Node(to, cost, cost + heuristic(to, goal)));
						}
						break; // the highest standable level in this direction is the way
					}
				}
			}
		}
		return null;
	}

	private static double heuristic(BlockPos a, BlockPos b) {
		return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
	}
}
