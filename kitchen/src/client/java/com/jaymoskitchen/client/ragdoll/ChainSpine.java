package com.jaymoskitchen.client.ragdoll;

import com.jaymoskitchen.client.mixin.ModelPartAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * (0.5.47) A snake's model laid along its chain ragdoll ({@code RagdollRig.Shape.CHAIN}): each body segment follows the
 * joints under it, so it lies bent and sagging wherever the physics put it instead of a few stiff blocks. Andy: "a
 * snake should go all noodle like when they ragdoll, this stiffness doesnt make sense".
 * <p>
 * The model's spine is found by name: a part called {@code tail...} with its {@code tail...} children running back,
 * and beside it a {@code neck} (with the head on it) or a {@code head} running forward (Naturalist's snake: main ->
 * neck -> skull, main -> tail -> tail2 -> tail3 -> tail4). The spine, head tip to tail tip, is stretched over the
 * chain's points by length, and each segment is turned to point from where its hinge falls on the chain to where its far
 * end falls.
 */
public final class ChainSpine {
	/** One segment: the part, its parents from the model's root, the way it reaches (its own frame, unit), and where it lies along the spine. */
	private record Segment(ModelPart part, ModelPart[] chain, float dirX, float dirY, float dirZ, float hingeAt, float farAt,
		boolean placed) {
	}

	/** {@code restAt}: arc lengths (pixels from the head's tip) of the points of {@code rest} (x, y, z each, model space standing). */
	private record Spine(List<Segment> segments, float length, Set<String> names, float[] restAt, float[] rest) {
	}

	/**
	 * (0.5.50) Draped over the arms: how far either side of the middle lies flat on them, the radius it rolls over their
	 * edge with (blocks), how much more each block further down swings, and how many points the drape is drawn through.
	 */
	private static final float DRAPE_REST = 0.12F, DRAPE_BEND = 0.12F, DRAPE_LAG = 0.6F;
	private static final int DRAPE_POINTS = 24;
	/**
	 * (0.5.50) Held in one hand: how far behind the snout it's gripped (pixels of model), and how far above the ground the
	 * lowest of it stays (blocks, at scale 1) before it lies along it.
	 */
	private static final float HOLD_BEHIND = 6.0F, HANG_CLEAR = 0.1F;
	private static final Map<ModelPart, Spine> SPINES = new WeakHashMap<>();
	private static final Spine NONE = new Spine(List.of(), 0.0F, Set.of(), new float[0], new float[0]);

	private ChainSpine() {
	}

