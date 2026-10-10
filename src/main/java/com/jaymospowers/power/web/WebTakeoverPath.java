package com.jaymospowers.power.web;

import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Debris;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Where a takeover drives you (rampage, hunger, curiosity, the feeding hunt, a run for its life). The server finds a
 * real way on foot (a bounded A* over block positions, sized for a player: two blocks tall, steps of one, charged leaps
 * up taller ledges, drops of a few) and sends the waypoints ({@link WebNet.TakeoverPath}); the client walks them.
 * Running, it floods out for the safest point it can reach. No way at all, or stuck, and the tendrils tear the blocking
 * blocks out as a clump and fling it aside, a slot at a time, then it looks again.
 */
final class WebTakeoverPath {
	// ===== pathing ===================================================================================================
	/** How often it looks for a new way (ticks), or at once if the target has moved this far (blocks) since. */
	static final int REPATH_TICKS = 10;
	static final double REPATH_TARGET_MOVE = 3.0;
	/** The search's budget: nodes expanded, and how far from you it may wander (blocks, horizontally). */
	static final int MAX_NODES = 1600;
	static final int SEARCH_RADIUS = 32;
	/** Drops it will walk off (blocks), and how far under a flying target it looks for ground. */
	static final int MAX_DROP = 3;
	static final int GROUND_SEARCH = 6;
	/** Arrived: this close to the target horizontally (blocks), and this close in height. */
	static final double GOAL_REACH = 1.6;
	static final int GOAL_REACH_Y = 2;
	/** Step costs on top of distance: a jump up, each block dropped, swimming, diagonals. */
	static final double COST_STEP_UP = 0.6, COST_DROP = 0.4, COST_WATER = 2.0, COST_DIAGONAL = 1.414;
	/**
	 * A ledge taller than a step is climbed with a charged leap (never more than {@link WebLeap#TAKEOVER_MAX_CHARGE} of a
	 * full charge): {@code LEAP_MIN_RISE} up to {@code LEAP_MAX_RISE} blocks, what that charge clears. It costs
	 * {@code COST_LEAP} plus {@code COST_LEAP_PER_BLOCK} a block of height on top of the step, so a walk round wins unless
	 * the leap saves real distance (or is the only way).
	 */
	static final int LEAP_MIN_RISE = 2;
	static final int LEAP_MAX_RISE = WebLeap.TAKEOVER_MAX_RISE;
	static final double COST_LEAP = 3.0, COST_LEAP_PER_BLOCK = 0.5;
	/** A partial path (the target out of reach) is only worth walking if it ends at least this much closer (blocks). */
	static final double PARTIAL_MIN_GAIN = 2.5;
	/** Waypoints sent at most (the rest come with the next re-path). */
	static final int PATH_SEND_MAX = 48;
	/** Straight at it instead: in this range (blocks), this level (blocks of height), and in clear sight, feet and eyes. */
	static final double DIRECT_RANGE = 7.0;
	static final double DIRECT_MAX_DY = 1.2;
	/** ...and ground under the whole line: checked every this many blocks, within this drop. */
	static final double DIRECT_GROUND_STEP = 0.5;
	static final int DIRECT_MAX_DROP = 2;
	// ===== running ===================================================================================================
	/** The flood for a safe point: nodes, the longest way it considers (cost), and how it scores a spot. */
	static final int FLEE_NODES = 1400;
	static final double FLEE_MAX_COST = 40.0;
	static final double FLEE_COST_WEIGHT = 0.35, FLEE_SKY_BONUS = 3.0;
	/** It counts as trapped unless the best spot puts at least this much more distance (blocks) between it and the threat. */
	static final double FLEE_MIN_GAIN = 4.0;
	// ===== stuck =====================================================================================================
	/** No progress (moved less than {@code STUCK_MOVE} blocks) for this long (ticks): it re-paths at once. */
	static final int STUCK_TICKS = 30;
	static final double STUCK_MOVE = 0.9;
	/** Stuck this many times in a row: it rips its way through instead. */
	static final int STUCK_RIP_AFTER = 2;
	/** After being stuck on the straight line, it keeps to paths for this long (ticks). */
	static final int DIRECT_BAN_TICKS = 60;
	// ===== ripping ===================================================================================================
	/** Nothing harder than this is torn out (obsidian, 50, and harder stay); nothing unbreakable, nothing with a block entity. */
	static final float RIP_MAX_HARDNESS = 20.0F;
	/**
	 * The tendrils dig into the blocking blocks and work them loose for {@code RIP_LASH_TICKS} (+ {@code RIP_TICKS_PER_HARDNESS}
	 * x the hardest one's hardness, + {@code RIP_TICKS_PER_BLOCK} for each block after the first; at most
	 * {@code RIP_LASH_MAX_TICKS}), then tear them out as one clump ({@link BlockCluster}, as Living Armor's tendrils do).
	 */
	static final int RIP_LASH_TICKS = 8;
	static final float RIP_TICKS_PER_HARDNESS = 2.0F;
	static final int RIP_TICKS_PER_BLOCK = 3;
	static final int RIP_LASH_MAX_TICKS = 30;
	/** The clump comes away this far toward you (blocks) out of the hole, before the tendril takes it. */
	static final double RIP_CLUMP_OUT = 0.15;
	/** Held up once the tendril has it (ticks), then flung aside; flung anyway this long after the tear (ticks). */
	static final int RIP_HOLD_TICKS = 6;
	static final int RIP_HOLD_TIMEOUT = 40;
	/**
	 * The fling: sideways, or up and sideways (whichever way is most open within {@code RIP_FLING_LOOK} blocks), never
	 * ahead along the tunnel nor back at you; this fast, lifted this much (blocks/tick).
	 */
	static final double RIP_FLING_SPEED = 1.2, RIP_FLING_LIFT = 0.25, RIP_FLING_LOOK = 4.0;
	/** Cells along the line it looks through for the blocking ones, and how far it can reach (blocks, from the eyes). */
	static final int RIP_LOOKAHEAD = 3;
	static final double RIP_REACH = 4.5;
	/** At most this many blocks torn out per takeover. */
	static final int RIP_MAX_BLOCKS = 32;
	/** When ripping can't help (nothing breakable), it waits this long before trying again (ticks). */
	static final int RIP_RETRY_TICKS = 40;
	/** The dread on your screen is refreshed this often while it rips (ticks), for this long. */
	static final int RIP_FX_EVERY = 40, RIP_FX_TICKS = 60;
	/** Debris puff from a block while it's worked at (1 is a normal puff). */
	static final float RIP_DEBRIS = 0.3F;
	// =================================================================================================================

