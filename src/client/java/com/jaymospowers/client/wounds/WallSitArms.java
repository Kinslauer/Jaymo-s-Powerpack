package com.jaymospowers.client.wounds;

import com.jaymospowers.client.PoseBlend;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Andy (2026-11-15): "I dont see the arms being used in the frames, that's fine if they dont have any or if they are
 * broken, but they should use what they can". The arms getting a downed player up against a wall ({@link WallSit}): the
 * arm on top pushes off the floor beside the shoulder to roll them over onto their back, both hands prop them up as they
 * sit up, they step round on the floor as the body turns its back to the wall, then two pushes shove the hips back to it
 * and the arms fall to the sides. A hand put down stays where it is on the floor as the body moves over it ({@link #reach}:
 * the arm's two halves, the elbow folding across the way {@code KneeBend} draws it); between holds it's lifted across.
 * An arm that's broken or gone hangs and the other does it all alone; out cold, or set against the wall by another, both
 * just hang (sat up, a Minecraft arm doesn't reach the floor).
 */
final class WallSitArms {
	// ---- the arm (model pixels): its pivot, the middle of its box across, the elbow and the fingertips down it
	static final float PIVOT_X = 5.0F, PIVOT_Y = 2.0F, MIDDLE = 1.0F, UPPER = 4.0F, FORE = 6.0F;
	/** How near and far the fingertips reach from the pivot: the elbow folded as far as it goes, and all but straight. */
	static final float REACH_MIN = 7.4F, REACH_MAX = 10.1F;
	/** A hand on the floor: its fingertips' middle this high (blocks), so the hand's edges stay out of the floor. */
	static final float HAND_Y = 0.09F;
	/**
	 * Where the hands go down (blocks, about the shoulder above them): pushing off to roll over, this far out to the side
	 * (or round toward the head or the feet, these many degrees from the head, where a wall takes the room); sitting up,
	 * out and toward the feet; leaning back and pushing back to the wall, out and behind. Never nearer the wall than
	 * {@code WALL_GAP}.
	 */
	static final float PUSH_OUT = 0.4F, SIT_OUT = 0.1F, SIT_DOWN = 0.12F, BACK_OUT = 0.06F, BACK_BEHIND = 0.04F, WALL_GAP = 0.12F;
	static final float[] AROUND = {80.0F, 50.0F, 20.0F, 110.0F, 140.0F};
	/** Lifted across between holds: this high at most (blocks), and no more than this share of the way it goes. */
	static final float LIFT = 0.14F, LIFT_SHARE = 0.6F;
	/** Turning round on the hands: a step for every this many degrees, each this long (a share of getting up), this high. */
	static final float STEP_DEGREES = 45.0F, STEP_HALF = 0.035F, STEP_LIFT = 0.08F;
	/** A hand is never planted nearer the shoulder than the folded arm reaches (a little past it). */
	static final float KEEP_OUT = 1.03F;
	/** How bent the elbows hang at rest (radians, across). */
	static final float FOLD = 0.3F;

	/**
	 * When each arm does what (shares of getting up): the pushing arm's hand down beside the shoulder (put down where it
	 * is at {@code PUSH_AT}), swung over to the side as they roll, then down again to prop the sit-up; the other arm keeps
	 * the crawl's reach till {@code REACH_END}, then props too. The steps round start this long after the turn does and
	 * end this long before the first push.
	 */
	static final float PUSH_FROM = 0.05F, PUSH_END = 0.1F, PUSH_AT = 0.07F, OVER_FROM = 0.22F, OVER_END = 0.26F, PROP_FROM = 0.32F,
		PROP_END = 0.42F, PROP_AT = 0.37F, REACH_END = 0.16F, OTHER_PROP_FROM = 0.34F, OTHER_PROP_AT = 0.38F, STEPS_LEAD = 0.04F,
		STEPS_GAP = 0.02F;

	private static final int RIGHT = 0, LEFT = 1;
	/** What an arm does over a stretch: holds a pose, has its hand down (at one spot, or kept beside the body as it moves). */
	private static final int POSE = 0, PLANT = 1, TRACK = 2, REST = 3;
	/** Where a hand goes down. */
	private static final int PUSH = 0, PROP = 1, BACK = 2;
	/** The poses: hanging at the side, hanging limp (in, so it isn't through the floor rolled onto), the crawl's reach. */
	private static final int SIDE = 0, LIMP = 1, CRAWL = 2;
	private static final float[][][] POSES = {
		{{-0.1F, 0.0F, 0.2F, FOLD}, {-0.1F, 0.0F, -0.2F, -FOLD}},
		{{-0.1F, 0.0F, -0.1F, 0.0F}, {-0.1F, 0.0F, 0.1F, 0.0F}},
		{{-0.15F, 0.0F, 2.519F, 1.717F}, {-0.15F, 0.0F, -2.519F, -1.717F}}};
	private static final float[] NONE = new float[0];
	/** Model pixels to the standing frame (blocks), as the player's model is drawn. */
	private static final Matrix4f MODEL = new Matrix4f().scale(-1.0F, -1.0F, 1.0F).scale(0.9375F).translate(0.0F, -1.501F, 0.0F)
		.scale(1.0F / 16.0F);

	private WallSitArms() {
	}

	/** The body {@code p} of the way, in the standing frame (as {@code WallSit} draws it). */
	interface Body {
		Matrix4f at(float p);
	}

	/** Where it gets up: the body, and the wall's face (out of it, in the standing frame) and how far it is from the middle. */
	record Room(Body body, float nx, float nz, float distance) {
		float gap(Vector3f at) {
			return at.x * this.nx + at.z * this.nz + this.distance;
		}
	}

	/** One stretch of an arm's part: from, to (shares of getting up), what it does, which pose or hold, when it's put down. */
	private record Event(float from, float to, int kind, int what, float at, float[] steps) {
	}

	/** Each arm's part (right, left), and which arm pushes off to roll them over (-1: neither). */
	record Plan(Event[][] arms, int pusher) {
		/** Which way the body rolls over so the pusher is on top (1, -1), or {@code roll} as it was. */
		float roll(float roll) {
			return this.pusher < 0 ? roll : this.pusher == LEFT ? 1.0F : -1.0F;
		}
	}

	/**
	 * Each arm's part in getting up: {@code arms}, which arms work (bit 1 right, bit 2 left); {@code turn}, how far round
	 * the body turns (degrees); {@code roll}, the way it'd roll over (taken when nothing else decides which arm pushes).
	 */
	static Plan plan(Room room, boolean placed, boolean limp, int arms, float turn, float roll) {
		Event idle = new Event(0.0F, 0.0F, POSE, LIMP, 0.0F, NONE), rest = new Event(1.0F, 1.0F, REST, 0, 0.0F, NONE);
		if (placed || limp || arms == 0) {
			return new Plan(new Event[][] {{idle, rest}, {idle, rest}}, -1);
		}
		int pusher;
		if (arms != 3) {
			pusher = arms == 1 ? RIGHT : LEFT;
		} else {
			// the arm away from the wall pushes (lying across it), else the one the roll puts on top
			Matrix4f m = room.body().at(0.0F);
			Vector3f hips = m.transformPosition(new Vector3f(0.0F, WallSit.HIP, 0.0F));
			Vector3f off = shoulder(m, RIGHT).sub(hips);
			float across = off.x * room.nx() + off.z * room.nz();
			pusher = Math.abs(across) > 0.15F ? across < 0.0F ? LEFT : RIGHT : roll > 0.0F ? LEFT : RIGHT;
		}
		// turning round on the floor: a step for every STEP_DEGREES, the hands taking turns
		float from = WallSit.TURN_FROM + STEPS_LEAD, to = WallSit.PUSH_1_FROM - STEPS_GAP;
		int n = Math.round(Math.abs(turn) / STEP_DEGREES);
		int[] order = arms == 3 ? new int[] {pusher, 1 - pusher} : new int[] {pusher};
		float[][] steps = {new float[n], new float[n]};
		int[] counts = new int[2];
		for (int i = 0; i < n; i++) {
			float at = from + STEP_HALF + (to - from - 2.0F * STEP_HALF) * (i + 0.5F) / n;
			int arm = order[i % order.length];
			if (counts[arm] == 0 || at - steps[arm][counts[arm] - 1] >= 2.0F * STEP_HALF) {
				steps[arm][counts[arm]++] = at;
			}
		}
		Event[][] plan = new Event[2][];
		for (int arm = RIGHT; arm <= LEFT; arm++) {
			if ((arms & (1 << arm)) == 0) {
				plan[arm] = new Event[] {idle, rest};
				continue;
			}
			float p1 = WallSit.PUSH_1_FROM, p2 = WallSit.PUSH_2_FROM;
			Event track = new Event(from, to, TRACK, BACK, 0.0F, java.util.Arrays.copyOf(steps[arm], counts[arm]));
			Event push1 = new Event(p1, WallSit.PUSH_1_END, PLANT, BACK, (p1 + WallSit.PUSH_1_END) * 0.5F, NONE);
			// (the second's put down early in it: the body straightens up against the wall as it goes)
			Event push2 = new Event(p2, WallSit.PUSH_2_END, PLANT, BACK, p2 + (WallSit.PUSH_2_END - p2) * 0.25F, NONE);
			plan[arm] = arm == pusher
				? new Event[] {new Event(0.0F, 0.0F, POSE, CRAWL, 0.0F, NONE), new Event(PUSH_FROM, PUSH_END, PLANT, PUSH, PUSH_AT, NONE),
					new Event(OVER_FROM, OVER_END, POSE, SIDE, 0.0F, NONE), new Event(PROP_FROM, PROP_END, PLANT, PROP, PROP_AT, NONE), track, push1,
					push2, rest}
				: new Event[] {new Event(0.0F, REACH_END, POSE, CRAWL, 0.0F, NONE), new Event(OTHER_PROP_FROM, PROP_END, PLANT, PROP, OTHER_PROP_AT, NONE),
					track, push1, push2, rest};
		}
		return new Plan(plan, pusher);
	}

	/**
	 * Both arms {@code p} of the way, as the model's parts take them: right x, y, z and elbow fold (across), then left's.
	 * {@code rest}: the same for the arms as they rest sat there at the end.
	 */
	static float[] pose(Room room, Plan plan, float p, float[] rest) {
		Matrix4f m = room.body().at(p);
		float[] out = new float[8];
		for (int arm = RIGHT; arm <= LEFT; arm++) {
			System.arraycopy(arm(room, plan.arms()[arm], arm, p, m, rest), 0, out, arm * 4, 4);
		}
		return out;
	}

	private static float[] arm(Room room, Event[] events, int arm, float p, Matrix4f m, float[] rest) {
		for (int i = 0; i < events.length; i++) {
			Event e = events[i];
			if (p >= e.from() && p <= e.to()) {
				return at(room, e, arm, p, m, rest).angles;
			}
			Event next = i + 1 < events.length ? events[i + 1] : null;
			if (next != null && p > e.to() && p < next.from()) {
				float s = PoseBlend.smooth((p - e.to()) / (next.from() - e.to()));
				Held a = at(room, e, arm, p, m, rest), b = at(room, next, arm, p, m, rest);
				if (a.target == null && b.target == null) {
					// pose to pose: turned across
					float[] mixed = new float[4];
					for (int j = 0; j < 4; j++) {
						mixed[j] = a.angles[j] + wrap(b.angles[j] - a.angles[j]) * s;
					}
					return mixed;
				}
				// the hand lifted across from one to the other
				Vector3f from = a.tip(m, arm), to = b.tip(m, arm);
				float lift = Math.min(LIFT, LIFT_SHARE * from.distance(to)) * Mth.sin(Mth.PI * s);
				Vector3f target = keepOut(m, arm, new Vector3f(from).lerp(to, s).add(0.0F, lift, 0.0F));
				Vector3f hint = a.hint(m, arm).normalize().mul(1.0F - s).add(b.hint(m, arm).normalize().mul(s));
				return reach(m, arm, target, hint);
			}
		}
		return at(room, events[events.length - 1], arm, p, m, rest).angles;
	}

	/** An arm in one stretch of its part: its turn, and (a hand down) where the fingertips are put and the elbow's way. */
	private record Held(float[] angles, Vector3f target, Vector3f elbow) {
		/** Where the fingertips are (put down, or as the pose has them). */
		Vector3f tip(Matrix4f m, int arm) {
			return this.target != null ? new Vector3f(this.target) : fingertips(m, arm, this.angles);
		}

		/** The elbow's way (put down, or as the pose has it). */
		Vector3f hint(Matrix4f m, int arm) {
			return this.elbow != null ? new Vector3f(this.elbow) : elbowWay(m, arm, this.angles);
		}
	}

	private static Held at(Room room, Event e, int arm, float p, Matrix4f m, float[] rest) {
		if (e.kind() == POSE) {
			return new Held(POSES[e.what()][arm], null, null);
		}
		if (e.kind() == REST) {
			return new Held(new float[] {rest[arm * 4], rest[arm * 4 + 1], rest[arm * 4 + 2], rest[arm * 4 + 3]}, null, null);
		}
		Vector3f target = keepOut(m, arm, spot(room, arm, e.what(), e.kind() == TRACK ? Mth.clamp(p, e.from(), e.to()) : e.at()));
		if (e.kind() == TRACK) {
			// kept beside the body as it turns, lifted and put down again at each step
			float up = 0.0F;
			for (float step : e.steps()) {
				up += Mth.sin(Mth.PI * Mth.clamp((p - step + STEP_HALF) / (2.0F * STEP_HALF), 0.0F, 1.0F));
			}
			target.add(0.0F, up * STEP_LIFT, 0.0F);
		}
		Vector3f elbow = elbowHint(m, arm, e.what());
		return new Held(reach(m, arm, target, elbow), target, elbow);
	}

	// ---- where the hands go

	/** The body's hips, the level way its back faces, and level across it. */
	private record Axes(Vector3f hips, Vector3f back, Vector3f across) {
		/** Which side of the body {@code arm}'s shoulder is on, along {@link #across} (1 or -1). */
		float side(Matrix4f m, int arm) {
			return shoulder(m, arm).sub(this.hips).dot(this.across) >= 0.0F ? 1.0F : -1.0F;
		}
	}

	private static Axes axes(Matrix4f m) {
		Vector3f hips = m.transformPosition(new Vector3f(0.0F, WallSit.HIP, 0.0F));
		Vector3f up = m.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)), behind = m.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F));
		Vector3f back = new Vector3f(up.x + behind.x, 0.0F, up.z + behind.z);
		if (back.lengthSquared() > 1.0E-12F) {
			back.normalize();
		}
		return new Axes(hips, back, new Vector3f(back.z, 0.0F, -back.x));
	}

	/** Where {@code arm}'s hand goes down for {@code style}, with the body as it is {@code at} of the way (standing frame). */
	private static Vector3f spot(Room room, int arm, int style, float at) {
		Matrix4f m = room.body().at(at);
		Axes axes = axes(m);
		float s = axes.side(m, arm);
		Vector3f shoulder = shoulder(m, arm);
		shoulder.y = HAND_Y;
		Vector3f spot = new Vector3f();
		if (style == PUSH) {
			// out beside the shoulder, or round toward the head or the feet where a wall takes the room
			for (float degrees : AROUND) {
				float a = degrees * Mth.DEG_TO_RAD;
				spot.set(axes.back()).mul(Mth.cos(a)).add(new Vector3f(axes.across()).mul(s * Mth.sin(a))).mul(PUSH_OUT).add(shoulder);
				if (room.gap(spot) >= WALL_GAP) {
					break;
				}
			}
		} else if (style == PROP) {
			// beside the shoulder toward the feet: leant on as the shoulders come up over it
			spot.set(axes.across()).mul(s * SIT_OUT).add(shoulder).sub(new Vector3f(axes.back()).mul(SIT_DOWN));
		} else {
			// beside and behind the shoulder, sat leaning back on it
			spot.set(axes.across()).mul(s * BACK_OUT).add(shoulder).add(new Vector3f(axes.back()).mul(BACK_BEHIND));
		}
		float gap = room.gap(spot);
		if (gap < WALL_GAP) {
			spot.add(room.nx() * (WALL_GAP - gap), 0.0F, room.nz() * (WALL_GAP - gap));
		}
		return spot;
	}

	/** The elbow's way with the hand down: up and out pushing off the floor, out (and a little up) otherwise. */
	private static Vector3f elbowHint(Matrix4f m, int arm, int style) {
		Axes axes = axes(m);
		float s = axes.side(m, arm);
		return style == PUSH ? new Vector3f(axes.across()).mul(0.7F * s).add(0.0F, 1.0F, 0.0F)
			: new Vector3f(axes.across()).mul(s).add(0.0F, 0.5F, 0.0F);
	}

	/** {@code at} moved out of the ball round the shoulder the arm can't fold into, along the floor. */
	private static Vector3f keepOut(Matrix4f m, int arm, Vector3f at) {
		Vector3f shoulder = shoulder(m, arm);
		float min = REACH_MIN * WallSit.PX * KEEP_OUT;
		if (at.distance(shoulder) >= min) {
			return at;
		}
		float y = Math.max(at.y, HAND_Y), dy = y - shoulder.y, out = Mth.sqrt(Math.max(0.0F, min * min - dy * dy));
		float hx = at.x - shoulder.x, hz = at.z - shoulder.z, h = Mth.sqrt(hx * hx + hz * hz);
		if (h < 1.0E-6F) {
			Axes axes = axes(m);
			float s = axes.side(m, arm);
			hx = axes.across().x * s;
			hz = axes.across().z * s;
			h = Mth.sqrt(hx * hx + hz * hz);
			if (h < 1.0E-6F) {
				return at;
			}
		}
		return new Vector3f(shoulder.x + hx / h * out, y, shoulder.z + hz / h * out);
	}

	// ---- the arm itself

	private static float pivotX(int arm) {
		return arm == RIGHT ? -PIVOT_X : PIVOT_X;
	}

	private static float middle(int arm) {
		return arm == RIGHT ? -MIDDLE : MIDDLE;
	}

	/** The arm's pivot (standing frame). */
	private static Vector3f shoulder(Matrix4f m, int arm) {
		return new Matrix4f(m).mul(MODEL).transformPosition(new Vector3f(pivotX(arm), PIVOT_Y, 0.0F));
	}

	/** From the pivot to the fingertips' middle with the elbow folded {@code fold} (model pixels, the part unturned). */
	private static Vector3f hand(int arm, float fold) {
		return new Vector3f(middle(arm) - FORE * Mth.sin(fold), UPPER + FORE * Mth.cos(fold), 0.0F);
	}

	private static Vector3f fingertips(Matrix4f m, int arm, float[] angles) {
		Vector3f hand = new Quaternionf().rotationZYX(angles[2], angles[1], angles[0]).transform(hand(arm, angles[3]));
		return new Matrix4f(m).mul(MODEL).transformPosition(hand.add(pivotX(arm), PIVOT_Y, 0.0F));
	}

	private static Vector3f elbowWay(Matrix4f m, int arm, float[] angles) {
		Quaternionf turn = new Quaternionf().rotationZYX(angles[2], angles[1], angles[0]);
		Vector3f hand = turn.transform(hand(arm, angles[3])).normalize();
		Vector3f elbow = turn.transform(new Vector3f(middle(arm), UPPER, 0.0F));
		elbow.sub(new Vector3f(hand).mul(elbow.dot(hand)));
		return new Matrix4f(m).mul(MODEL).transformDirection(elbow);
	}

	/**
	 * The arm's turn (x, y, z, as a part takes them) and elbow fold putting its fingertips on {@code target}, the elbow
	 * toward {@code hint} (standing frame); short of it, or folded as far as it goes, if it's out of reach.
	 */
	static float[] reach(Matrix4f m, int arm, Vector3f target, Vector3f hint) {
		Matrix4f inverse = new Matrix4f(m).mul(MODEL).invertAffine();
		Vector3f to = inverse.transformPosition(new Vector3f(target)).sub(pivotX(arm), PIVOT_Y, 0.0F);
		return solve(arm, to, inverse.transformDirection(new Vector3f(hint)));
	}

	/** {@link #reach} in the model's pixels: {@code to} from the pivot, {@code hint} the elbow's way. */
	static float[] solve(int arm, Vector3f to, Vector3f hint) {
		float length = to.length();
		if (length < 1.0E-4F) {
			return POSES[SIDE][arm].clone();
		}
		boolean right = arm == RIGHT;
		float xc = middle(arm), reach = Mth.clamp(length, REACH_MIN, REACH_MAX);
		// the fold that puts the fingertips that far from the pivot (the forearm swings across about the elbow)
		float r = Mth.sqrt(48.0F * 48.0F + 144.0F * xc * xc), beta = (float) Math.atan2(12.0F * xc, 48.0F);
		float c = Mth.clamp((reach * reach - xc * xc - 52.0F) / r, -1.0F, 1.0F);
		float fold = right ? (float) Math.acos(c) - beta : -(float) Math.acos(c) - beta;
		// turned so the fingertips point at it, then about that line so the elbow goes its way
		Vector3f way = new Vector3f(to).div(length);
		Quaternionf turn = new Quaternionf().rotationTo(hand(arm, fold).normalize(), way);
		Vector3f elbow = turn.transform(new Vector3f(xc, UPPER, 0.0F));
		elbow.sub(new Vector3f(way).mul(elbow.dot(way)));
		Vector3f toward = new Vector3f(hint).sub(new Vector3f(way).mul(hint.dot(way)));
		if (elbow.lengthSquared() > 1.0E-12F && toward.lengthSquared() > 1.0E-12F) {
			float twist = (float) Math.atan2(way.dot(new Vector3f(elbow).cross(toward)), elbow.dot(toward));
			turn = new Quaternionf().rotationAxis(twist, way).mul(turn);
		}
		// as x, y, z angles taken in z, y, x order (ModelPart's)
		Matrix3f rot = new Matrix3f().set(turn);
		float y = (float) Math.asin(Mth.clamp(-rot.m02, -1.0F, 1.0F));
		float x = (float) Math.atan2(rot.m12, rot.m22);
		float z = (float) Math.atan2(rot.m01, rot.m00);
		return new float[] {x, y, z, fold};
	}

	/** {@code a} wrapped into -pi..pi. */
	static float wrap(float a) {
		float d = a % Mth.TWO_PI;
		return d > Mth.PI ? d - Mth.TWO_PI : d < -Mth.PI ? d + Mth.TWO_PI : d;
	}
}