	/**
	 * Lays {@code root}'s spine along {@code points} (the chain's line from the head's tip to the tail's tip, x, y, z each,
	 * relative to where the body's drawn), the model placed by {@code body} at {@code scale}. Returns the names of every
	 * part it posed or that rides on one (left alone after), or null if the model has no spine it knows.
	 */
	static Set<String> apply(ModelPart root, Matrix4f body, float scale, float[] points) {
		Spine spine = SPINES.computeIfAbsent(root, ChainSpine::find);
		if (spine.segments.isEmpty() || points.length < 6) {
			return null;
		}
		// the chain into model space (pixels), as LivingEntityRenderer draws a standing model (see LooseParts#apply)
		Matrix4f toModel = new Matrix4f(body).scale(scale).scale(1.0F, -1.0F, -1.0F).translate(0.0F, -1.501F, 0.0F)
			.scale(1.0F / 16.0F).invert();
		int n = points.length / 3;
		float[] px = new float[n], py = new float[n], pz = new float[n], at = new float[n];
		Vector3f v = new Vector3f();
		for (int i = 0; i < n; i++) {
			toModel.transformPosition(v.set(points[i * 3], points[i * 3 + 1], points[i * 3 + 2]));
			px[i] = v.x;
			py[i] = v.y;
			pz[i] = v.z;
			at[i] = i == 0 ? 0.0F : at[i - 1] + (float) Math.sqrt(sq(px[i] - px[i - 1]) + sq(py[i] - py[i - 1]) + sq(pz[i] - pz[i - 1]));
		}
		Matrix4f parentMat = new Matrix4f();
		Quaternionf parentTurn = new Quaternionf(), step = new Quaternionf();
		Vector3f hinge = new Vector3f(), far = new Vector3f(), want = new Vector3f();
		for (Segment s : spine.segments) {
			parentMat.identity();
			parentTurn.identity();
			for (ModelPart p : s.chain) {
				parentMat.translate(p.x, p.y, p.z).rotate(step.rotationZYX(p.zRot, p.yRot, p.xRot));
				parentTurn.mul(step);
			}
			ModelPart part = s.part;
			if (s.placed) {
				// the first segment each way from the body: its hinge onto the chain, as far from the head's tip as on
				// the model
				point(px, py, pz, at, s.hingeAt, hinge);
				Vector3f local = new Matrix4f(parentMat).invert().transformPosition(new Vector3f(hinge));
				part.x = local.x;
				part.y = local.y;
				part.z = local.z;
			} else {
				// the rest hinge where the last one ends
				parentMat.transformPosition(hinge.set(part.x, part.y, part.z));
			}
			// its far end on the chain, as long as it is, onward from about where it starts (the head's way for the neck
			// and head, the tail's for the tail)
			float length = Math.abs(s.farAt - s.hingeAt);
			reachAlong(px, py, pz, at, hinge, s.hingeAt, s.farAt < s.hingeAt, length, far);
			far.sub(hinge, want);
			if (want.lengthSquared() < 1.0E-8F) {
				continue;
			}
			// the way it should reach, in its parent's frame; turned the shortest way there from how it reaches standing
			new Quaternionf(parentTurn).conjugate().transform(want.normalize());
			Quaternionf turn = new Quaternionf().rotationTo(s.dirX, s.dirY, s.dirZ, want.x, want.y, want.z);
			Vector3f euler = turn.getEulerAnglesZYX(new Vector3f());
			part.xRot = euler.x;
			part.yRot = euler.y;
			part.zRot = euler.z;
		}
		return spine.names;
	}

	/**
	 * (0.5.50) A carried (or slung, or falling) snake: no physics runs, so its spine is laid over the carrier's arms by
	 * hand, the middle across them ({@code across}: that way, the carrier's left, as a Minecraft yaw in degrees) and both
	 * ends hanging down off either side, swung {@code swingBack} and {@code swingLeft} (radians, as Limp has them) the
	 * further the lower. {@code body} and {@code scale} place the model as {@link #apply} has them. Returns what
	 * {@link #apply} does; null for a model with no spine.
	 */
	static Set<String> drape(ModelPart root, Matrix4f body, float scale, float across, float swingBack, float swingLeft) {
		Spine spine = SPINES.computeIfAbsent(root, ChainSpine::find);
		if (spine.segments.isEmpty() || spine.restAt.length < 2) {
			return null;
		}
		Matrix4f toWorld = new Matrix4f(body).scale(scale).scale(1.0F, -1.0F, -1.0F).translate(0.0F, -1.501F, 0.0F)
			.scale(1.0F / 16.0F);
		int n = spine.restAt.length;
		float[] wx = new float[n], wy = new float[n], wz = new float[n];
		Vector3f v = new Vector3f();
		for (int i = 0; i < n; i++) {
			toWorld.transformPosition(v.set(spine.rest[i * 3], spine.rest[i * 3 + 1], spine.rest[i * 3 + 2]));
			wx[i] = v.x;
			wy[i] = v.y;
			wz[i] = v.z;
		}
		// the middle stays where the body is drawn now; the head goes off whichever side of the arms it already lies
		Vector3f middle = new Vector3f();
		float half = spine.length * 0.5F;
		point(wx, wy, wz, spine.restAt, half, middle);
		float yaw = across * Mth.DEG_TO_RAD;
		float dx = Mth.cos(yaw), dz = Mth.sin(yaw);
		if ((wx[0] - wx[n - 1]) * dx + (wz[0] - wz[n - 1]) * dz < 0.0F) {
			dx = -dx;
			dz = -dz;
		}
		// square to it, level: the carrier's front and back
		float fx = -dz, fz = dx;
		float blocks = scale / 16.0F;
		int samples = DRAPE_POINTS;
		float[] out = new float[samples * 3];
		for (int i = 0; i < samples; i++) {
			float s = spine.length * i / (samples - 1);
			// signed distance from the middle (blocks): + toward the head
			float u = (half - s) * blocks;
			float side = u >= 0.0F ? 1.0F : -1.0F;
			float a = Math.abs(u);
			float along, down;
			float bend = DRAPE_BEND * (float) (Math.PI * 0.5);
			float ox = 0.0F, oz = 0.0F;
			if (a <= DRAPE_REST) {
				along = a;
				down = 0.0F;
			} else if (a <= DRAPE_REST + bend) {
				// rolling over the edge of the arm
				float t = (a - DRAPE_REST) / DRAPE_BEND;
				along = DRAPE_REST + DRAPE_BEND * Mth.sin(t);
				down = DRAPE_BEND * (1.0F - Mth.cos(t));
			} else {
				// hanging, swung the further the lower it hangs
				float h = a - DRAPE_REST - bend;
				float lag = 1.0F + h * DRAPE_LAG;
				float out1 = Mth.clamp(swingLeft * side * lag, -1.2F, 1.2F), back = Mth.clamp(swingBack * lag, -1.2F, 1.2F);
				along = DRAPE_REST + DRAPE_BEND + Mth.sin(out1) * h;
				down = DRAPE_BEND + Mth.cos(out1) * Mth.cos(back) * h;
				ox = fx * Mth.sin(back) * h;
				oz = fz * Mth.sin(back) * h;
			}
			out[i * 3] = middle.x + dx * side * along + ox;
			out[i * 3 + 1] = middle.y - down;
			out[i * 3 + 2] = middle.z + dz * side * along + oz;
		}
		return apply(root, body, scale, out);
	}

