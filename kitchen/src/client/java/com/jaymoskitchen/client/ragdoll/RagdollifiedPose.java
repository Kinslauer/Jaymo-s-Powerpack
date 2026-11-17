package com.jaymoskitchen.client.ragdoll;

import com.jaymoskitchen.ragdoll.RagdollBody;
import com.jaymoskitchen.ragdoll.RagdollRig;
import com.jaymoskitchen.ragdoll.RagdollSnapshot;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * Maps the pose Ragdollified's ragdoll came to rest in onto our humanoid rig (see {@link RagdollifiedBridge}). Its body is
 * six boxes (torso, head, two arms, two legs), each with a centre and a turn; ours is points joined by sticks. The torso
 * gives the frame (up toward the head, right toward the right arm); each of our torso points is put where it sits in that
 * frame; each limb runs from its shoulder or hip along its box's long axis, away from the torso, for the length of our
 * stick; the head runs on from the neck toward the head box. A part Ragdollified lost (a dismembered limb) hangs straight
 * down. Only the humanoid rig is mapped: other shapes have no part list to match. Called by reflection only.
 */
final class RagdollifiedPose {
	/** Ragdollified's part names, in the order used below. */
	private static final String[] NAMES = {"TORSO", "HEAD", "LEFT_LEG", "RIGHT_LEG", "LEFT_ARM", "RIGHT_ARM"};
	private static final int TORSO = 0, HEAD = 1, LEFT_LEG = 2, RIGHT_LEG = 3, LEFT_ARM = 4, RIGHT_ARM = 5;
	/** Our torso's rest height (in the rig's pixel units, 32 to the body) at the middle of Ragdollified's torso box. */
	private static final float TORSO_CENTRE = 18.0F / 32.0F;

	private static @Nullable Method worldPose, halfExtents;
	private static @Nullable Field partId, position, rotation, vx, vy, vz, qx, qy, qz, qw, partIndex;
	private static Object @Nullable [] parts;
	private static boolean failed;

	private RagdollifiedPose() {
	}

	/** One part's box: where its centre is, its long axis (unit), and half its length. */
	private record Box(Vector3d at, Vector3d axis, Vector3d side, double half) {
	}

	/** The pose of ragdoll {@code id} in {@code rig}'s points, asleep; null if it can't be mapped. */
	static @Nullable RagdollSnapshot build(Class<?> api, int id, RagdollRig rig) {
		if (rig.shape != RagdollRig.Shape.HUMANOID || failed) {
			return null;
		}
		try {
			if (worldPose == null && !init(api)) {
				failed = true;
				return null;
			}
			Optional<?> pose = (Optional<?>) worldPose.invoke(null, id);
			if (pose.isEmpty()) {
				return null;
			}
			Object[] transforms = (Object[]) pose.get();
			Box[] box = new Box[NAMES.length];
			for (int i = 0; i < NAMES.length; i++) {
				box[i] = box(transforms, i);
			}
			if (box[TORSO] == null || box[HEAD] == null) {
				return null;
			}
			return map(rig, box);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			failed = true;
			return null;
		}
	}

	private static boolean init(Class<?> api) throws ReflectiveOperationException {
		Class<?> transform = Class.forName("com.raiiiden.ragdollified.RagdollTransform");
		Class<?> part = Class.forName("com.raiiiden.ragdollified.RagdollPart");
		worldPose = api.getMethod("worldPose", int.class);
		halfExtents = part.getMethod("getHalfExtents");
		partId = transform.getField("partId");
		position = transform.getField("position");
		rotation = transform.getField("rotation");
		partIndex = part.getField("index");
		Class<?> vector = Class.forName("javax.vecmath.Vector3f");
		Class<?> quat = Class.forName("javax.vecmath.Quat4f");
		vx = vector.getField("x");
		vy = vector.getField("y");
		vz = vector.getField("z");
		qx = quat.getField("x");
		qy = quat.getField("y");
		qz = quat.getField("z");
		qw = quat.getField("w");
		Object[] found = new Object[NAMES.length];
		for (int i = 0; i < NAMES.length; i++) {
			found[i] = part.getField(NAMES[i]).get(null);
		}
		parts = found;
		return true;
	}

	/** Part {@code which}'s box in the world, or null when Ragdollified has none of it (a limb that came off). */
	private static @Nullable Box box(Object[] transforms, int which) throws ReflectiveOperationException {
		int index = partIndex.getInt(parts[which]);
		for (Object t : transforms) {
			if (t == null || partId.getInt(t) != index) {
				continue;
			}
			Object p = position.get(t), q = rotation.get(t);
			double x = qx.getFloat(q), y = qy.getFloat(q), z = qz.getFloat(q), w = qw.getFloat(q);
			Vector3d axis = new Vector3d(2 * (x * y - w * z), 1 - 2 * (x * x + z * z), 2 * (y * z + w * x));
			Vector3d side = new Vector3d(1 - 2 * (y * y + z * z), 2 * (x * y + w * z), 2 * (x * z - w * y));
			double half = ((org.joml.Vector3f) halfExtents.invoke(parts[which])).y;
			return new Box(new Vector3d(vx.getFloat(p), vy.getFloat(p), vz.getFloat(p)), axis.normalize(), side.normalize(), half);
		}
		return null;
	}

