package com.jaymospowers.client.power.web;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Web lines as real simulated rope (client only, looks only): a chain of points pinned at the hand and where it
 * sticks, pulled down by gravity and held to the line's length, that drapes over edges and lies on the ground instead
 * of passing through blocks. Slack line piles up and swings; pulled taut it straightens. Our own verlet rope, made
 * after the idea of physics fishing-line mods.
 */
final class WebRopeSim {
	// ===== the knobs ================================================================================================
	/** A point every this many blocks of line, between {@code MIN_POINTS} and {@code MAX_POINTS}. */
	static final double POINT_SPACING = 0.8;
	static final int MIN_POINTS = 10, MAX_POINTS = 48;
	/** Gravity on the line (blocks/tick²) and the share of its motion each point keeps a tick (air drag). */
	static final double GRAVITY = 0.05, DRAG = 0.96;
	/** Lying on something, a point keeps only this share of its sideways motion (friction). */
	static final double FRICTION = 0.5;
	/** Length passes a tick (more = stiffer, truer to length). */
	static final int ITERATIONS = 14;
	/** How far a point is kept off a block face it rests on. */
	static final double SKIN = 0.03;
	/** An end that jumps further than this (blocks) in a tick is a new line: the rope starts over. */
	static final double RESET_JUMP = 3.0;
	/**
	 * A line with no more than {@code TAUT_SLACK} blocks of slack is taut: drawn dead straight, the way it sticks. From
	 * there to {@code LOOSE_SLACK} it eases into hanging free (a chain of points alone never pulls quite straight under
	 * gravity: a long one sagged even with no slack at all). {@code TAUT_PULL}: share of the way a taut line's points are
	 * drawn straight each tick.
	 */
	static final double TAUT_SLACK = 0.3, LOOSE_SLACK = 2.0, TAUT_PULL = 0.6;
	// ================================================================================================================

	private static final class Rope {
		Vec3[] now;
		Vec3[] before;
		/** Where the ends were at the last two ticks (to draw it between them). */
		Vec3[] drawnNow;
		Vec3[] drawnBefore;
		Vec3 hand;
		Vec3 anchor;
		double length;
		long seen;
	}

	private static final Map<Long, Rope> ROPES = new HashMap<>();

	private WebRopeSim() {
	}

	/**
	 * The shape of rope {@code key} this frame, hung from {@code hand} to {@code anchor} (world positions) with
	 * {@code length} blocks of line out. Positions given relative to {@code camera}.
	 */
	static WebLineShape.Shape shape(long key, Vec3 hand, Vec3 anchor, double length, float partialTick, Vec3 camera) {
		long now = System.nanoTime();
		Rope rope = ROPES.get(key);
		double span = hand.distanceTo(anchor);
		if (rope == null || now - rope.seen > 250_000_000L || rope.anchor.distanceTo(anchor) > RESET_JUMP * 4.0) {
			rope = start(hand, anchor);
			ROPES.put(key, rope);
		}
		rope.seen = now;
		rope.hand = hand;
		rope.anchor = anchor;
		rope.length = Math.max(span * 0.999, length);
		Rope r = rope;
		double taut = taut(r.length, span);
		int last = r.now.length - 1;
		// between the last two ticks, with the ends where they are this frame (any gap shared out along it)
		Vec3[] points = new Vec3[r.now.length];
		for (int i = 0; i <= last; i++) {
			points[i] = r.drawnBefore[i].lerp(r.drawnNow[i], partialTick);
		}
		Vec3 handGap = hand.subtract(points[0]), anchorGap = anchor.subtract(points[last]);
		for (int i = 0; i <= last; i++) {
			double t = i / (double)last;
			points[i] = points[i].add(handGap.scale(1.0 - t)).add(anchorGap.scale(t)).lerp(hand.lerp(anchor, t), taut).subtract(camera);
		}
		return t -> {
			double at = Mth.clamp(t, 0.0, 1.0) * last;
			int i = Math.min(last - 1, (int)at);
			return points[i].lerp(points[i + 1], at - i);
		};
	}

	/** 1 for a taut line (drawn straight), easing to 0 for one with plenty of slack (hanging free). */
	private static double taut(double length, double span) {
		return 1.0 - Mth.clamp((length - span - TAUT_SLACK) / (LOOSE_SLACK - TAUT_SLACK), 0.0, 1.0);
	}

	private static Rope start(Vec3 hand, Vec3 anchor) {
		Rope rope = new Rope();
		int count = Mth.clamp((int)Math.ceil(hand.distanceTo(anchor) / POINT_SPACING) + 1, MIN_POINTS, MAX_POINTS);
		rope.now = new Vec3[count];
		rope.before = new Vec3[count];
		for (int i = 0; i < count; i++) {
			rope.now[i] = hand.lerp(anchor, i / (double)(count - 1));
			rope.before[i] = rope.now[i];
		}
		rope.drawnNow = rope.now.clone();
		rope.drawnBefore = rope.now.clone();
		rope.hand = hand;
		rope.anchor = anchor;
		return rope;
	}