	/**
	 * (0.5.50) A snake held in one hand, just behind its head ({@link #HOLD_BEHIND} along from its snout), the hand at
	 * {@code handX, handY, handZ} in the placement's frame and {@code hand} blocks above the ground: the head sticks up out of the fist, the body hangs
	 * straight down (swung {@code swingBack} and {@code swingLeft}, more the lower), and whatever reaches the ground
	 * trails along it behind the carrier ({@code facing}, a yaw in degrees). Returns as {@link #apply}.
	 */
	static Set<String> hang(ModelPart root, Matrix4f body, float scale, float facing, float swingBack, float swingLeft,
		float hand, float handX, float handY, float handZ) {
		Spine spine = SPINES.computeIfAbsent(root, ChainSpine::find);
		if (spine.segments.isEmpty()) {
			return null;
		}
		float blocks = scale / 16.0F;
		float yaw = facing * Mth.DEG_TO_RAD;
		// the carrier's back and right, level
		float bx = Mth.sin(yaw), bz = -Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
		float hold = Math.min(HOLD_BEHIND, spine.length * 0.5F) * blocks;
		float drop = Math.max(hand - HANG_CLEAR * scale, 0.05F);
		float back = Mth.clamp(swingBack, -0.8F, 0.8F), side = Mth.clamp(swingLeft, -0.8F, 0.8F);
		int samples = DRAPE_POINTS;
		float[] out = new float[samples * 3];
		for (int i = 0; i < samples; i++) {
			float u = spine.length * blocks * i / (samples - 1) - hold;
			float x, y, z;
			if (u <= 0.0F) {
				// the head, up out of the fist
				x = 0.0F;
				y = -u;
				z = 0.0F;
			} else {
				// hanging, swung the more the lower, down to the ground
				float h = Math.min(u, drop);
				float lag = 1.0F + h * DRAPE_LAG;
				float b = back * lag, l = side * lag;
				x = (bx * Mth.sin(b) + rx * Mth.sin(l)) * h;
				z = (bz * Mth.sin(b) + rz * Mth.sin(l)) * h;
				y = -Mth.cos(b) * Mth.cos(l) * h;
				if (u > drop) {
					// on the ground: trailing behind
					float t = u - drop;
					x += bx * t;
					z += bz * t;
					y = -drop;
				}
			}
			out[i * 3] = handX + x;
			out[i * 3 + 1] = handY + y;
			out[i * 3 + 2] = handZ + z;
		}
		return apply(root, body, scale, out);
	}