	private static RagdollSnapshot map(RagdollRig rig, Box[] box) {
		Box torso = box[TORSO];
		// up: along the torso's long axis, toward the head
		Vector3d up = new Vector3d(torso.axis);
		if (up.dot(new Vector3d(box[HEAD].at).sub(torso.at)) < 0) {
			up.negate();
		}
		// right: along the torso's width, toward the right arm's side
		Vector3d right = new Vector3d(torso.side);
		double lean = 0;
		if (box[RIGHT_ARM] != null && box[LEFT_ARM] != null) {
			lean = right.dot(new Vector3d(box[RIGHT_ARM].at).sub(box[LEFT_ARM].at));
		} else if (box[RIGHT_ARM] != null) {
			lean = right.dot(new Vector3d(box[RIGHT_ARM].at).sub(torso.at));
		} else if (box[LEFT_ARM] != null) {
			lean = right.dot(new Vector3d(torso.at).sub(box[LEFT_ARM].at));
		} else if (box[RIGHT_LEG] != null && box[LEFT_LEG] != null) {
			lean = right.dot(new Vector3d(box[RIGHT_LEG].at).sub(box[LEFT_LEG].at));
		}
		if (lean < 0) {
			right.negate();
		}
		right.sub(new Vector3d(up).mul(right.dot(up))).normalize();
		// forward (our +Z at rest): up x right
		Vector3d forward = new Vector3d(up).cross(right);

		RagdollBody body = new RagdollBody(rig);
		double centre = TORSO_CENTRE * rig.height;
		for (int i : new int[] {RagdollRig.R_SHOULDER, RagdollRig.L_SHOULDER, RagdollRig.R_HIP, RagdollRig.L_HIP,
			RagdollRig.NECK, RagdollRig.CHEST}) {
			// our rest has its right toward -X
			Vector3d at = new Vector3d(torso.at).fma(-rig.restX[i], right).fma(rig.restY[i] - centre, up).fma(rig.restZ[i], forward);
			put(body, i, at);
		}
		limb(body, rig, box[RIGHT_ARM], RagdollRig.R_SHOULDER, RagdollRig.R_HAND, up);
		limb(body, rig, box[LEFT_ARM], RagdollRig.L_SHOULDER, RagdollRig.L_HAND, up);
		limb(body, rig, box[RIGHT_LEG], RagdollRig.R_HIP, RagdollRig.R_FOOT, up);
		limb(body, rig, box[LEFT_LEG], RagdollRig.L_HIP, RagdollRig.L_FOOT, up);
		// the head runs on from the neck toward the head box
		Vector3d neck = new Vector3d(body.x[RagdollRig.NECK], body.y[RagdollRig.NECK], body.z[RagdollRig.NECK]);
		Vector3d toward = new Vector3d(box[HEAD].at).sub(neck);
		if (toward.lengthSquared() < 1.0E-4) {
			toward.set(up);
		}
		put(body, RagdollRig.HEAD, neck.fma(length(rig, RagdollRig.NECK, RagdollRig.HEAD) / toward.length(), toward));
		body.stop();
		RagdollSnapshot made = RagdollSnapshot.of(body);
		return new RagdollSnapshot(made.shape(), made.width(), made.height(), made.x(), made.y(), made.z(), made.points(), true);
	}

	/** A limb from {@code root} to {@code tip}: out along its box's long axis, away from the torso; hanging if it has none. */
	private static void limb(RagdollBody body, RagdollRig rig, @Nullable Box box, int root, int tip, Vector3d up) {
		Vector3d from = new Vector3d(body.x[root], body.y[root], body.z[root]);
		Vector3d way = new Vector3d(up).negate();
		if (box != null) {
			Vector3d a = new Vector3d(box.at).fma(box.half, box.axis), b = new Vector3d(box.at).fma(-box.half, box.axis);
			// the end by the root is the near one
			way = a.distanceSquared(from) < b.distanceSquared(from) ? b.sub(a) : a.sub(b);
			if (way.lengthSquared() < 1.0E-6) {
				way.set(up).negate();
			}
		}
		way.normalize(length(rig, root, tip));
		put(body, tip, from.add(way));
	}

	private static double length(RagdollRig rig, int a, int b) {
		for (int k = 0; k < rig.stickA.length; k++) {
			if (rig.stickA[k] == a && rig.stickB[k] == b || rig.stickA[k] == b && rig.stickB[k] == a) {
				return rig.stickLength[k];
			}
		}
		return 0.3;
	}

	private static void put(RagdollBody body, int i, Vector3d at) {
		body.x[i] = at.x;
		body.y[i] = at.y;
		body.z[i] = at.z;
	}
}