	private static final int PASSABLE = 1, FLOOR = 2, DANGER = 4, WATER = 8, LOW = 16, UNLOADED = 32;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private WebTakeoverPath() {
	}

	/** One takeover's way (kept in the symbiote's per-player live state). */
	static final class Route {
		int mode = -1;
		List<BlockPos> nodes = List.of();
		long pathAt = Long.MIN_VALUE;
		Vec3 pathTarget = Vec3.ZERO;
		Vec3 progressPos = Vec3.ZERO;
		long progressAt;
		int stuck;
		long directBanUntil;
		long ripRetryAt;
		/** The blocks being worked loose (one clump), the one the tendrils aim at, and the way through (toward the slot). */
		List<BlockPos> ripSlot = List.of();
		@Nullable BlockPos ripping;
		Vec3 ripDir = Vec3.ZERO;
		long ripStart;
		long ripFxAt;
		int ripLashTicks;
		/** The torn clump a tendril holds before flinging it aside (-1 none), which tendril, when it was torn, when it was held. */
		int clumpId = -1;
		int clumpHand;
		long tornAt;
		long heldAt = -1L;
		int ripped;
		boolean whispered;
	}

	/**
	 * A takeover tick: keeps the way to {@code target} fresh (or, {@code flee}ing, away from it), notices when you're
	 * stuck, and rips through when there's no way. {@code reach} is how close counts as there (no need to move).
	 */
	static void tick(ServerPlayer player, Route route, Entity target, boolean flee, double reach, long now) {
		ServerLevel level = player.level();
		if (route.ripping != null || route.clumpId >= 0) {
			tickRip(player, route, now);
			return;
		}
		// progress: moved, or close enough to the prey to fight it
		boolean there = !flee && player.distanceTo(target) - target.getBbWidth() * 0.5 <= reach + 0.5;
		// (coiling for a leap counts: it's about to move)
		if (there || player.position().distanceToSqr(route.progressPos) > STUCK_MOVE * STUCK_MOVE || route.progressAt == 0L
			|| player.getAttached(WebPower.CHARGING) != null) {
			route.progressPos = player.position();
			route.progressAt = now;
			if (there) {
				route.stuck = 0;
			}
		} else if (now - route.progressAt > STUCK_TICKS) {
			route.progressPos = player.position();
			route.progressAt = now;
			route.stuck++;
			route.pathAt = Long.MIN_VALUE;
			if (route.mode == WebNet.TakeoverPath.DIRECT) {
				route.directBanUntil = now + DIRECT_BAN_TICKS;
			}
			if (route.stuck >= STUCK_RIP_AFTER && now >= route.ripRetryAt) {
				route.stuck = 0;
				Vec3 toward = flee ? awayPoint(player, target) : target.position();
				if (startRip(player, route, toward, now)) {
					return;
				}
			}
		}
		if (!flee && now >= route.directBanUntil && direct(player, target)) {
			if (route.mode != WebNet.TakeoverPath.DIRECT) {
				send(player, route, List.of(), WebNet.TakeoverPath.DIRECT, BlockPos.ZERO);
			}
			route.pathAt = now;
			route.pathTarget = target.position();
			return;
		}
		boolean moved = target.position().distanceToSqr(route.pathTarget) > REPATH_TARGET_MOVE * REPATH_TARGET_MOVE;
		if (now - route.pathAt < REPATH_TICKS && !moved) {
			return;
		}
		route.pathAt = now;
		route.pathTarget = target.position();
		BlockPos start = startNode(level, player);
		Search search = new Search(level, start);
		if (flee) {
			Node best = search.flee(target.position());
			if (best != null) {
				send(player, route, best.path(), WebNet.TakeoverPath.PATH, BlockPos.ZERO);
			} else if (now < route.ripRetryAt || !startRip(player, route, awayPoint(player, target), now)) {
				send(player, route, List.of(), WebNet.TakeoverPath.FALLBACK, BlockPos.ZERO);
			}
			return;
		}
		BlockPos goal = ground(level, target.blockPosition());
		Node end = search.chase(goal);
		if (end != null && (end.reached || flat(start, goal) - flat(end.pos, goal) >= PARTIAL_MIN_GAIN)) {
			send(player, route, end.path(), WebNet.TakeoverPath.PATH, BlockPos.ZERO);
		} else if (now < route.ripRetryAt || !startRip(player, route, target.position(), now)) {
			send(player, route, List.of(), WebNet.TakeoverPath.FALLBACK, BlockPos.ZERO);
		}
	}

