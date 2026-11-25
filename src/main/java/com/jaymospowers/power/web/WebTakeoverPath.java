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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Where a takeover drives you (rampage, hunger, curiosity, the feeding hunt, a run for its life). The server finds a
 * real way on foot (a bounded A* over block positions, sized for a player: two blocks tall, steps of one, charged leaps
 * up taller ledges, drops of a few) and sends the waypoints ({@link WebNet.TakeoverPath}); the client walks them.
 * Running, it floods out for the safest point it can reach. Chasing prey, the search plans its way through blocks as well
 * as round them (Andy: "if it sees something underground or in a building or otherwise behind blocks, it can dig STRAIGHT
 * there, using its tendril rip, it is extremely strong and should act like it"), and the tendrils tear the blocks in the
 * way out as a clump and fling it aside, a slot at a time. Stuck, it climbs a ladder: look again with digging on, rip
 * straight at the prey, zip at it, haul it down, and in the end give that prey up.
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
	/** Where you are on the stored path: looked for this many nodes behind where it last found you, and this many on. */
	static final int HOST_BACK = 2, HOST_AHEAD = 16;
	/** Straight at it instead: in this range (blocks), this level (blocks of height), and in clear sight, feet and eyes. */
	static final double DIRECT_RANGE = 7.0;
	static final double DIRECT_MAX_DY = 1.2;
	/** ...and ground under the whole line: checked every this many blocks, within this drop. */
	static final double DIRECT_GROUND_STEP = 0.5;
	static final int DIRECT_MAX_DROP = 2;
	/**
	 * Planned digging (chasing prey only): the search may go through blocks, within {@code DIG_PLAN_RANGE} blocks of the
	 * prey (further off it walks, and digs only once it's stuck). A block costs {@code COST_DIG_BLOCK} plus
	 * {@code COST_DIG_PER_HARDNESS} a point of hardness, in walk-time, so it walks round only when that's really quicker.
	 * The search leans toward the straight line by {@code HEURISTIC_WEIGHT}.
	 */
	static final double DIG_PLAN_RANGE = 32.0;
	static final double COST_DIG_BLOCK = 0.5, COST_DIG_PER_HARDNESS = 0.15;
	static final double HEURISTIC_WEIGHT = 1.5;
	/** The blocks it has been refused (a claim, a lock) are remembered, at most this many, and routed round. */
	static final int REFUSED_MAX = 256;
	/** Forced re-paths (stuck, refused) are at least this far apart (ticks). */
	static final int SEARCH_MIN_GAP = 5;
	/**
	 * Taller than its leap, it climbs: the tendrils take it up a wall of up to {@code CLIMB_MAX_RISE} blocks (the client
	 * leaps, then climbs on contact). It costs {@code COST_CLIMB_PER_BLOCK} a block on top of a leap.
	 */
	static final int CLIMB_MAX_RISE = 16;
	static final double COST_CLIMB_PER_BLOCK = 0.4;
	/** A gap of up to {@code GAP_MAX} open blocks, with nothing to land on in a drop, is jumped (the client bounds across); {@code COST_GAP} a block. */
	static final int GAP_MAX = 3;
	static final double COST_GAP = 1.5;
	// ===== running ===================================================================================================
	/** The flood for a safe point: nodes, the longest way it considers (cost), and how it scores a spot. */
	static final int FLEE_NODES = 1400;
	static final double FLEE_MAX_COST = 40.0;
	static final double FLEE_COST_WEIGHT = 0.35, FLEE_SKY_BONUS = 3.0;
	/** It counts as trapped unless the best spot puts at least this much more distance (blocks) between it and the threat. */
	static final double FLEE_MIN_GAIN = 4.0;
	// ===== stuck =====================================================================================================
	/**
	 * No progress for this long (ticks) and it's stuck (the clock waits while it coils for a leap or a weapon forms).
	 * Chasing, progress is getting {@code STUCK_GAIN} blocks closer to the prey, or a plan that much cheaper, or ripping,
	 * zipping, a counter: its own hops don't count (they hid a real stuck). Running, it's moving {@code STUCK_MOVE}.
	 * It never stands about (Andy).
	 */
	static final int STUCK_TICKS = 15;
	static final double STUCK_MOVE = 0.9;
	static final double STUCK_GAIN = 1.0;
	/**
	 * Each stuck event tries the next step of a ladder: look again with digging on, rip straight at the prey, zip at it,
	 * haul it down to you (a step with nothing to do passes to the next). This many in a row without a real gain and the
	 * prey is given up ({@link Route#hopeless}). Running, it only looks again and rips away.
	 */
	static final int STUCK_GIVE_UP = 6;
	private static final int[] CHASE_LADDER = {1, 2, 3, 4}, FLEE_LADDER = {2, 1};
	/** The haul reaches this far (blocks, in sight). */
	static final double HAUL_REACH = WebCounter.REACH;
	/** After being stuck on the straight line, it keeps to paths for this long (ticks). */
	static final int DIRECT_BAN_TICKS = 60;
	// ===== ripping ===================================================================================================
	/** Nothing harder than this is torn out (obsidian, 50, and harder stay); nothing unbreakable, nothing with a block entity. */
	static final float RIP_MAX_HARDNESS = 20.0F;
	/**
	 * It digs fast (0.8.6): the tendrils rip into the blocking blocks for only {@code RIP_LASH_TICKS} (+
	 * {@code RIP_TICKS_PER_HARDNESS} x the hardest one's hardness, + {@code RIP_TICKS_PER_BLOCK} for each block after the
	 * first; at most {@code RIP_LASH_MAX_TICKS}), then tear them out as one clump ({@link BlockCluster}, as Living Armor's
	 * tendrils do), the R and G sides taking turns, and go straight on to the next blocks in the way.
	 */
	static final int RIP_LASH_TICKS = 3;
	static final float RIP_TICKS_PER_HARDNESS = 1.0F;
	static final int RIP_TICKS_PER_BLOCK = 1;
	static final int RIP_LASH_MAX_TICKS = 10;
	/** The clump comes away this far toward you (blocks) out of the hole, before the tendril takes it. */
	static final double RIP_CLUMP_OUT = 0.15;
	/** Yanked out and held only this long (ticks), then let go of, thrown; thrown anyway this long after the tear (ticks). */
	static final int RIP_HOLD_TICKS = 2;
	static final int RIP_HOLD_TIMEOUT = 10;
	/**
	 * The throw: forward, on along the way it's digging, this fast and lifted this much (blocks/tick). A clump that
	 * meets the next wall just shatters there (it's too slow to smash anything).
	 */
	static final double RIP_FLING_SPEED = 1.0, RIP_FLING_LIFT = 0.35;
	/** Cells along the line it looks through for the blocking ones, and how far it can reach (blocks, from the eyes). */
	static final int RIP_LOOKAHEAD = 3;
	static final double RIP_REACH = 4.5;
	/** At most this many blocks torn out per takeover. */
	static final int RIP_MAX_BLOCKS = 640;
	/**
	 * Each pull tears out a small crater, not just the blocks in the way (0.9.8): the slot plus the breakable blocks
	 * round it (within {@code RIP_CRATER_RADIUS} of its middle, ragged, never below the slot's floor), up to
	 * {@code RIP_CRATER} blocks.
	 */
	static final int RIP_CRATER = 9;
	static final double RIP_CRATER_RADIUS = 1.8, RIP_CRATER_RAGGED = 0.6;
	/** When ripping can't help (nothing breakable), it waits this long before trying again (ticks). */
	static final int RIP_RETRY_TICKS = 10;
	/** The dread on your screen is refreshed this often while it rips (ticks), for this long. */
	static final int RIP_FX_EVERY = 40, RIP_FX_TICKS = 60;
	/**
	 * Tunnelling along its way: it rips the solid cells of the next nodes, from the dig face on, up to
	 * {@code DIG_TUNNEL_BLOCKS} a pull, once one is within {@code DIG_START_NODES} nodes of it (before it bumps the wall).
	 * Digging down, the clump is flung up and out ({@code RIP_SHAFT_LIFT}, {@code RIP_SHAFT_OUT}); a target this close
	 * (blocks, horizontally) and well below is dug at straight down.
	 */
	static final int DIG_START_NODES = 2;
	static final int DIG_TUNNEL_BLOCKS = BlockCluster.MAX_PIECES;
	static final double RIP_SHAFT_LIFT = 0.6, RIP_SHAFT_OUT = 0.4, RIP_SHAFT_RANGE = 1.5;
	/** Debris puff from a block while it's worked at (1 is a normal puff). */
	static final float RIP_DEBRIS = 0.3F;
	// ===== zipping ===================================================================================================
	/**
	 * Getting away (running for its life, a sightseeing walk, the climate's walk out) it fires a cord (Web Zip's pull
	 * and line, see {@link WebPower#takeoverZip}) at a block on the way ahead every {@code ZIP_EVERY} ticks, the first
	 * {@code ZIP_FIRST} into the takeover; with nothing worth it, it looks again {@code ZIP_RETRY} later. Any takeover
	 * zips up a rise of {@code ZIP_CLIMB_RISE}+ blocks on its way (out of a pit, up a cliff).
	 */
	static final int ZIP_EVERY = 50, ZIP_FIRST = 20, ZIP_RETRY = 10;
	static final int ZIP_CLIMB_RISE = 3;
	/** (0.13.23) Chasing something further off than this (blocks), it zips along the way at it too: no ambling after prey. */
	static final double ZIP_CHASE_RANGE = 12.0;
	/** A zip's spot: this far off at least and at most (blocks), never more than this below your feet. */
	static final double ZIP_MIN_RANGE = 5.0, ZIP_MAX_RANGE = 18.0, ZIP_MAX_FALL = 0.5;
	/** Scoring a spot: one per waypoint it skips, plus this per block up (running, or climbing: high ground first). */
	static final double ZIP_HIGH_WEIGHT = 1.5;
	/**
	 * Running, it also looks off the path for high ground to zip up onto: {@code ZIP_FAN_YAWS} ways round the line
	 * away from the threat (spread over {@code ZIP_FAN_SPREAD} degrees each side), at these pitches up (degrees), for a
	 * top it can stand on {@code ZIP_FAN_MIN_RISE}+ blocks up that puts it {@code ZIP_FAN_GAIN} further from the threat.
	 */
	static final int ZIP_FAN_YAWS = 5;
	static final float ZIP_FAN_SPREAD = 60.0F;
	static final float[] ZIP_FAN_PITCHES = {15.0F, 30.0F, 45.0F, 60.0F};
	static final int ZIP_FAN_MIN_RISE = 2;
	static final double ZIP_FAN_GAIN = 2.0;
	/** A zip counts as done once your feet are this close to its spot (blocks), whatever the line's time says. */
	static final double ZIP_ARRIVED = 1.5;
	/** Each zip yanks your view along the cord (degrees of shake) and pulses the dark over your screen (ticks). */
	static final float ZIP_SHAKE = 0.6F;
	static final int ZIP_DREAD = 25;
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
		/** Set by the caller each tick: chasing prey, so the search may plan digging (see {@link #DIG_PLAN_RANGE}). */
		boolean dig;
		/** Stuck too many times in a row: the caller picks other prey, or lets go ({@link #STUCK_GIVE_UP}). */
		boolean hopeless;
		/** Where the last search began, how far into {@link #nodes} the client was sent (its dig face), and when it last searched. */
		BlockPos pathFrom = BlockPos.ZERO;
		int sentEnd;
		long searchAt = Long.MIN_VALUE;
		/** The node you were last found nearest ({@link #hostIndex}): the look for you moves on with you. */
		int hostAt;
		/** Progress: the first tick seen, where it stood (running), the best distance to the prey, the last plan's cost, ticks without any. */
		boolean started;
		Vec3 progressPos = Vec3.ZERO;
		double bestDist = Double.MAX_VALUE;
		double lastCost = Double.MAX_VALUE;
		int idle;
		int stuck;
		/** Blocks a protection refused it (packed positions): the search treats them as walls. */
		final java.util.Set<Long> refused = new java.util.HashSet<>();
		/** The rip under way follows the path (the tunnel), and is dug straight down. */
		boolean pathRip;
		boolean ripShaft;
		long directBanUntil;
		long ripRetryAt;
		/** The blocks being worked loose (one clump), the one the tendrils aim at, and the way through (toward the slot). */
		List<BlockPos> ripSlot = List.of();
		@Nullable BlockPos ripping;
		Vec3 ripDir = Vec3.ZERO;
		long ripStart;
		long ripFxAt;
		int ripLashTicks;
		/** Torn clumps the tendrils hold before throwing them (several at once while it digs); the side that tears next. */
		final List<Clump> clumps = new ArrayList<>();
		int nextHand;
		/** Where it's digging toward, so it can go straight on to the next blocks in the way. */
		Vec3 ripToward = Vec3.ZERO;
		int ripped;
		boolean whispered;
		/** Zips: when it may fire the next (0: not yet set), and where the one reeling you in now ends (null: none). */
		long zipReadyAt;
		@Nullable Vec3 zipTo;
	}

	/** A torn clump in a tendril: its id, which tendril, when it was torn, when it was held (-1 not yet). */
	static final class Clump {
		final int id;
		final int hand;
		final long tornAt;
		long heldAt = -1L;

		Clump(int id, int hand, long tornAt) {
			this.id = id;
			this.hand = hand;
			this.tornAt = tornAt;
		}
	}

	/**
	 * A takeover tick: keeps the way to {@code target} fresh (or, {@code flee}ing, away from it), digs along it when it
	 * leads through blocks ({@link Route#dig}), notices when you're stuck and climbs the ladder ({@link #STUCK_GIVE_UP}).
	 * {@code reach} is how close counts as there (no need to move). {@code away}: it's getting you away somewhere
	 * (running, a sightseeing or climate walk), so it zips along the way.
	 */
	static void tick(ServerPlayer player, Route route, Entity target, boolean flee, boolean away, double reach, long now) {
		ServerLevel level = player.level();
		tickFlings(player, route, now);
		if (route.ripping != null) {
			route.idle = 0;
			tickRip(player, route, now);
			return;
		}
		if (route.zipTo != null) {
			if (WebPower.pulling(player) && player.position().distanceTo(route.zipTo) > ZIP_ARRIVED) {
				// reeled in on its cord: that's progress, and the way is looked for again once it's there
				route.idle = 0;
				return;
			}
			route.zipTo = null;
			route.pathAt = Long.MIN_VALUE;
		}
		if (player.getAttached(WebCounter.ACTIVE) != null) {
			// a counter is running (it hauls, strikes): that's progress, and the client holds still for it
			route.idle = 0;
			return;
		}
		if (!flee && route.dig && now >= route.ripRetryAt && startPathRip(player, route, now)) {
			return;
		}
		if (route.mode == WebNet.TakeoverPath.PATH && now >= route.zipReadyAt && zip(player, route, target, flee, away, false, now)) {
			return;
		}
		// progress: closer to the prey (running: moved), or close enough to fight it
		boolean there = !flee && player.distanceTo(target) - target.getBbWidth() * 0.5 <= reach + 0.5 && player.hasLineOfSight(target);
		Vec3 pos = player.position();
		boolean progressed;
		if (flee) {
			progressed = !route.started || pos.distanceToSqr(route.progressPos) > STUCK_MOVE * STUCK_MOVE;
			if (progressed) {
				route.progressPos = pos;
			}
		} else {
			double distance = pos.distanceTo(target.position());
			progressed = there || distance <= route.bestDist - STUCK_GAIN;
			if (progressed) {
				route.bestDist = Math.min(route.bestDist, distance);
			}
		}
		route.started = true;
		if (progressed) {
			route.idle = 0;
			route.stuck = 0;
		} else if (player.getAttached(WebPower.CHARGING) != null || player.getAttached(WebMorph.MORPH) != null) {
			// (coiling for a leap, a weapon forming: it's about to move, so the clock waits; a hop in place still runs it down)
		} else if (++route.idle > STUCK_TICKS && escalate(player, route, target, flee, now)) {
			return;
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
		if (route.pathAt == Long.MIN_VALUE && now - route.searchAt < SEARCH_MIN_GAP) {
			// (a forced look: not more than every few ticks)
			return;
		}
		route.pathAt = now;
		route.searchAt = now;
		route.pathTarget = target.position();
		BlockPos start = startNode(level, player);
		// chasing prey it plans through blocks (within range of it, or once it's been stuck), never when running
		boolean dig = !flee && route.dig && route.ripped < RIP_MAX_BLOCKS && (route.stuck > 0 || player.distanceTo(target) <= DIG_PLAN_RANGE);
		Search search = new Search(level, start, dig ? player : null, route);
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
		if (end != null && (end.reached || dist3(start, goal) - dist3(end.pos, goal) >= PARTIAL_MIN_GAIN)) {
			// a plan that got cheaper is progress (a detour, a tunnel under way)
			double cost = end.g + (end.reached ? 0.0 : end.h);
			if (route.lastCost - cost >= STUCK_GAIN) {
				route.idle = 0;
				route.stuck = 0;
			}
			route.lastCost = cost;
			route.pathFrom = start;
			route.nodes = end.path();
			route.hostAt = 0;
			transmit(player, route, 0);
			if (route.dig && now >= route.ripRetryAt) {
				// (a face right at the start: it tears into it now, rather than leave the client nowhere to go)
				startPathRip(player, route, now);
			}
		} else if (now < route.ripRetryAt || !startRip(player, route, target.position(), now)) {
			send(player, route, List.of(), WebNet.TakeoverPath.FALLBACK, BlockPos.ZERO);
		}
	}

	/**
	 * Stuck for {@link #STUCK_TICKS}: the next step of the ladder (see {@link #STUCK_GIVE_UP}). True if it acted and the tick
	 * is spent (a rip, a zip, a haul); false to go on and look for a way again.
	 */
	private static boolean escalate(ServerPlayer player, Route route, Entity target, boolean flee, long now) {
		route.idle = 0;
		route.stuck++;
		route.bestDist = player.position().distanceTo(target.position());
		route.progressPos = player.position();
		if (route.mode == WebNet.TakeoverPath.DIRECT) {
			route.directBanUntil = now + DIRECT_BAN_TICKS;
		}
		if (!flee && route.stuck >= STUCK_GIVE_UP) {
			route.hopeless = true;
			return false;
		}
		int[] ladder = flee ? FLEE_LADDER : CHASE_LADDER;
		int first = flee ? 0 : (route.stuck - 1) % ladder.length;
		for (int k = 0; k < ladder.length; k++) {
			switch (ladder[(first + k) % ladder.length]) {
				case 1 -> {
					// look again now (with digging on: stuck counts), the straight line banned
					route.pathAt = Long.MIN_VALUE;
					return false;
				}
				case 2 -> {
					// rip straight at it, outside the plan
					if (now >= route.ripRetryAt && startRip(player, route, flee ? awayPoint(player, target) : target.position(), now)) {
						return true;
					}
				}
				case 3 -> {
					// a cord toward it, whether or not one's due
					if (!flee && zip(player, route, target, false, false, true, now)) {
						return true;
					}
				}
				default -> {
					// the tendrils haul it down to you
					if (!flee && haul(player, target)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The prey in sight and within the haul's reach: a Constrict drags it down in front of you (see WebCounter#seize). */
	private static boolean haul(ServerPlayer player, Entity target) {
		return target instanceof LivingEntity prey && prey.isAlive() && player.distanceTo(prey) <= HAUL_REACH && player.hasLineOfSight(prey)
			&& WebCounter.seize(player, prey, WebCounter.CONSTRICT);
	}

	/** The takeover's over: any half-torn block's cracks fade, and a clump still held is flung aside. */
	static void stop(ServerPlayer player, @Nullable Route route) {
		if (route != null) {
			clearCracks(player, route);
			for (Clump clump : route.clumps) {
				if (player.level().getEntity(clump.id) instanceof BlockCluster chunk && holds(player, clump.hand, chunk)) {
					WebTendrils.fling(player, clump.hand, flingVelocity(player, route, chunk));
				}
			}
			route.clumps.clear();
		}
		if (player.getAttached(WebSymbiote.RIPPING) != null) {
			player.removeAttached(WebSymbiote.RIPPING);
		}
	}

	private static void send(ServerPlayer player, Route route, List<BlockPos> nodes, int mode, BlockPos focus) {
		route.mode = mode;
		route.nodes = nodes;
		route.hostAt = 0;
		route.sentEnd = Math.min(nodes.size(), PATH_SEND_MAX);
		List<BlockPos> sent = nodes.size() > PATH_SEND_MAX ? List.copyOf(nodes.subList(0, PATH_SEND_MAX)) : nodes;
		ServerPlayNetworking.send(player, new WebNet.TakeoverPath(sent, mode, focus));
	}

	/**
	 * Sends the stored path from node {@code from} on, cut at its dig face (the first node whose way is through blocks) when
	 * it tunnels, so the client never leaps or walks at a waypoint inside rock. The rest waits for the tendrils.
	 */
	private static void transmit(ServerPlayer player, Route route, int from) {
		int end = Math.min(route.nodes.size(), from + PATH_SEND_MAX);
		if (route.dig) {
			end = Math.min(end, digFace(player.level(), route, from));
		}
		end = Math.max(from, end);
		route.mode = WebNet.TakeoverPath.PATH;
		route.sentEnd = end;
		ServerPlayNetworking.send(player, new WebNet.TakeoverPath(List.copyOf(route.nodes.subList(from, end)), WebNet.TakeoverPath.PATH, BlockPos.ZERO));
	}

	/** The cells the body passes through to get from {@code prev} to {@code to}: the spot, over it, and the head's room on a step up or a sideways drop. */
	private static List<BlockPos> sweep(BlockPos prev, BlockPos to) {
		List<BlockPos> cells = new ArrayList<>(4);
		cells.add(to);
		cells.add(to.above());
		if (to.getY() > prev.getY()) {
			cells.add(prev.above(2));
		} else if (to.getY() < prev.getY() && (to.getX() != prev.getX() || to.getZ() != prev.getZ())) {
			cells.add(to.above(2));
		}
		return cells;
	}

	/**
	 * A cell the way goes through that's solid and not just a slab or carpet to stand on ({@code spot}: it's where the feet
	 * go). Fire, lava and unloaded cells are walls, not for digging.
	 */
	private static boolean needsDig(int kind, boolean spot) {
		return (kind & (PASSABLE | DANGER | UNLOADED)) == 0 && !(spot && (kind & LOW) != 0);
	}

	/** The first node from {@code from} on whose way is through solid blocks (the dig face), or the path's length. */
	private static int digFace(ServerLevel level, Route route, int from) {
		Map<Long, Integer> cache = new HashMap<>();
		List<BlockPos> nodes = route.nodes;
		for (int j = from; j < nodes.size() && j < from + PATH_SEND_MAX; j++) {
			BlockPos prev = j == 0 ? route.pathFrom : nodes.get(j - 1);
			List<BlockPos> cells = sweep(prev, nodes.get(j));
			for (int i = 0; i < cells.size(); i++) {
				if (needsDig(kind(level, cache, cells.get(i)), i == 0)) {
					return j;
				}
			}
		}
		return nodes.size();
	}

	/**
	 * Where you are on the stored path: the nearest node from a little behind where it last found you to a stretch on
	 * ({@link #HOST_BACK}, {@link #HOST_AHEAD}), so it keeps up with you however long the walk.
	 */
	private static int hostIndex(ServerPlayer player, Route route) {
		int size = route.nodes.size();
		int last = Math.min(route.hostAt, Math.max(0, size - 1));
		int from = last;
		double near = Double.MAX_VALUE;
		for (int i = Math.max(0, last - HOST_BACK); i < Math.min(size, last + HOST_AHEAD); i++) {
			double d = Vec3.atBottomCenterOf(route.nodes.get(i)).distanceToSqr(player.position());
			if (d < near) {
				near = d;
				from = i;
			}
		}
		route.hostAt = from;
		return from;
	}

	private static double dist3(BlockPos a, BlockPos b) {
		return Math.sqrt(a.distSqr(b));
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

	// ---- zipping along -----------------------------------------------------------------------------------------------

	/**
	 * Fires a cord at a spot on the way ahead (see {@link #ZIP_EVERY}): the waypoint furthest along (and, running or
	 * climbing, highest) that's in range, not below you, in clear sight and with a clear line for the whole body;
	 * running, high ground off the path too. True if it fired.
	 */
	private static boolean zip(ServerPlayer player, Route route, Entity target, boolean flee, boolean away, boolean force, long now) {
		if (!force && route.zipReadyAt == 0L) {
			route.zipReadyAt = now + ZIP_FIRST;
			return false;
		}
		if (!player.onGround() || player.isInWater() || player.isInLava() || player.isPassenger() || player.getAbilities().flying
			|| player.getAttached(WebPower.CHARGING) != null || WebPower.pulling(player) || route.nodes.isEmpty() && !force) {
			return false;
		}
		ServerLevel level = player.level();
		Map<Long, Integer> cache = new HashMap<>();
		List<BlockPos> nodes = route.nodes;
		// (the path starts where it last saw you: pick up from the nearest of the first few)
		int from = hostIndex(player, route);
		BlockHitResult best = null;
		double bestScore = 0.0;
		// (forced, when it's stuck: at the prey whatever the distance, or the spot by it)
		boolean chase = force || !flee && !away && player.distanceTo(target) > ZIP_CHASE_RANGE;
		if (force) {
			BlockPos goal = ground(level, target.blockPosition());
			best = zipHit(player, level, cache, goal);
			bestScore = best == null ? 0.0 : nodes.size() + 1.0;
		}
		// (only as far along as the client was sent)
		for (int j = from + 1; j < route.sentEnd; j++) {
			BlockPos node = nodes.get(j);
			double rise = node.getY() - player.getY();
			boolean climb = rise >= ZIP_CLIMB_RISE;
			if (!away && !climb && !chase || rise < -ZIP_MAX_FALL || (kind(level, cache, node) & WATER) != 0) {
				continue;
			}
			double score = (j - from) + (flee || climb ? Math.max(0.0, rise) * ZIP_HIGH_WEIGHT : 0.0);
			if (score <= bestScore) {
				continue;
			}
			BlockHitResult hit = zipHit(player, level, cache, node);
			if (hit != null) {
				best = hit;
				bestScore = score;
			}
		}
		if (flee) {
			// running: high ground off the path too, further from the threat
			Vec3 awayDir = player.position().subtract(target.position()).horizontal();
			awayDir = awayDir.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal().reverse() : awayDir.normalize();
			double gap = player.distanceTo(target);
			Vec3 eye = player.getEyePosition();
			for (int i = 0; i < ZIP_FAN_YAWS; i++) {
				float yaw = ZIP_FAN_YAWS == 1 ? 0.0F : -ZIP_FAN_SPREAD + 2.0F * ZIP_FAN_SPREAD * i / (ZIP_FAN_YAWS - 1);
				Vec3 flat = awayDir.yRot(yaw * Mth.DEG_TO_RAD);
				for (float pitch : ZIP_FAN_PITCHES) {
					double p = pitch * Mth.DEG_TO_RAD;
					Vec3 ray = flat.scale(Math.cos(p)).add(0.0, Math.sin(p), 0.0);
					BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(ray.scale(ZIP_MAX_RANGE)), ClipContext.Block.COLLIDER,
						ClipContext.Fluid.ANY, player));
					if (hit.getType() != HitResult.Type.BLOCK) {
						continue;
					}
					// a top it hit, or the top of the face it hit (a ledge a block or two above the hit point)
					BlockPos feet = null;
					if (hit.getDirection() == Direction.UP) {
						feet = hit.getBlockPos().above();
					} else if (hit.getDirection().getAxis().isHorizontal()) {
						for (int k = 1; k <= 2 && feet == null; k++) {
							if (standable(level, cache, hit.getBlockPos().above(k))) {
								feet = hit.getBlockPos().above(k);
							}
						}
					}
					if (feet == null || (kind(level, cache, feet) & WATER) != 0) {
						continue;
					}
					double rise = feet.getY() - player.getY();
					Vec3 spot = Vec3.atBottomCenterOf(feet);
					if (rise < ZIP_FAN_MIN_RISE || spot.distanceTo(target.position()) < gap + ZIP_FAN_GAIN) {
						continue;
					}
					// (scored as a waypoint as far off as it is)
					double score = spot.distanceTo(player.position()) + rise * ZIP_HIGH_WEIGHT;
					if (score <= bestScore) {
						continue;
					}
					BlockHitResult top = zipHit(player, level, cache, feet);
					if (top != null) {
						best = top;
						bestScore = score;
					}
				}
			}
		}
		if (best == null) {
			route.zipReadyAt = now + ZIP_RETRY;
			return false;
		}
		Vec3 spot = best.getLocation();
		WebPower.takeoverZip(player, best);
		route.zipTo = spot;
		route.zipReadyAt = now + ZIP_EVERY;
		route.progressPos = player.position();
		route.idle = 0;
		// your view's yanked along the cord, and the dark pulses under it
		Vec3 pull = spot.subtract(player.position());
		Vec3 behind = player.position().subtract(pull.lengthSqr() < 1.0E-4 ? Vec3.ZERO : pull.normalize());
		com.jaymospowers.core.Shakes.send(player, ZIP_SHAKE, behind);
		WebSymbiote.dread(player, ZIP_DREAD, false);
		return true;
	}

	/**
	 * Where a zip would stick to get you onto {@code feet} (a spot it could stand): the floor's top, in clear sight from
	 * the eyes (the ray meets that very top), or, for a ledge above your eyes, its face toward you just under the edge
	 * (Web Zip hangs you there and perches you up onto it). In range, nothing that burns there, and a clear line for the
	 * feet and the head the whole way (no water or lava on it either). Null if not.
	 */
	private static @Nullable BlockHitResult zipHit(ServerPlayer player, ServerLevel level, Map<Long, Integer> cache, BlockPos feet) {
		double d = Vec3.atBottomCenterOf(feet).distanceTo(player.position());
		if (d < ZIP_MIN_RANGE || d > ZIP_MAX_RANGE || !standable(level, cache, feet) || (kind(level, cache, feet.below()) & DANGER) != 0) {
			return null;
		}
		// the floor's top: the slab or carpet in the feet block, else the block under it
		BlockPos floor = (kind(level, cache, feet) & PASSABLE) == 0 ? feet : feet.below();
		VoxelShape shape = level.getBlockState(floor).getCollisionShape(level, floor);
		if (shape.isEmpty()) {
			return null;
		}
		Vec3 spot = new Vec3(feet.getX() + 0.5, floor.getY() + shape.max(Direction.Axis.Y), feet.getZ() + 0.5);
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = zipRay(player, level, eye, spot);
		Vec3 end;
		if (hit != null && hit.getDirection() == Direction.UP && hit.getLocation().distanceToSqr(spot) <= 0.6 * 0.6) {
			end = hit.getLocation().add(0.0, 0.05, 0.0);
		} else {
			// a ledge over your head: its near face, just under the edge
			Vec3 back = eye.subtract(spot).horizontal();
			if (back.lengthSqr() < 1.0E-4) {
				return null;
			}
			Vec3 edge = spot.add(back.normalize().scale(0.45)).add(0.0, -0.3, 0.0);
			hit = zipRay(player, level, eye, edge);
			if (hit == null || !hit.getBlockPos().equals(floor) || !hit.getDirection().getAxis().isHorizontal()
				|| hit.getLocation().distanceToSqr(edge) > 0.8 * 0.8) {
				return null;
			}
			end = hit.getLocation().add(hit.getDirection().getUnitVec3().scale(player.getBbWidth() * 0.5 + 0.2)).add(0.0, -1.3, 0.0);
		}
		// the body's way there (where Web Zip's pull takes the feet): feet and head, clear of blocks and fluids
		for (double up : new double[] {0.3, player.getBbHeight() - 0.2}) {
			Vec3 a = player.position().add(0.0, up, 0.0), b = end.add(0.0, up, 0.0);
			if (level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player)).getType() != HitResult.Type.MISS) {
				return null;
			}
		}
		return hit;
	}

	/** The block a ray from {@code eye} at {@code at} (and a little past) meets first, or null. */
	private static @Nullable BlockHitResult zipRay(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 at) {
		Vec3 ray = at.subtract(eye);
		if (ray.lengthSqr() < 1.0E-4) {
			return null;
		}
		BlockHitResult hit = level.clip(new ClipContext(eye, at.add(ray.normalize().scale(0.3)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	// ---- the block grid ----------------------------------------------------------------------------------------------

	private static int kind(ServerLevel level, Map<Long, Integer> cache, BlockPos pos) {
		long key = pos.asLong();
		Integer known = cache.get(key);
		if (known != null) {
			return known;
		}
		int kind;
		if (!com.jaymospowers.core.Loaded.made(level, pos)) {
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

	/**
	 * One bounded search over the grid around {@code start}. With a {@code digger} (and the {@code route} that knows what's
	 * been refused) it plans through blocks as well as round them.
	 */
	private static final class Search {
		final ServerLevel level;
		final BlockPos start;
		final @Nullable ServerPlayer digger;
		final @Nullable Route route;
		final double weight;
		final Map<Long, Integer> cache = new HashMap<>();
		final Map<Long, Node> nodes = new HashMap<>();
		/** What each block costs to tear out (-1: it may not be). */
		final Map<Long, Double> digCosts = new HashMap<>();
		final PriorityQueue<Node> open = new PriorityQueue<>();

		Search(ServerLevel level, BlockPos start, @Nullable ServerPlayer digger, @Nullable Route route) {
			this.level = level;
			this.start = start;
			this.digger = digger;
			this.route = route;
			// (digging makes the search three-dimensional: it leans toward the straight line to reach far enough)
			this.weight = digger == null ? 1.0 : HEURISTIC_WEIGHT;
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

		private double heuristic(BlockPos a, BlockPos goal) {
			return Math.sqrt(a.distSqr(goal)) * weight;
		}

		private boolean outside(BlockPos pos) {
			return Math.abs(pos.getX() - start.getX()) > SEARCH_RADIUS || Math.abs(pos.getZ() - start.getZ()) > SEARCH_RADIUS;
		}

		private void expand(Node n, @Nullable BlockPos goal) {
			BlockPos p = n.pos;
			for (int[] d : DIRS) {
				boolean diagonal = d[0] != 0 && d[1] != 0;
				BlockPos side = p.offset(d[0], 0, d[1]);
				if (outside(side)) {
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
					// open, but nothing to stand on: a drop it can take, or a gap it can jump
					boolean landed = false, clear = true;
					for (int k = 1; k <= MAX_DROP; k++) {
						BlockPos down = side.below(k);
						if (standable(level, cache, down)) {
							visit(n, down, 1.0 + k * COST_DROP, goal);
							landed = true;
							break;
						}
						if (!passable(level, cache, down)) {
							clear = false;
							break;
						}
					}
					if (!landed && clear) {
						jump(n, d[0], d[1], goal);
					}
				}
			}
			if (digger != null && goal != null) {
				fall(n, goal);
				digMoves(n, goal);
			}
		}

		/**
		 * Too tall to step: the lowest ledge on {@code side} it can leap up to ({@code LEAP_MIN_RISE} to
		 * {@code LEAP_MAX_RISE} blocks), with headroom all the way up over where it coils, and over the ledge's edge.
		 * Taller still, it climbs ({@link #climb}).
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
			climb(n, side, goal);
		}

		/**
		 * Taller than a leap, up to {@code CLIMB_MAX_RISE}: the column over you clear, a face of solid blocks on {@code side}
		 * all the way up, and a ledge to stand on at the top. The client leaps, then the tendrils climb the wall.
		 */
		private void climb(Node n, BlockPos side, @Nullable BlockPos goal) {
			BlockPos p = n.pos;
			for (int i = 0; i <= LEAP_MAX_RISE; i++) {
				if (passable(level, cache, side.above(i))) {
					return;
				}
			}
			for (int h = LEAP_MAX_RISE + 1; h <= CLIMB_MAX_RISE; h++) {
				if (!passable(level, cache, p.above(h + 2))) {
					return;
				}
				BlockPos top = side.above(h);
				if (standable(level, cache, top) && passable(level, cache, top.above(2))) {
					visit(n, top, 1.0 + COST_LEAP + COST_CLIMB_PER_BLOCK * h, goal);
					return;
				}
				if (passable(level, cache, top)) {
					// the wall ends with nothing to stand on
					return;
				}
			}
		}

		/**
		 * A gap: {@code side} is open with nothing to land on, so it jumps across (cardinal only): 1 to {@code GAP_MAX} open
		 * cells, headroom all the way, and a landing level with the start or up to {@code MAX_DROP} below.
		 */
		private void jump(Node n, int dx, int dz, @Nullable BlockPos goal) {
			BlockPos p = n.pos;
			if (!passable(level, cache, p.above(2)) || !passable(level, cache, p.offset(dx, 2, dz))) {
				return;
			}
			for (int i = 2; i <= GAP_MAX + 1; i++) {
				BlockPos cell = p.offset(dx * i, 0, dz * i);
				if (outside(cell) || !passable(level, cache, cell) || !passable(level, cache, cell.above())) {
					return;
				}
				for (int k = 0; k <= MAX_DROP; k++) {
					BlockPos land = cell.below(k);
					if (standable(level, cache, land)) {
						if (passable(level, cache, land.above(2))) {
							visit(n, land, 1.0 + COST_GAP * (i - 1) + COST_DROP * k, goal);
						}
						return;
					}
					if (!passable(level, cache, land)) {
						return;
					}
				}
				// (another open cell: on, with room over it)
				if (!passable(level, cache, cell.above(2))) {
					return;
				}
			}
		}

		/** Dug into with nothing under it: the way on is the drop through the hole it made. */
		private void fall(Node n, BlockPos goal) {
			BlockPos p = n.pos;
			if ((kind(level, cache, p.below()) & (FLOOR | WATER)) != 0 || !passable(level, cache, p.below())) {
				return;
			}
			for (int k = 1; k <= MAX_DROP; k++) {
				BlockPos down = p.below(k);
				if (standable(level, cache, down)) {
					visit(n, down, 1.0 + k * COST_DROP, goal);
					return;
				}
				if (!passable(level, cache, down)) {
					return;
				}
			}
		}

		/** Through the blocks: level, a step up (a stair cut) or a step down on each side, and straight down. No diagonals, no straight up. */
		private void digMoves(Node n, BlockPos goal) {
			BlockPos p = n.pos;
			for (int i = 0; i < 4; i++) {
				BlockPos side = p.offset(DIRS[i][0], 0, DIRS[i][1]);
				digTo(n, side, goal);
				digTo(n, side.above(), goal);
				digTo(n, side.below(), goal);
			}
			digTo(n, p.below(), goal);
		}

		/**
		 * One dig move to {@code to}: every cell the body passes through (see {@link #sweep}) open or tearable, at least one
		 * of them solid, and a floor under it (or a drop it can take through the hole). It costs a step plus each block.
		 */
		private void digTo(Node n, BlockPos to, BlockPos goal) {
			BlockPos p = n.pos;
			if (outside(to) || Math.abs(to.getY() - start.getY()) > SEARCH_RADIUS) {
				return;
			}
			double cost = 1.0;
			boolean dug = false;
			for (BlockPos cell : sweep(p, to)) {
				if (cell.equals(p) || cell.equals(p.above())) {
					// (where it already is: paid for when it got there)
					continue;
				}
				int k = kind(level, cache, cell);
				if ((k & (DANGER | UNLOADED)) != 0) {
					return;
				}
				if ((k & PASSABLE) != 0 || cell.equals(to) && (k & LOW) != 0) {
					continue;
				}
				double c = digCost(cell);
				if (c < 0.0) {
					return;
				}
				cost += c;
				dug = true;
			}
			if (!dug) {
				// (nothing to tear: the walking moves have it)
				return;
			}
			int floor = kind(level, cache, to.below());
			if ((floor & (DANGER | UNLOADED)) != 0 || (floor & (FLOOR | WATER)) == 0 && !drops(to)) {
				return;
			}
			visit(n, to, cost, goal);
		}

		/** Nothing under {@code to}, but a place to land within {@code MAX_DROP}. */
		private boolean drops(BlockPos to) {
			for (int k = 1; k <= MAX_DROP; k++) {
				BlockPos down = to.below(k);
				if (standable(level, cache, down)) {
					return true;
				}
				if (!passable(level, cache, down)) {
					return false;
				}
			}
			return false;
		}

		/** What tearing out {@code cell} costs, or -1 if it may not be torn out. */
		private double digCost(BlockPos cell) {
			long key = cell.asLong();
			Double known = digCosts.get(key);
			if (known != null) {
				return known;
			}
			float hardness = digger == null || route == null ? -1.0F : digHardness(digger, level, cache, route, cell);
			double cost = hardness < 0.0F ? -1.0 : COST_DIG_BLOCK + COST_DIG_PER_HARDNESS * hardness;
			digCosts.put(key, cost);
			return cost;
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
		List<BlockPos> blocks = ripTargets(player, route, toward);
		if (blocks.isEmpty()) {
			route.ripRetryAt = now + RIP_RETRY_TICKS;
			return false;
		}
		route.pathRip = false;
		route.ripShaft = shaft(blocks);
		blocks = crater(player, route, blocks);
		return beginRip(player, route, blocks, toward, now);
	}

	/** Every block in one column, more than one: digging straight down. */
	private static boolean shaft(List<BlockPos> blocks) {
		BlockPos first = blocks.get(0);
		for (BlockPos pos : blocks) {
			if (pos.getX() != first.getX() || pos.getZ() != first.getZ()) {
				return false;
			}
		}
		return blocks.size() > 1;
	}

	/**
	 * Digging along the path (the tunnel the search planned): the solid cells of the next nodes, from the dig face on. False
	 * if it isn't at the face yet, or there's nothing there it may tear out.
	 */
	private static boolean startPathRip(ServerPlayer player, Route route, long now) {
		if (route.nodes.isEmpty() || route.ripped >= RIP_MAX_BLOCKS) {
			return false;
		}
		int from = hostIndex(player, route);
		ServerLevel level = player.level();
		Map<Long, Integer> cache = new HashMap<>();
		// (the face must be within the next few nodes: it tears before it bumps the wall)
		int face = -1;
		for (int j = from; j < Math.min(route.nodes.size(), from + DIG_START_NODES) && face < 0; j++) {
			for (BlockPos cell : sweep(j == 0 ? route.pathFrom : route.nodes.get(j - 1), route.nodes.get(j))) {
				if (needsDig(kind(level, cache, cell), cell.equals(route.nodes.get(j)))) {
					face = j;
					break;
				}
			}
		}
		if (face < 0) {
			return false;
		}
		List<BlockPos> slot = pathSlot(player, route, face, cache);
		if (slot.isEmpty()) {
			return false;
		}
		route.pathRip = true;
		route.ripShaft = shaft(slot);
		Vec3 toward = Vec3.atCenterOf(slot.get(slot.size() - 1));
		return beginRip(player, route, slot, toward, now);
	}

	/**
	 * The solid cells of the path from node {@code face} on (up to {@code DIG_TUNNEL_BLOCKS}, all within reach), that it may
	 * tear out; empty if the first of them may not be (or is out of reach). Sand or gravel over the top of it joins, so
	 * it doesn't pour in behind.
	 */
	private static List<BlockPos> pathSlot(ServerPlayer player, Route route, int face, Map<Long, Integer> cache) {
		ServerLevel level = player.level();
		java.util.Set<BlockPos> cells = new java.util.LinkedHashSet<>();
		for (int j = face; j < route.nodes.size() && cells.size() < DIG_TUNNEL_BLOCKS; j++) {
			BlockPos node = route.nodes.get(j);
			List<BlockPos> solid = new ArrayList<>();
			for (BlockPos cell : sweep(j == 0 ? route.pathFrom : route.nodes.get(j - 1), node)) {
				if (needsDig(kind(level, cache, cell), cell.equals(node))) {
					solid.add(cell);
				}
			}
			if (Vec3.atCenterOf(node).distanceTo(player.getEyePosition()) > RIP_REACH + 1.0) {
				break;
			}
			boolean ok = true;
			for (BlockPos cell : solid) {
				if (digHardness(player, level, cache, route, cell) < 0.0F || Vec3.atCenterOf(cell).distanceTo(player.getEyePosition()) > RIP_REACH) {
					ok = false;
				}
			}
			if (!ok || cells.size() + solid.size() > DIG_TUNNEL_BLOCKS && !cells.isEmpty()) {
				break;
			}
			cells.addAll(solid);
		}
		if (cells.isEmpty()) {
			return List.of();
		}
		List<BlockPos> slot = new ArrayList<>(cells);
		// sand and gravel overhead would pour in behind (WebSymbioteDig has the same care)
		for (BlockPos cell : cells) {
			BlockPos above = cell.above();
			for (int i = 0; i < WebSymbioteDig.CLEAR_ABOVE && slot.size() < BlockCluster.MAX_PIECES; i++, above = above.above()) {
				if (!(level.getBlockState(above).getBlock() instanceof FallingBlock) || slot.contains(above)
					|| digHardness(player, level, cache, route, above) < 0.0F) {
					break;
				}
				slot.add(above);
			}
		}
		return slot;
	}

	/** The first rip of a slot: the tendrils lash it, then tear it out ({@link #tickRip}). */
	private static boolean beginRip(ServerPlayer player, Route route, List<BlockPos> blocks, Vec3 toward, long now) {
		if (blocks.size() > RIP_MAX_BLOCKS - route.ripped) {
			blocks = blocks.subList(0, RIP_MAX_BLOCKS - route.ripped);
		}
		ServerLevel level = player.level();
		route.ripToward = toward;
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
		// (the client holds still; the stored way on is kept for when the tunnel's open)
		route.mode = WebNet.TakeoverPath.RIP;
		ServerPlayNetworking.send(player, new WebNet.TakeoverPath(List.of(), WebNet.TakeoverPath.RIP, focus));
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

	/** The blocks in the way and a ragged crater round them (see {@link #RIP_CRATER}); the blocks in the way first. */
	private static List<BlockPos> crater(ServerPlayer player, Route route, List<BlockPos> slot) {
		ServerLevel level = player.level();
		Map<Long, Integer> cache = new HashMap<>();
		Vec3 middle = Vec3.ZERO;
		int floor = Integer.MAX_VALUE;
		for (BlockPos pos : slot) {
			middle = middle.add(Vec3.atCenterOf(pos));
			floor = Math.min(floor, pos.getY());
		}
		middle = middle.scale(1.0 / slot.size());
		BlockPos feet = player.blockPosition().below();
		BlockPos center = BlockPos.containing(middle);
		Map<BlockPos, Double> rank = new HashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
			BlockPos at = pos.immutable();
			double d = Vec3.atCenterOf(at).distanceTo(middle);
			if (slot.contains(at) || at.getY() < floor || at.equals(feet) || d > RIP_CRATER_RADIUS
				|| Vec3.atCenterOf(at).distanceTo(player.getEyePosition()) > RIP_REACH + 1.0 || digHardness(player, level, cache, route, at) < 0.0F) {
				continue;
			}
			rank.put(at, d + player.getRandom().nextDouble() * RIP_CRATER_RAGGED);
		}
		List<BlockPos> around = new ArrayList<>(rank.keySet());
		around.sort(java.util.Comparator.comparingDouble(rank::get));
		List<BlockPos> out = new ArrayList<>(slot);
		for (BlockPos pos : around) {
			if (out.size() >= Math.max(RIP_CRATER, slot.size())) {
				break;
			}
			out.add(pos);
		}
		return out;
	}

	/**
	 * The blocking blocks in the first cell along the line to {@code toward} (main axis first, then the other). Right over
	 * it and below, the two blocks under your feet (digging straight down); right under it, along the way you face (the
	 * stair is cut up).
	 */
	private static List<BlockPos> ripTargets(ServerPlayer player, Route route, Vec3 toward) {
		ServerLevel level = player.level();
		Vec3 to = toward.subtract(player.position());
		if (to.y <= -1.5 && to.horizontalDistanceSqr() < RIP_SHAFT_RANGE * RIP_SHAFT_RANGE) {
			Map<Long, Integer> cache = new HashMap<>();
			BlockPos feet = BlockPos.containing(player.getX(), player.getY() + 0.01, player.getZ());
			List<BlockPos> shaft = new ArrayList<>();
			for (int i = 1; i <= 2; i++) {
				BlockPos cell = feet.below(i);
				if ((kind(level, cache, cell) & (DANGER | WATER | UNLOADED)) != 0) {
					return List.of();
				}
				if (passable(level, cache, cell)) {
					continue;
				}
				if (digHardness(player, level, cache, route, cell) < 0.0F || Vec3.atCenterOf(cell).distanceTo(player.getEyePosition()) > RIP_REACH) {
					return List.of();
				}
				shaft.add(cell);
			}
			// (only with ground under the hole to land on, no further down than the search's drops, nothing that burns or drowns)
			return shaft.isEmpty() || !shaftLanding(level, cache, feet.below(2)) ? List.of() : shaft;
		}
		Direction main;
		Direction other;
		if (to.horizontalDistanceSqr() < 0.25) {
			main = player.getDirection();
			other = main;
		} else {
			main = Math.abs(to.x) >= Math.abs(to.z) ? (to.x > 0 ? Direction.EAST : Direction.WEST) : (to.z > 0 ? Direction.SOUTH : Direction.NORTH);
			other = Math.abs(to.x) >= Math.abs(to.z) ? (to.z > 0 ? Direction.SOUTH : Direction.NORTH) : (to.x > 0 ? Direction.EAST : Direction.WEST);
		}
		List<BlockPos> found = ripAlong(player, route, level, main, toward.y);
		if (found.isEmpty() && other != main && Math.min(Math.abs(to.x), Math.abs(to.z)) > 0.5) {
			found = ripAlong(player, route, level, other, toward.y);
		}
		return found;
	}

	/**
	 * Under the bottom of a hole dug straight down ({@code bottom}, open once it's torn): a floor to land on within
	 * {@link #MAX_DROP} blocks, the way down open with no fire, lava or water in it.
	 */
	private static boolean shaftLanding(ServerLevel level, Map<Long, Integer> cache, BlockPos bottom) {
		for (int k = 0; k <= MAX_DROP; k++) {
			int under = kind(level, cache, bottom.below(k + 1));
			if ((under & (DANGER | WATER | UNLOADED)) != 0) {
				return false;
			}
			if ((under & PASSABLE) == 0) {
				return (under & FLOOR) != 0;
			}
		}
		return false;
	}

	private static List<BlockPos> ripAlong(ServerPlayer player, Route route, ServerLevel level, Direction dir, double targetY) {
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
				if (digHardness(player, level, cache, route, pos) < 0.0F || Vec3.atCenterOf(pos).distanceTo(eye) > RIP_REACH) {
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
		if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door && !door.type().canOpenByHand()) {
			// an iron door stops it
			return false;
		}
		return player.mayInteract(level, pos) && !player.blockActionRestricted(level, pos, player.gameMode());
	}

	/**
	 * What it may tear out, and how hard it is: -1 if it may not. Breakable (see {@link #breakable}), not refused before,
	 * loaded, not burning, and no water or lava beside it on any side (the tunnel never floods, as WebSymbioteDig keeps it).
	 * Nothing once the takeover's budget ({@link #RIP_MAX_BLOCKS}) is spent.
	 */
	private static float digHardness(ServerPlayer player, ServerLevel level, Map<Long, Integer> cache, Route route, BlockPos pos) {
		if (route.ripped >= RIP_MAX_BLOCKS || route.refused.contains(pos.asLong())
			|| (kind(level, cache, pos) & (WATER | DANGER | UNLOADED)) != 0 || !breakable(player, level, pos)) {
			return -1.0F;
		}
		for (Direction side : Direction.values()) {
			if ((kind(level, cache, pos.relative(side)) & (WATER | DANGER | UNLOADED)) != 0) {
				return -1.0F;
			}
		}
		return level.getBlockState(pos).getDestroySpeed(level, pos);
	}

	private static void tickRip(ServerPlayer player, Route route, long now) {
		ServerLevel level = player.level();
		if (now >= route.ripFxAt) {
			route.ripFxAt = now + RIP_FX_EVERY;
			WebSymbiote.dread(player, RIP_FX_TICKS, true);
		}
		player.setAttached(WebSymbiote.TENDRILS_OUT, now + 12);
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
		if (t % 2 == 0) {
			// the tendrils gouging at it, ripping it loose
			BlockPos at = slot.get((int)(t / 2 % slot.size()));
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
				// someone's protecting it: it's remembered and routed round, the old way dropped, and that's a stuck
				if (route.refused.size() < REFUSED_MAX) {
					route.refused.add(pos.asLong());
				}
				route.stuck++;
				route.hopeless = route.stuck >= STUCK_GIVE_UP;
				route.nodes = List.of();
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
		// a wooden door comes off whole and drops, leaves are bitten through: neither flies off as a clump
		for (int i = taken.size() - 1; i >= 0; i--) {
			BlockPos pos = taken.get(i);
			BlockState state = states.get(i);
			boolean door = state.is(BlockTags.WOODEN_DOORS);
			if (!door && !state.is(BlockTags.LEAVES)) {
				continue;
			}
			if (level.getBlockState(pos).is(state.getBlock())) {
				level.destroyBlock(pos, door, player, 512);
				PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, null);
			}
			route.ripped++;
			taken.remove(i);
			states.remove(i);
		}
		if (taken.isEmpty()) {
			finishRip(player, route);
			return;
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
		WebSymbiote.narrate(player, "dig");
		// R's side and G's take turns, so one is always free to tear out the next
		int hand = WebTendrils.freeHand(player) < 0 ? -1 : route.nextHand;
		if (hand >= 0 && player.getAttachedOrElse(WebTendrils.GRIPS, WebTendrils.Grips.NONE).get(hand) >= 0) {
			hand = WebTendrils.freeHand(player);
		}
		if (hand >= 0) {
			WebTendrils.grab(player, hand, chunk, -1L, true);
			route.nextHand = hand == WebNet.MAIN ? WebNet.OFF : WebNet.MAIN;
		}
		if (hand < 0 || !holds(player, hand, chunk)) {
			// both tendrils full: it's shoved on ahead where it is
			chunk.setDeltaMovement(flingVelocity(player, route, chunk));
			chunk.syncVelocity = true;
			chunk.launch(player);
		} else {
			route.clumps.add(new Clump(chunk.getId(), hand, now));
		}
		// and straight on to the next blocks in the way (or, nothing more within reach, the path again)
		route.ripping = null;
		route.ripSlot = List.of();
		if (route.pathRip) {
			if (!startPathRip(player, route, now)) {
				// no more in reach: the tunnel's open, the stored way on is sent again (cut at the next face) with no new search
				route.pathRip = false;
				route.idle = 0;
				route.mode = -1;
				if (player.getAttached(WebSymbiote.RIPPING) != null) {
					player.removeAttached(WebSymbiote.RIPPING);
				}
				transmit(player, route, Math.min(hostIndex(player, route), Math.max(0, route.nodes.size() - 1)));
				route.pathAt = now;
			}
		} else if (!startRip(player, route, route.ripToward, now)) {
			finishRip(player, route);
			route.ripRetryAt = now;
		}
	}

	/** Each torn clump: yanked out of the hole, held a moment, then thrown on ahead, freeing its tendril for the next. */
	private static void tickFlings(ServerPlayer player, Route route, long now) {
		for (java.util.Iterator<Clump> it = route.clumps.iterator(); it.hasNext(); ) {
			Clump clump = it.next();
			if (!(player.level().getEntity(clump.id) instanceof BlockCluster chunk) || !chunk.isAlive() || !holds(player, clump.hand, chunk)) {
				it.remove();
				continue;
			}
			if (clump.heldAt < 0L && WebTendrils.reached(player, clump.hand)) {
				clump.heldAt = now;
			}
			boolean ready = clump.heldAt >= 0L && now - clump.heldAt >= RIP_HOLD_TICKS;
			if (!ready && now - clump.tornAt < RIP_HOLD_TIMEOUT) {
				continue;
			}
			WebTendrils.fling(player, clump.hand, flingVelocity(player, route, chunk));
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.8F, 0.5F);
			it.remove();
		}
	}

	/** Thrown on ahead, the way it's digging (and up a little); out of a shaft it's flung up and out. */
	private static Vec3 flingVelocity(ServerPlayer player, Route route, BlockCluster chunk) {
		Vec3 dir = route.ripDir;
		Vec3 ahead = dir.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : dir;
		ahead = ahead.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : ahead.normalize();
		if (route.ripShaft) {
			return ahead.scale(RIP_SHAFT_OUT).add(0.0, RIP_SHAFT_LIFT, 0.0);
		}
		return ahead.scale(RIP_FLING_SPEED).add(0.0, RIP_FLING_LIFT, 0.0);
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
		route.idle = 0;
		route.pathRip = false;
		// (the stored way is stale now: a new search before any more tunnelling)
		route.nodes = List.of();
		route.sentEnd = 0;
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