	/** Every client tick: each rope seen lately moves on a tick. */
	static void tick(ClientLevel level) {
		long now = System.nanoTime();
		for (Iterator<Rope> it = ROPES.values().iterator(); it.hasNext(); ) {
			Rope rope = it.next();
			if (now - rope.seen > 1_000_000_000L) {
				it.remove();
				continue;
			}
			step(level, rope);
		}
	}

	private static void step(ClientLevel level, Rope rope) {
		Vec3[] p = rope.now;
		Vec3[] old = rope.before;
		int last = p.length - 1;
		if (p[0].distanceTo(rope.hand) > RESET_JUMP || p[last].distanceTo(rope.anchor) > RESET_JUMP) {
			// an end leapt (a teleport, a far zip): lay it out afresh rather than whipping across the world
			Rope fresh = start(rope.hand, rope.anchor);
			rope.now = fresh.now;
			rope.before = fresh.before;
			rope.drawnNow = fresh.drawnNow;
			rope.drawnBefore = fresh.drawnBefore;
			return;
		}
		boolean[] resting = new boolean[p.length];
		for (int i = 1; i < last; i++) {
			Vec3 velocity = p[i].subtract(old[i]).scale(DRAG);
			old[i] = p[i];
			p[i] = p[i].add(velocity).add(0.0, -GRAVITY, 0.0);
		}
		old[0] = p[0];
		old[last] = p[last];
		p[0] = rope.hand;
		p[last] = rope.anchor;
		double segment = rope.length / last;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int pass = 0; pass < ITERATIONS; pass++) {
			for (int i = 0; i < last; i++) {
				Vec3 d = p[i + 1].subtract(p[i]);
				double distance = d.length();
				if (distance < 1.0E-6) {
					continue;
				}
				// a line can go slack but not longer than it is
				if (distance <= segment) {
					continue;
				}
				Vec3 fix = d.scale((distance - segment) / distance);
				if (i == 0) {
					p[i + 1] = p[i + 1].subtract(fix);
				} else if (i + 1 == last) {
					p[i] = p[i].add(fix);
				} else {
					p[i] = p[i].add(fix.scale(0.5));
					p[i + 1] = p[i + 1].subtract(fix.scale(0.5));
				}
			}
			for (int i = 1; i < last; i++) {
				Vec3 out = pushOut(level, pos, p[i]);
				if (out != null) {
					p[i] = out;
					resting[i] = true;
				}
			}
		}
		double taut = taut(rope.length, rope.hand.distanceTo(rope.anchor));
		if (taut > 0.0) {
			// taut: pulled straight (and its swing stilled), so it's already straight when it's drawn
			for (int i = 1; i < last; i++) {
				p[i] = p[i].lerp(rope.hand.lerp(rope.anchor, i / (double)last), taut * TAUT_PULL);
				old[i] = old[i].lerp(p[i], taut);
			}
		}
		for (int i = 1; i < last; i++) {
			if (resting[i]) {
				// friction: lying on something, it barely slides
				Vec3 moved = p[i].subtract(old[i]);
				old[i] = p[i].subtract(moved.x * FRICTION, moved.y, moved.z * FRICTION);
			}
		}
		rope.drawnBefore = rope.drawnNow;
		rope.drawnNow = p.clone();
	}

	/** {@code point} moved out of any block it's inside (to the nearest face), or null if it's in the open. */
	private static Vec3 pushOut(ClientLevel level, BlockPos.MutableBlockPos pos, Vec3 point) {
		pos.set(point.x, point.y, point.z);
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return null;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		if (shape.isEmpty()) {
			return null;
		}
		List<AABB> boxes = shape.toAabbs();
		for (AABB local : boxes) {
			AABB box = local.move(pos).inflate(SKIN);
			if (!box.contains(point)) {
				continue;
			}
			double[] pushes = {box.minX - point.x, box.maxX - point.x, box.minY - point.y, box.maxY - point.y, box.minZ - point.z, box.maxZ - point.z};
			int best = 3;
			for (int k = 0; k < 6; k++) {
				// prefer resting on top: a small bias toward up
				double cost = Math.abs(pushes[k]) - (k == 3 ? 0.15 : 0.0);
				double bestCost = Math.abs(pushes[best]) - (best == 3 ? 0.15 : 0.0);
				if (cost < bestCost) {
					best = k;
				}
			}
			return switch (best / 2) {
				case 0 -> new Vec3(point.x + pushes[best], point.y, point.z);
				case 1 -> new Vec3(point.x, point.y + pushes[best], point.z);
				default -> new Vec3(point.x, point.y, point.z + pushes[best]);
			};
		}
		return null;
	}
}