	/** The takeover's over: any half-torn block's cracks fade, and a clump still held is flung aside. */
	static void stop(ServerPlayer player, @Nullable Route route) {
		if (route != null) {
			clearCracks(player, route);
			if (route.clumpId >= 0 && player.level().getEntity(route.clumpId) instanceof BlockCluster chunk && holds(player, route.clumpHand, chunk)) {
				WebTendrils.fling(player, route.clumpHand, flingVelocity(player, chunk, route.ripDir));
			}
			route.clumpId = -1;
		}
		if (player.getAttached(WebSymbiote.RIPPING) != null) {
			player.removeAttached(WebSymbiote.RIPPING);
		}
	}

	private static void send(ServerPlayer player, Route route, List<BlockPos> nodes, int mode, BlockPos focus) {
		route.mode = mode;
		route.nodes = nodes;
		List<BlockPos> sent = nodes.size() > PATH_SEND_MAX ? List.copyOf(nodes.subList(0, PATH_SEND_MAX)) : nodes;
		ServerPlayNetworking.send(player, new WebNet.TakeoverPath(sent, mode, focus));
	}

	/** In reach, near level, nothing in the way at the feet or the eyes. */
	private static boolean direct(ServerPlayer player, Entity target) {
		if (player.distanceTo(target) > DIRECT_RANGE || Math.abs(target.getY() - player.getY()) > DIRECT_MAX_DY) {
			return false;
		}
		if (!clear(player, player.getEyePosition(), target.getBoundingBox().getCenter())
			|| !clear(player, player.position().add(0.0, 0.3, 0.0), target.position().add(0.0, 0.3, 0.0))) {
			return false;
		}
		// and ground all the way (no lurching into a ravine it can see across)
		ServerLevel level = player.level();
		Map<Long, Integer> cache = new HashMap<>();
		Vec3 from = player.position(), to = target.position();
		int steps = Math.max(1, (int)Math.ceil(from.distanceTo(to) / DIRECT_GROUND_STEP));
		for (int i = 1; i <= steps; i++) {
			BlockPos at = BlockPos.containing(from.lerp(to, i / (double)steps).add(0.0, 0.01, 0.0));
			boolean ground = false;
			for (int k = 0; k <= DIRECT_MAX_DROP && !ground; k++) {
				int kind = kind(level, cache, at.below(k));
				if ((kind & (DANGER | UNLOADED)) != 0) {
					return false;
				}
				ground = (kind & (FLOOR | WATER)) != 0 || (kind & PASSABLE) == 0;
			}
			if (!ground) {
				return false;
			}
		}
		return true;
	}