	/** The point {@code s} along the chain (pixels from the head's tip), into {@code out}. */
	private static void point(float[] x, float[] y, float[] z, float[] at, float s, Vector3f out) {
		int n = at.length;
		int i = 1;
		while (i < n - 1 && at[i] < s) {
			i++;
		}
		float span = at[i] - at[i - 1];
		float t = span < 1.0E-6F ? 0.0F : Math.max(0.0F, Math.min(1.0F, (s - at[i - 1]) / span));
		out.set(x[i - 1] + (x[i] - x[i - 1]) * t, y[i - 1] + (y[i] - y[i - 1]) * t, z[i - 1] + (z[i] - z[i - 1]) * t);
	}

	/**
	 * The point on the chain {@code length} from {@code from}, searching from {@code start} along it ({@code back}: toward
	 * the head's tip); past the chain's end, on along its last stretch.
	 */
	private static void reachAlong(float[] x, float[] y, float[] z, float[] at, Vector3f from, float start, boolean back,
		float length, Vector3f out) {
		int n = at.length;
		// the line's points in the search's order, from just past where it starts
		int first = 0;
		while (first < n - 1 && at[first + 1] <= start) {
			first++;
		}
		Vector3f a = new Vector3f(), b = new Vector3f();
		point(x, y, z, at, start, a);
		int i = back ? first : first + 1;
		float r2 = length * length;
		while (back ? i >= 0 : i < n) {
			b.set(x[i], y[i], z[i]);
			if (b.distanceSquared(from) >= r2) {
				// it crosses between a and b: where, by halves
				float lo = 0.0F, hi = 1.0F;
				for (int k = 0; k < 12; k++) {
					float mid = (lo + hi) * 0.5F;
					float mx = a.x + (b.x - a.x) * mid, my = a.y + (b.y - a.y) * mid, mz = a.z + (b.z - a.z) * mid;
					if (sq(mx - from.x) + sq(my - from.y) + sq(mz - from.z) >= r2) {
						hi = mid;
					} else {
						lo = mid;
					}
				}
				out.set(a.x + (b.x - a.x) * hi, a.y + (b.y - a.y) * hi, a.z + (b.z - a.z) * hi);
				return;
			}
			a.set(b);
			i += back ? -1 : 1;
		}
		// the chain ran out: on the way its end points
		int e = back ? 0 : n - 1, f = back ? Math.min(1, n - 1) : Math.max(n - 2, 0);
		out.set(x[e] - x[f], y[e] - y[f], z[e] - z[f]);
		if (out.lengthSquared() < 1.0E-8F) {
			out.set(0.0F, 0.0F, 1.0F);
		}
		out.normalize(length).add(from);
	}

	private static float sq(float v) {
		return v * v;
	}

	// ---------------------------------------------------------------- finding the spine

	private static Spine find(ModelPart root) {
		Spine spine = search(root, new ModelPart[] {root}, 0);
		return spine == null ? NONE : spine;
	}

