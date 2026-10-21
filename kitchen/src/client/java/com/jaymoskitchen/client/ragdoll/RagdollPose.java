package com.jaymoskitchen.client.ragdoll;

import com.jaymoskitchen.ragdoll.RagdollRig;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Matrix4f;

/**
 * (0.5.1) A ragdoll worked out for one frame, ready to draw: where the whole body goes ({@link #body}, from the
 * standing model's place to the ragdoll's torso) and how each limb turns from there. Put on the body's render state by
 * whoever draws it (a corpse's renderer, or the player renderer for a limp player) and applied, by part name, right
 * after the model animates (see {@code ModelFeatureRendererMixin}), last of all poses.
 * <p>
 * The game draws a model standing at its feet, facing south once its renderer has turned it (bodyRot 0). Here,
 * world = T(torso now) * R * T(-torso standing), R turning the standing torso's frame into the ragdoll's.
 */
public final class RagdollPose {
	public static final RenderStateDataKey<RagdollPose> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:ragdoll_pose");

	/** The limbs each shape drives: model part, the points it runs between, and which way the part points at rest. */
	private record Limb(String part, int root, int tip, float restX, float restY, float restZ) {
	}

	private static final Limb[] HUMANOID = {
		new Limb("head", RagdollRig.NECK, RagdollRig.HEAD, 0, -1, 0),
		new Limb("right_arm", RagdollRig.R_SHOULDER, RagdollRig.R_HAND, 0, 1, 0),
		new Limb("left_arm", RagdollRig.L_SHOULDER, RagdollRig.L_HAND, 0, 1, 0),
		new Limb("right_leg", RagdollRig.R_HIP, RagdollRig.R_FOOT, 0, 1, 0),
		new Limb("left_leg", RagdollRig.L_HIP, RagdollRig.L_FOOT, 0, 1, 0)
	};
	private static final Limb[] QUADRUPED = {
		new Limb("head", RagdollRig.SNOUT, RagdollRig.MUZZLE, 0, 0, -1),
		new Limb("right_front_leg", RagdollRig.FRONT_RIGHT, RagdollRig.FR_FOOT, 0, 1, 0),
		new Limb("left_front_leg", RagdollRig.FRONT_LEFT, RagdollRig.FL_FOOT, 0, 1, 0),
		new Limb("right_hind_leg", RagdollRig.BACK_RIGHT, RagdollRig.BR_FOOT, 0, 1, 0),
		new Limb("left_hind_leg", RagdollRig.BACK_LEFT, RagdollRig.BL_FOOT, 0, 1, 0)
	};
	private static final Limb[] NONE = {};
	/** Overlay parts some models keep beside (not inside) the part they cover: they turn with it. */
	private static final String[][] OVERLAYS = {{"hat", "head"}, {"right_sleeve", "right_arm"}, {"left_sleeve", "left_arm"},
		{"right_pants", "right_leg"}, {"left_pants", "left_leg"}, {"jacket", "body"}};

	/** World transform from the standing model to the ragdoll, about the entity's drawn spot (blocks). */
	public final Matrix4f body;
	private final Limb[] limbs;
	/** Per limb: x, y, z rotation (radians, as ModelPart takes them). */
	private final float[] angles;
	public final RagdollRig.Shape shape;

	private RagdollPose(Matrix4f body, Limb[] limbs, float[] angles, RagdollRig.Shape shape) {
		this.body = body;
		this.limbs = limbs;
		this.angles = angles;
		this.shape = shape;
	}