	private static boolean clear(ServerPlayer player, Vec3 from, Vec3 to) {
		return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
	}

	/** A point well away from the threat (for ripping out of a dead end). */
	private static Vec3 awayPoint(ServerPlayer player, Entity threat) {
		Vec3 away = player.position().subtract(threat.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal().reverse() : away.normalize();
		return player.position().add(away.scale(8.0));
	}

	/** Where you stand: your feet block, or the first spot you can stand under you (mid-jump, mid-fall). */
	private static BlockPos startNode(ServerLevel level, ServerPlayer player) {
		BlockPos feet = BlockPos.containing(player.getX(), player.getY() + 0.01, player.getZ());
		return ground(level, feet);
	}

	/** The first spot to stand at or under {@code pos}, or {@code pos} itself. */
	private static BlockPos ground(ServerLevel level, BlockPos pos) {
		Map<Long, Integer> cache = new HashMap<>();
		for (int i = 0; i <= GROUND_SEARCH; i++) {
			BlockPos p = pos.below(i);
			if (standable(level, cache, p)) {
				return p;
			}
		}
		return pos;
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---- the block grid ----------------------------------------------------------------------------------------------

	private static int kind(ServerLevel level, Map<Long, Integer> cache, BlockPos pos) {
		long key = pos.asLong();
		Integer known = cache.get(key);
		if (known != null) {
			return known;
		}
		int kind;
		if (!level.isLoaded(pos)) {
			kind = UNLOADED;
		} else {
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(level, pos);
			kind = 0;
			if (shape.isEmpty()) {
				kind |= PASSABLE;
			} else {
				double top = shape.max(Direction.Axis.Y);
				if (top <= 1.0) {
					kind |= FLOOR;
				}
				if (top <= 0.5) {
					kind |= LOW;
				}
			}
			if (state.getFluidState().is(FluidTags.WATER)) {
				kind |= WATER;
			}
			if (state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.CACTUS)
				|| state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE)
				|| state.is(Blocks.COBWEB)) {
				kind |= DANGER;
			}
		}
		cache.put(key, kind);
		return kind;
	}

	private static boolean passable(ServerLevel level, Map<Long, Integer> cache, BlockPos pos) {
		int k = kind(level, cache, pos);
		return (k & PASSABLE) != 0 && (k & (DANGER | UNLOADED)) == 0;
	}

	/** A player can stand with their feet in {@code pos}: room for two blocks, something under them, nothing that burns. */
	private static boolean standable(ServerLevel level, Map<Long, Integer> cache, BlockPos pos) {
		int here = kind(level, cache, pos);
		if ((here & (DANGER | UNLOADED)) != 0) {
			return false;
		}
		if ((here & PASSABLE) == 0) {
			// a slab, carpet or snow layer: you stand on top of it, so it needs a little more headroom
			return (here & LOW) != 0 && passable(level, cache, pos.above()) && passable(level, cache, pos.above(2));
		}
		if (!passable(level, cache, pos.above())) {
			return false;
		}
		if ((here & WATER) != 0) {
			return true;
		}
		int below = kind(level, cache, pos.below());
		return (below & (DANGER | UNLOADED)) == 0 && ((below & FLOOR) != 0 || (below & WATER) != 0);
	}

	private static final class Node implements Comparable<Node> {
		final BlockPos pos;
		@Nullable Node parent;
		double g;
		double f;
		double h;
		boolean closed;
		boolean reached;

		Node(BlockPos pos) {
			this.pos = pos;
		}

		/** The waypoints from the start to here (the start itself left out). */
		List<BlockPos> path() {
			ArrayList<BlockPos> out = new ArrayList<>();
			for (Node n = this; n != null && n.parent != null; n = n.parent) {
				out.add(n.pos);
			}
			java.util.Collections.reverse(out);
			return out;
		}

		@Override
		public int compareTo(Node other) {
			return Double.compare(f, other.f);
		}
	}

	/** One bounded search over the grid around {@code start}. */
	private static final class Search {
		final ServerLevel level;
		final BlockPos start;
		final Map<Long, Integer> cache = new HashMap<>();
		final Map<Long, Node> nodes = new HashMap<>();
		final PriorityQueue<Node> open = new PriorityQueue<>();

		Search(ServerLevel level, BlockPos start) {
			this.level = level;
			this.start = start;
		}