	/** The first part (three deep at most) that holds both a tail and a neck or head. */
	private static Spine search(ModelPart part, ModelPart[] chain, int depth) {
		Map<String, ModelPart> kids = children(part);
		String tail = null, front = null;
		for (String name : kids.keySet()) {
			String n = name.toLowerCase(Locale.ROOT);
			if (tail == null && n.startsWith("tail")) {
				tail = name;
			} else if (n.equals("neck") || front == null && n.equals("head")) {
				front = name;
			}
		}
		if (tail != null && front != null) {
			return build(part, chain, kids.get(front), kids.get(tail));
		}
		if (depth >= 3) {
			return null;
		}
		for (ModelPart child : kids.values()) {
			Spine found = search(child, extend(chain, child), depth + 1);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/**
	 * The spine from {@code front} (a neck, maybe with a head on it, or a head) and {@code tail} (and the tails on it),
	 * both on {@code body}, laid out by length from the head's tip.
	 */
	private static Spine build(ModelPart body, ModelPart[] chain, ModelPart front, ModelPart tail) {
		Set<String> names = new java.util.HashSet<>();
		// the front run, from the body outward: the neck, then a head on it
		List<ModelPart> frontRun = new ArrayList<>();
		frontRun.add(front);
		ModelPart head = null;
		for (Map.Entry<String, ModelPart> e : children(front).entrySet()) {
			String n = e.getKey().toLowerCase(Locale.ROOT);
			if (n.equals("head") || n.equals("skull")) {
				head = e.getValue();
			}
		}
		if (head != null) {
			frontRun.add(head);
		}
		// the tail run: each next tail on the last
		List<ModelPart> tailRun = new ArrayList<>();
		ModelPart t = tail;
		while (t != null && tailRun.size() < 16) {
			tailRun.add(t);
			ModelPart next = null;
			for (Map.Entry<String, ModelPart> e : children(t).entrySet()) {
				if (e.getKey().toLowerCase(Locale.ROOT).startsWith("tail")) {
					next = e.getValue();
					break;
				}
			}
			t = next;
		}
		// lengths, in each part's own frame: to the next part's hinge, or (the ends) as far as its boxes reach
		int fn = frontRun.size();
		float[][] frontDir = new float[fn][];
		for (int i = 0; i < fn; i++) {
			ModelPart p = frontRun.get(i);
			boolean tip = i == fn - 1;
			if (!tip) {
				frontDir[i] = toward(frontRun.get(i + 1).getInitialPose());
			} else if (p == head || fn == 1) {
				// a head reaches forward (-Z), as far as its boxes go
				frontDir[i] = new float[] {0.0F, 0.0F, -1.0F, extent(p, 0.0F, 0.0F, -1.0F)};
			} else {
				frontDir[i] = reach(p);
			}
		}
		int tn = tailRun.size();
		float[][] tailDir = new float[tn][];
		for (int i = 0; i < tn; i++) {
			tailDir[i] = i < tn - 1 ? toward(tailRun.get(i + 1).getInitialPose()) : reach(tailRun.get(i));
		}
		if (frontDir[fn - 1] == null || tailDir[tn - 1] == null) {
			return null;
		}
		float frontLength = 0.0F;
		for (float[] d : frontDir) {
			frontLength += d[3];
		}
		PartPose fp = front.getInitialPose(), tp = tail.getInitialPose();
		float gap = (float) Math.sqrt(sq(fp.x() - tp.x()) + sq(fp.y() - tp.y()) + sq(fp.z() - tp.z()));
		List<Segment> segments = new ArrayList<>();
		// front: hinges at the body end (further along from the head's tip), reaching toward the head
		float s = frontLength;
		ModelPart[] c = chain;
		for (int i = 0; i < fn; i++) {
			ModelPart p = frontRun.get(i);
			float[] d = frontDir[i];
			segments.add(new Segment(p, c, d[0], d[1], d[2], s, s - d[3], i == 0));
			s -= d[3];
			c = extend(c, p);
		}
		// tail: hinges at the head end, reaching back
		s = frontLength + gap;
		c = chain;
		for (int i = 0; i < tn; i++) {
			ModelPart p = tailRun.get(i);
			float[] d = tailDir[i];
			segments.add(new Segment(p, c, d[0], d[1], d[2], s, s + d[3], i == 0));
			s += d[3];
			c = extend(c, p);
		}
		for (ModelPart p : frontRun) {
			collect(p, names);
		}
		for (ModelPart p : tailRun) {
			collect(p, names);
		}
		// every part named the same as a spine part (and what's on it) is the spine's: LooseParts leaves them be
		for (Map.Entry<String, ModelPart> e : children(body).entrySet()) {
			if (frontRun.contains(e.getValue()) || tailRun.contains(e.getValue())) {
				names.add(e.getKey());
			}
		}
		// (0.5.50) the spine standing, as points along it: every hinge and far end, head's tip first
		java.util.TreeMap<Float, float[]> line = new java.util.TreeMap<>();
		for (Segment g : segments) {
			float hx = 0.0F, hy = 0.0F, hz = 0.0F;
			for (ModelPart a : g.chain) {
				PartPose pose = a.getInitialPose();
				hx += pose.x();
				hy += pose.y();
				hz += pose.z();
			}
			PartPose own = g.part.getInitialPose();
			hx += own.x();
			hy += own.y();
			hz += own.z();
			float len = Math.abs(g.farAt - g.hingeAt);
			line.putIfAbsent(g.hingeAt, new float[] {hx, hy, hz});
			line.putIfAbsent(g.farAt, new float[] {hx + g.dirX * len, hy + g.dirY * len, hz + g.dirZ * len});
		}
		float[] restAt = new float[line.size()], rest = new float[line.size() * 3];
		int k = 0;
		for (Map.Entry<Float, float[]> e : line.entrySet()) {
			restAt[k] = e.getKey();
			System.arraycopy(e.getValue(), 0, rest, k * 3, 3);
			k++;
		}
		return new Spine(segments, s, names, restAt, rest);
	}

	/** Unit direction and distance to a child's hinge: x, y, z, length. */
	private static float[] toward(PartPose child) {
		float l = (float) Math.sqrt(sq(child.x()) + sq(child.y()) + sq(child.z()));
		if (l < 1.0E-4F) {
			return new float[] {0.0F, 0.0F, 1.0F, 0.0F};
		}
		return new float[] {child.x() / l, child.y() / l, child.z() / l, l};
	}

	/** Toward the middle of a part's boxes, and how far they reach that way; null if it has none. */
	private static float[] reach(ModelPart part) {
		List<ModelPart.Cube> cubes = cubes(part);
		if (cubes.isEmpty()) {
			return null;
		}
		float cx = 0.0F, cy = 0.0F, cz = 0.0F;
		for (ModelPart.Cube cube : cubes) {
			cx += (cube.minX + cube.maxX) * 0.5F;
			cy += (cube.minY + cube.maxY) * 0.5F;
			cz += (cube.minZ + cube.maxZ) * 0.5F;
		}
		float l = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
		if (l < 1.0E-4F) {
			return null;
		}
		float dx = cx / l, dy = cy / l, dz = cz / l;
		return new float[] {dx, dy, dz, extent(part, dx, dy, dz)};
	}

	/** How far a part's boxes reach along a direction from its hinge (pixels). */
	private static float extent(ModelPart part, float dx, float dy, float dz) {
		float length = 0.0F;
		for (ModelPart.Cube c : cubes(part)) {
			for (int i = 0; i < 8; i++) {
				float x = (i & 1) == 0 ? c.minX : c.maxX, y = (i & 2) == 0 ? c.minY : c.maxY, z = (i & 4) == 0 ? c.minZ : c.maxZ;
				length = Math.max(length, x * dx + y * dy + z * dz);
			}
		}
		return Math.max(length, 0.5F);
	}

	private static void collect(ModelPart part, Set<String> names) {
		for (Map.Entry<String, ModelPart> e : children(part).entrySet()) {
			names.add(e.getKey());
			collect(e.getValue(), names);
		}
	}

	private static ModelPart[] extend(ModelPart[] chain, ModelPart part) {
		ModelPart[] next = java.util.Arrays.copyOf(chain, chain.length + 1);
		next[chain.length] = part;
		return next;
	}

	private static Map<String, ModelPart> children(ModelPart part) {
		return ((ModelPartAccessor) (Object) part).jaymoskitchen$children();
	}

	private static List<ModelPart.Cube> cubes(ModelPart part) {
		return ((ModelPartAccessor) (Object) part).jaymoskitchen$cubes();
	}
}