	/**
	 * Works the pose out from a body's points this frame ({@code points}: x, y, z each, in the world), drawn about
	 * {@code (ox, oy, oz)} (where the renderer stands the entity this frame).
	 */
	public static RagdollPose of(RagdollRig rig, double[] points, double ox, double oy, double oz) {
		int n = rig.count;
		float[] x = new float[n], y = new float[n], z = new float[n];
		for (int i = 0; i < n; i++) {
			x[i] = (float) (points[i * 3] - ox);
			y[i] = (float) (points[i * 3 + 1] - oy);
			z[i] = (float) (points[i * 3 + 2] - oz);
		}
		float[] now = new float[9], rest = new float[9];
		RagdollRig.frame(x, y, z, now);
		rig.restFrame(rest);
		// R = now^T * rest (frames as rows): rest-world vectors to world vectors
		float[] r = new float[9];
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				float sum = 0;
				for (int k = 0; k < 3; k++) {
					sum += now[k * 3 + row] * rest[k * 3 + col];
				}
				r[row * 3 + col] = sum;
			}
		}
		// the torso's middle now and standing
		float mx = (x[0] + x[1] + x[2] + x[3]) * 0.25F, my = (y[0] + y[1] + y[2] + y[3]) * 0.25F,
			mz = (z[0] + z[1] + z[2] + z[3]) * 0.25F;
		float sx = (rig.restX[0] + rig.restX[1] + rig.restX[2] + rig.restX[3]) * 0.25F;
		float sy = (rig.restY[0] + rig.restY[1] + rig.restY[2] + rig.restY[3]) * 0.25F;
		float sz = (rig.restZ[0] + rig.restZ[1] + rig.restZ[2] + rig.restZ[3]) * 0.25F;
		Matrix4f body = new Matrix4f();
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				body.setRowColumn(row, col, r[row * 3 + col]);
			}
		}
		// translation: m - R * s
		body.setRowColumn(0, 3, mx - (r[0] * sx + r[1] * sy + r[2] * sz));
		body.setRowColumn(1, 3, my - (r[3] * sx + r[4] * sy + r[5] * sz));
		body.setRowColumn(2, 3, mz - (r[6] * sx + r[7] * sy + r[8] * sz));

		Limb[] limbs = switch (rig.shape) {
			case HUMANOID -> HUMANOID;
			case QUADRUPED -> QUADRUPED;
			case BOX -> NONE;
		};
		float[] angles = new float[limbs.length * 3];
		float[] out = new float[3];
		for (int l = 0; l < limbs.length; l++) {
			Limb limb = limbs[l];
			float ux = x[limb.tip] - x[limb.root], uy = y[limb.tip] - y[limb.root], uz = z[limb.tip] - z[limb.root];
			// back into the standing body's frame (R transposed), then into the model's (y and z flipped)
			float rx = r[0] * ux + r[3] * uy + r[6] * uz;
			float ry = r[1] * ux + r[4] * uy + r[7] * uz;
			float rz = r[2] * ux + r[5] * uy + r[8] * uz;
			eulerTo(limb.restX, limb.restY, limb.restZ, rx, -ry, -rz, out);
			angles[l * 3] = out[0];
			angles[l * 3 + 1] = out[1];
			angles[l * 3 + 2] = out[2];
		}
		return new RagdollPose(body, limbs, angles, rig.shape);
	}

	/**
	 * The ModelPart rotation (x, y, z: applied z then y then x outermost first, as {@code rotationZYX}) that turns
	 * direction {@code a} into direction {@code d}, the shortest way.
	 */
	static void eulerTo(float ax, float ay, float az, float dx, float dy, float dz, float[] out) {
		float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dl < 1.0E-6F) {
			out[0] = out[1] = out[2] = 0;
			return;
		}
		dx /= dl;
		dy /= dl;
		dz /= dl;
		// quaternion from a to d: (a x d, 1 + a.d), normalised
		float cx = ay * dz - az * dy, cy = az * dx - ax * dz, cz = ax * dy - ay * dx;
		float w = 1 + ax * dx + ay * dy + az * dz;
		if (w < 1.0E-5F) {
			// opposite: half a turn about any axis square to a
			if (Math.abs(ax) < 0.9F) {
				cx = 0;
				cy = -az;
				cz = ay;
			} else {
				cx = az;
				cy = 0;
				cz = -ax;
			}
			w = 0;
		}
		float ql = (float) Math.sqrt(cx * cx + cy * cy + cz * cz + w * w);
		float qx = cx / ql, qy = cy / ql, qz = cz / ql, qw = w / ql;
		// rotation matrix rows we need: M = Rz * Ry * Rx gives m20 = -sin y, m21 = cos y sin x, m22 = cos y cos x,
		// m10 = sin z cos y, m00 = cos z cos y
		float m00 = 1 - 2 * (qy * qy + qz * qz);
		float m10 = 2 * (qx * qy + qz * qw);
		float m20 = 2 * (qx * qz - qy * qw);
		float m21 = 2 * (qy * qz + qx * qw);
		float m22 = 1 - 2 * (qx * qx + qy * qy);
		out[1] = (float) Math.asin(Math.max(-1.0F, Math.min(1.0F, -m20)));
		out[0] = (float) Math.atan2(m21, m22);
		out[2] = (float) Math.atan2(m10, m00);
	}

	/** On a just-animated model (see {@code ModelFeatureRendererMixin}): turns each limb to the ragdoll's. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof EntityRenderState renderState)) {
			return;
		}
		RagdollPose pose = renderState.getData(KEY);
		if (pose != null) {
			pose.pose(model.root());
		}
	}

	private void pose(ModelPart root) {
		if (root.hasChild("body")) {
			// the torso is the whole model's turn: its own animation (crouching, breathing) is undone
			ModelPart body = root.getChild("body");
			if (shape == RagdollRig.Shape.HUMANOID) {
				body.xRot = body.yRot = body.zRot = 0;
			}
		}
		for (int l = 0; l < limbs.length; l++) {
			ModelPart part = find(root, limbs[l].part);
			if (part == null) {
				continue;
			}
			part.xRot = angles[l * 3];
			part.yRot = angles[l * 3 + 1];
			part.zRot = angles[l * 3 + 2];
		}
		for (String[] overlay : OVERLAYS) {
			if (root.hasChild(overlay[0]) && root.hasChild(overlay[1])) {
				root.getChild(overlay[0]).loadPose(root.getChild(overlay[1]).storePose());
			}
		}
	}

	/** A part on the model's root, or on its body (some models hang their legs from the body). */
	private static ModelPart find(ModelPart root, String name) {
		if (root.hasChild(name)) {
			return root.getChild(name);
		}
		if (root.hasChild("body")) {
			ModelPart body = root.getChild("body");
			if (body.hasChild(name)) {
				return body.getChild(name);
			}
		}
		return null;
	}
}