		/** A* to the goal; the node that reached it, else the closest one it got to (null if it couldn't move at all). */
		@Nullable Node chase(BlockPos goal) {
			Node first = new Node(start);
			first.h = heuristic(start, goal);
			first.f = first.h;
			nodes.put(start.asLong(), first);
			open.add(first);
			Node best = first;
			int expanded = 0;
			while (!open.isEmpty() && expanded++ < MAX_NODES) {
				Node n = open.poll();
				if (n.closed) {
					continue;
				}
				n.closed = true;
				if (flat(n.pos, goal) <= GOAL_REACH && Math.abs(n.pos.getY() - goal.getY()) <= GOAL_REACH_Y) {
					n.reached = true;
					return n;
				}
				if (n.h < best.h) {
					best = n;
				}
				expand(n, goal);
			}
			return best == first ? null : best;
		}

		/** A flood out to {@code FLEE_MAX_COST}: the reachable spot that's best to run to, or null if none gains enough. */
		@Nullable Node flee(Vec3 threat) {
			Node first = new Node(start);
			nodes.put(start.asLong(), first);
			open.add(first);
			double from = Vec3.atBottomCenterOf(start).distanceTo(threat);
			Node best = null;
			double bestScore = -Double.MAX_VALUE;
			int expanded = 0;
			while (!open.isEmpty() && expanded++ < FLEE_NODES) {
				Node n = open.poll();
				if (n.closed) {
					continue;
				}
				n.closed = true;
				double away = Vec3.atBottomCenterOf(n.pos).distanceTo(threat);
				if (away >= from + FLEE_MIN_GAIN) {
					double score = away - n.g * FLEE_COST_WEIGHT + (level.canSeeSky(n.pos.above()) ? FLEE_SKY_BONUS : 0.0);
					if (score > bestScore) {
						bestScore = score;
						best = n;
					}
				}
				if (n.g < FLEE_MAX_COST) {
					expand(n, null);
				}
			}
			return best;
		}

		private static double heuristic(BlockPos a, BlockPos goal) {
			return Math.sqrt(a.distSqr(goal));
		}

		private void expand(Node n, @Nullable BlockPos goal) {
			BlockPos p = n.pos;
			for (int[] d : DIRS) {
				boolean diagonal = d[0] != 0 && d[1] != 0;
				BlockPos side = p.offset(d[0], 0, d[1]);
				if (Math.abs(side.getX() - start.getX()) > SEARCH_RADIUS || Math.abs(side.getZ() - start.getZ()) > SEARCH_RADIUS) {
					continue;
				}
				if (diagonal) {
					// no cutting corners: both sides clear, feet and head
					BlockPos a = p.offset(d[0], 0, 0), b = p.offset(0, 0, d[1]);
					if (!passable(level, cache, a) || !passable(level, cache, a.above()) || !passable(level, cache, b) || !passable(level, cache, b.above())) {
						continue;
					}
					if (standable(level, cache, side)) {
						visit(n, side, COST_DIAGONAL, goal);
					}
					continue;
				}
				if (standable(level, cache, side)) {
					visit(n, side, 1.0, goal);
				} else if (!passable(level, cache, side) || !passable(level, cache, side.above())) {
					// a step up: room overhead to jump it
					BlockPos up = side.above();
					if (standable(level, cache, up) && passable(level, cache, p.above(2))) {
						visit(n, up, 1.0 + COST_STEP_UP, goal);
					} else {
						leap(n, side, goal);
					}
				} else {
					// open, but nothing to stand on: a drop it can take
					for (int k = 1; k <= MAX_DROP; k++) {
						BlockPos down = side.below(k);
						if (standable(level, cache, down)) {
							visit(n, down, 1.0 + k * COST_DROP, goal);
							break;
						}
						if (!passable(level, cache, down)) {
							break;
						}
					}
				}
			}
		}

		/**
		 * Too tall to step: the lowest ledge on {@code side} it can leap up to ({@code LEAP_MIN_RISE} to
		 * {@code LEAP_MAX_RISE} blocks), with headroom all the way up over where it coils, and over the ledge's edge.
		 */
		private void leap(Node n, BlockPos side, @Nullable BlockPos goal) {
			BlockPos p = n.pos;
			// (no leaping out of water: there's nothing to push off)
			if ((kind(level, cache, p) & WATER) != 0 || !passable(level, cache, p.above(2)) || !passable(level, cache, p.above(3))) {
				return;
			}
			for (int h = LEAP_MIN_RISE; h <= LEAP_MAX_RISE; h++) {
				if (!passable(level, cache, p.above(h + 2))) {
					return;
				}
				BlockPos top = side.above(h);
				if (standable(level, cache, top) && passable(level, cache, top.above(2))) {
					visit(n, top, 1.0 + COST_LEAP + COST_LEAP_PER_BLOCK * h, goal);
					return;
				}
			}
		}

		private void visit(Node from, BlockPos pos, double step, @Nullable BlockPos goal) {
			double cost = step + ((kind(level, cache, pos) & WATER) != 0 ? COST_WATER : 0.0);
			double g = from.g + cost;
			long key = pos.asLong();
			Node node = nodes.get(key);
			if (node == null) {
				node = new Node(pos);
				node.h = goal == null ? 0.0 : heuristic(pos, goal);
				nodes.put(key, node);
			} else if (node.closed || g >= node.g) {
				return;
			}
			node.parent = from;
			node.g = g;
			node.f = g + node.h;
			open.add(node);
		}
	}

	// ---- ripping a way through ---------------------------------------------------------------------------------------

	/**
	 * No way round: the tendrils dig into what stands between you and {@code toward} (a 1x2 slot, or a step up or down
	 * if it's higher or lower), work it loose, and tear it out as one clump. False if there's nothing there it may break.
	 */
	private static boolean startRip(ServerPlayer player, Route route, Vec3 toward, long now) {
		if (route.ripped >= RIP_MAX_BLOCKS) {
			route.ripRetryAt = Long.MAX_VALUE;
			return false;
		}
		List<BlockPos> blocks = ripTargets(player, toward);
		if (blocks.isEmpty()) {
			route.ripRetryAt = now + RIP_RETRY_TICKS;
			return false;
		}
		if (blocks.size() > RIP_MAX_BLOCKS - route.ripped) {
			blocks = blocks.subList(0, RIP_MAX_BLOCKS - route.ripped);
		}
		ServerLevel level = player.level();
		route.ripSlot = new ArrayList<>(blocks);
		Vec3 middle = Vec3.ZERO;
		float hardest = 0.0F;
		for (BlockPos pos : blocks) {
			middle = middle.add(Vec3.atCenterOf(pos));
			hardest = Math.max(hardest, level.getBlockState(pos).getDestroySpeed(level, pos));
		}
		middle = middle.scale(1.0 / blocks.size());
		Vec3 dir = middle.subtract(player.position()).horizontal();
		route.ripDir = dir.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal().normalize() : dir.normalize();
		// the tendrils aim at the block nearest your eyes
		BlockPos focus = blocks.get(0);
		for (BlockPos pos : blocks) {
			if (Vec3.atCenterOf(pos).distanceToSqr(player.getEyePosition()) < Vec3.atCenterOf(focus).distanceToSqr(player.getEyePosition())) {
				focus = pos;
			}
		}
		route.ripping = focus;
		route.ripStart = now;
		route.ripFxAt = now;
		route.ripLashTicks = Math.min(RIP_LASH_MAX_TICKS,
			RIP_LASH_TICKS + Math.round(hardest * RIP_TICKS_PER_HARDNESS) + RIP_TICKS_PER_BLOCK * (blocks.size() - 1));
		player.setAttached(WebSymbiote.RIPPING, focus);
		send(player, route, List.of(), WebNet.TakeoverPath.RIP, focus);
		if (!route.whispered) {
			route.whispered = true;
			WebSymbiote.whisper(player, "rip");
		}
		WebSymbiote.trail(level, player.getEyePosition().add(0.0, -0.3, 0.0), Vec3.atCenterOf(focus));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.8F, 0.5F);
		return true;
	}

	/** The blocking blocks in the first cell along the line to {@code toward} (main axis first, then the other). */
	private static List<BlockPos> ripTargets(ServerPlayer player, Vec3 toward) {
		ServerLevel level = player.level();
		Vec3 to = toward.subtract(player.position());
		if (to.horizontalDistanceSqr() < 0.25) {
			return List.of();
		}
		Direction main = Math.abs(to.x) >= Math.abs(to.z) ? (to.x > 0 ? Direction.EAST : Direction.WEST) : (to.z > 0 ? Direction.SOUTH : Direction.NORTH);
		Direction other = Math.abs(to.x) >= Math.abs(to.z) ? (to.z > 0 ? Direction.SOUTH : Direction.NORTH) : (to.x > 0 ? Direction.EAST : Direction.WEST);
		List<BlockPos> found = ripAlong(player, level, main, toward.y);
		if (found.isEmpty() && Math.min(Math.abs(to.x), Math.abs(to.z)) > 0.5) {
			found = ripAlong(player, level, other, toward.y);
		}
		return found;
	}

	private static List<BlockPos> ripAlong(ServerPlayer player, ServerLevel level, Direction dir, double targetY) {
		Map<Long, Integer> cache = new HashMap<>();
		BlockPos prev = BlockPos.containing(player.getX(), player.getY() + 0.01, player.getZ());
		Vec3 eye = player.getEyePosition();
		for (int i = 1; i <= RIP_LOOKAHEAD; i++) {
			BlockPos cell = prev.relative(dir);
			double dy = targetY - cell.getY();
			List<BlockPos> slot;
			BlockPos next;
			if (dy >= 1.5 && !passable(level, cache, cell)) {
				// up a step: keep the block under it as the step, clear above it and over your own head
				slot = List.of(cell.above(), cell.above(2), prev.above(2));
				next = cell.above();
			} else if (dy <= -1.5 && !passable(level, cache, cell.below())) {
				// down a step
				slot = List.of(cell, cell.above(), cell.below());
				next = cell.below();
			} else {
				slot = List.of(cell, cell.above());
				next = cell;
			}
			List<BlockPos> out = new ArrayList<>();
			for (BlockPos pos : slot) {
				if (passable(level, cache, pos)) {
					continue;
				}
				if (!breakable(player, level, pos) || Vec3.atCenterOf(pos).distanceTo(eye) > RIP_REACH) {
					// a wall it may not (or cannot) break: no way through this way
					return List.of();
				}
				out.add(pos);
			}
			if (!out.isEmpty()) {
				return out;
			}
			prev = next;
		}
		return List.of();
	}

	/** It tears this out only if it's solid, not too hard, holds no block entity or fluid, and you'd be allowed to break it. */
	private static boolean breakable(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.getCollisionShape(level, pos).isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F || hardness > RIP_MAX_HARDNESS) {
			return false;
		}
		return player.mayInteract(level, pos) && !player.blockActionRestricted(level, pos, player.gameMode());
	}

	private static void tickRip(ServerPlayer player, Route route, long now) {
		ServerLevel level = player.level();
		if (now >= route.ripFxAt) {
			route.ripFxAt = now + RIP_FX_EVERY;
			WebSymbiote.dread(player, RIP_FX_TICKS, true);
		}
		player.setAttached(WebSymbiote.TENDRILS_OUT, now + 12);
		if (route.clumpId >= 0) {
			tickFling(player, route, now);
			return;
		}
		// what's gone (or no longer allowed) since it started drops out of the clump
		List<BlockPos> slot = new ArrayList<>();
		for (BlockPos pos : route.ripSlot) {
			if (breakable(player, level, pos) && Vec3.atCenterOf(pos).distanceTo(player.getEyePosition()) <= RIP_REACH + 1.0) {
				slot.add(pos);
			} else {
				level.destroyBlockProgress(crackId(player, route.ripSlot.indexOf(pos)), pos, -1);
			}
		}
		route.ripSlot = slot;
		if (slot.isEmpty()) {
			// the slot's open: look for the way again
			finishRip(player, route);
			return;
		}
		long t = now - route.ripStart;
		int crack = Mth.clamp((int)(t * 10 / Math.max(1, route.ripLashTicks)), 0, 9);
		for (int i = 0; i < slot.size(); i++) {
			level.destroyBlockProgress(crackId(player, i), slot.get(i), crack);
		}
		if (t % 3 == 0) {
			// the tendrils gouging at it, working it loose
			BlockPos at = slot.get((int)(t / 3 % slot.size()));
			BlockState state = level.getBlockState(at);
			level.playSound(null, at, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.6F);
			Debris.puff(level, at, state, RIP_DEBRIS);
			WebSymbiote.trail(level, player.getEyePosition().add(0.0, -0.3, 0.0), Vec3.atCenterOf(at));
		}
		if (t < route.ripLashTicks) {
			return;
		}
		tear(player, route, now);
	}

	/** Worked loose: the blocks come out as one clump (the same as Living Armor's tendrils tear one), and a tendril takes it. */
	private static void tear(ServerPlayer player, Route route, long now) {
		ServerLevel level = player.level();
		clearCracks(player, route);
		List<BlockPos> taken = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		for (BlockPos pos : route.ripSlot) {
			BlockState state = level.getBlockState(pos);
			if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos))) {
				// someone's protecting it: stop tearing
				route.ripRetryAt = now + RIP_RETRY_TICKS;
				finishRip(player, route);
				return;
			}
			taken.add(pos);
			states.add(state);
		}
		route.ripping = null;
		if (player.getAttached(WebSymbiote.RIPPING) != null) {
			player.removeAttached(WebSymbiote.RIPPING);
		}
		BlockCluster chunk = BlockCluster.of(level, taken, route.ripDir.scale(-RIP_CLUMP_OUT), player);
		if (chunk == null) {
			finishRip(player, route);
			return;
		}
		for (int i = 0; i < taken.size(); i++) {
			PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, taken.get(i), states.get(i), null);
		}
		route.ripped += taken.size();
		Vec3 at = chunk.getBoundingBox().getCenter();
		level.sendParticles(WebSymbiote.dust(1.2F), at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9F, 0.5F);
		int hand = WebTendrils.freeHand(player);
		if (hand >= 0) {
			WebTendrils.grab(player, hand, chunk, -1L);
		}
		if (hand < 0 || !holds(player, hand, chunk)) {
			// both tendrils full: it's shoved aside where it is
			chunk.setDeltaMovement(flingVelocity(player, chunk, route.ripDir));
			chunk.syncVelocity = true;
			chunk.launch(player);
			finishRip(player, route);
			return;
		}
		route.clumpId = chunk.getId();
		route.clumpHand = hand;
		route.tornAt = now;
		route.heldAt = -1L;
	}

	/** The tendril hauls the clump up out of the hole, holds it a moment, then flings it aside. */
	private static void tickFling(ServerPlayer player, Route route, long now) {
		if (!(player.level().getEntity(route.clumpId) instanceof BlockCluster chunk) || !chunk.isAlive() || !holds(player, route.clumpHand, chunk)) {
			route.clumpId = -1;
			finishRip(player, route);
			return;
		}
		if (route.heldAt < 0L && WebTendrils.reached(player, route.clumpHand)) {
			route.heldAt = now;
		}
		boolean ready = route.heldAt >= 0L && now - route.heldAt >= RIP_HOLD_TICKS;
		if (!ready && now - route.tornAt < RIP_HOLD_TIMEOUT) {
			return;
		}
		WebTendrils.fling(player, route.clumpHand, flingVelocity(player, chunk, route.ripDir));
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.8F, 0.5F);
		route.clumpId = -1;
		finishRip(player, route);
	}

	/**
	 * Aside: square to the way through ({@code dir}), to whichever side (level, or up and over) has the most open room
	 * within {@code RIP_FLING_LOOK} blocks. Never along the tunnel, never back at you.
	 */
	private static Vec3 flingVelocity(ServerPlayer player, BlockCluster chunk, Vec3 dir) {
		ServerLevel level = player.level();
		Vec3 side = dir.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(-dir.z, 0.0, dir.x).normalize();
		Vec3 from = chunk.getBoundingBox().getCenter();
		Vec3[] ways = {side, side.reverse(), side.add(0.0, 1.0, 0.0).normalize(), side.reverse().add(0.0, 1.0, 0.0).normalize()};
		Vec3 best = player.getRandom().nextBoolean() ? ways[0] : ways[1];
		double bestRoom = -1.0;
		for (Vec3 way : ways) {
			double room = 0.0;
			while (room < RIP_FLING_LOOK) {
				BlockPos at = BlockPos.containing(from.add(way.scale(room + 0.5)));
				if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
					break;
				}
				room += 0.5;
			}
			if (room > bestRoom + 0.25) {
				bestRoom = room;
				best = way;
			}
		}
		return best.scale(RIP_FLING_SPEED).add(0.0, RIP_FLING_LIFT, 0.0);
	}

	/** That tendril of yours holds {@code chunk}. */
	private static boolean holds(ServerPlayer player, int hand, BlockCluster chunk) {
		return player.getAttachedOrElse(WebTendrils.GRIPS, WebTendrils.Grips.NONE).get(hand) == chunk.getId();
	}

	private static void clearCracks(ServerPlayer player, Route route) {
		for (int i = 0; i < route.ripSlot.size(); i++) {
			player.level().destroyBlockProgress(crackId(player, i), route.ripSlot.get(i), -1);
		}
	}

	private static void finishRip(ServerPlayer player, Route route) {
		clearCracks(player, route);
		route.ripping = null;
		route.ripSlot = List.of();
		route.pathAt = Long.MIN_VALUE;
		route.progressAt = 0L;
		route.mode = -1;
		if (player.getAttached(WebSymbiote.RIPPING) != null) {
			player.removeAttached(WebSymbiote.RIPPING);
		}
	}

	/** One crack overlay per block of the slot (the level keeps one per id). */
	private static int crackId(ServerPlayer player, int index) {
		return player.getId() * 41 + 2000 + index;
	}
}
