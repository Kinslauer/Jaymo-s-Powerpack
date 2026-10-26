package com.jaymospowers.client;

import com.jaymospowers.client.mixin.ModelPartCubesAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * (0.17.15) Andy: "crouching looks like a split right now, the back leg is fine, but the front leg should bend at the knee,
 * and stay bent while firing". A leg is one straight box, so for the one draw of a model on one knee its boxes are swapped
 * for bent copies ({@link #bend}, put back by {@link #restore} once that model is drawn): every side cut in two at
 * {@link #KNEE}, the shin swung back about the middle of the leg, and the cut itself tilted halfway so the two halves meet.
 * The texture is cut with it, so skin, trousers, armour and MCA bodies all bend the same way. Boxes that are drawn by
 * other mods' fast paths (Sodium's) are drawn by us while bent, then handed back.
 */
public final class KneeBend {
	/** The knee: this far down the leg from the hip (pixels; half of a 12 pixel leg). */
	static final float KNEE = 6.0F;
	/** The cut at the knee tilts at most this far (radians), so a very deep bend doesn't stretch it out of shape. */
	static final float MAX_HALF = 0.8F;
	private static final float EPS = 0.001F;

	/** Parts drawn bent just now → the boxes they had. */
	private static final Map<ModelPart, List<ModelPart.Cube>> SWAPPED = new IdentityHashMap<>();
	/** Each box's bent copy, made once. */
	private static final Map<ModelPart.Cube, Bent> BENT = new WeakHashMap<>();

	private KneeBend() {
	}

	/**
	 * Draw {@code leg} (and what hangs straight off it, like the trouser layer) bent at the knee by {@code angle} radians,
	 * the shin swinging back, until {@link #restore}. Only call it right before the model is drawn.
	 */
	public static void bend(ModelPart leg, float angle) {
		if (Math.abs(angle) < EPS) {
			return;
		}
		for (ModelPart part : leg.getAllParts()) {
			if (part != leg && (part.x != 0.0F || part.y != 0.0F || part.z != 0.0F || part.xRot != 0.0F || part.yRot != 0.0F
				|| part.zRot != 0.0F)) {
				continue;
			}
			ModelPartCubesAccessor access = (ModelPartCubesAccessor) (Object) part;
			List<ModelPart.Cube> cubes = access.jaymospowers$cubes();
			if (cubes.isEmpty() || SWAPPED.containsKey(part)) {
				continue;
			}
			List<ModelPart.Cube> bent = new ArrayList<>(cubes.size());
			for (ModelPart.Cube cube : cubes) {
				Bent b = cube instanceof Bent already ? already : BENT.computeIfAbsent(cube, Bent::new);
				b.angle = angle;
				bent.add(b);
			}
			SWAPPED.put(part, cubes);
			access.jaymospowers$setCubes(bent);
		}
	}

	/** Every bent part back to its own boxes. */
	public static void restore() {
		if (SWAPPED.isEmpty()) {
			return;
		}
		SWAPPED.forEach((part, cubes) -> ((ModelPartCubesAccessor) (Object) part).jaymospowers$setCubes(cubes));
		SWAPPED.clear();
	}

	/** A box drawn bent at the knee. */
	static final class Bent extends ModelPart.Cube {
		final ModelPart.Cube cube;
		float angle;

		Bent(ModelPart.Cube cube) {
			// (no faces of its own: it draws the original's)
			super(0, 0, cube.minX, cube.minY, cube.minZ, cube.maxX - cube.minX, cube.maxY - cube.minY, cube.maxZ - cube.minZ, 0.0F, 0.0F,
				0.0F, false, 64.0F, 64.0F, EnumSet.noneOf(Direction.class));
			this.cube = cube;
		}

		@Override
		public void compile(PoseStack.Pose pose, VertexConsumer out, int light, int overlay, int color) {
			if (this.cube.maxY <= KNEE + EPS) {
				// all thigh
				this.cube.compile(pose, out, light, overlay, color);
				return;
			}
			Knee knee = new Knee(this.angle, (this.cube.minZ + this.cube.maxZ) * 0.5F);
			boolean allShin = this.cube.minY >= KNEE - EPS;
			Vector3f normal = new Vector3f();
			Vector3f at = new Vector3f();
			ModelPart.Vertex[] quad = new ModelPart.Vertex[4];
			for (ModelPart.Polygon polygon : this.cube.polygons) {
				ModelPart.Vertex[] v = polygon.vertices();
				Vector3f n = new Vector3f(polygon.normal());
				boolean above = true, below = true;
				for (ModelPart.Vertex vertex : v) {
					above &= vertex.y() <= KNEE + EPS;
					below &= vertex.y() >= KNEE - EPS;
				}
				if (allShin || below && !above) {
					emit(pose, out, v, knee, 2, knee.turn(n), light, overlay, color, normal, at);
				} else if (above) {
					emit(pose, out, v, knee, 0, n, light, overlay, color, normal, at);
				} else if (!split(pose, out, v, n, knee, quad, light, overlay, color, normal, at)) {
					emit(pose, out, v, knee, 0, n, light, overlay, color, normal, at);
				}
			}
		}

		/** A side running past the knee, drawn as thigh and shin. False if it isn't a box side after all. */
		private static boolean split(PoseStack.Pose pose, VertexConsumer out, ModelPart.Vertex[] v, Vector3f n, Knee knee, ModelPart.Vertex[] quad,
			int light, int overlay, int color, Vector3f normal, Vector3f at) {
			if (v.length != 4) {
				return false;
			}
			ModelPart.Vertex[] mid = new ModelPart.Vertex[4];
			for (int i = 0; i < 4; i++) {
				ModelPart.Vertex partner = null;
				for (ModelPart.Vertex o : v) {
					if (o != v[i] && Math.abs(o.x() - v[i].x()) < EPS && Math.abs(o.z() - v[i].z()) < EPS && Math.abs(o.y() - v[i].y()) > EPS) {
						partner = o;
						break;
					}
				}
				if (partner == null) {
					return false;
				}
				ModelPart.Vertex top = v[i].y() < partner.y() ? v[i] : partner;
				ModelPart.Vertex bottom = top == v[i] ? partner : v[i];
				float f = (KNEE - top.y()) / (bottom.y() - top.y());
				mid[i] = new ModelPart.Vertex(Mth.lerp(f, top.x(), bottom.x()), KNEE, Mth.lerp(f, top.z(), bottom.z()), Mth.lerp(f, top.u(), bottom.u()),
					Mth.lerp(f, top.v(), bottom.v()));
			}
			// the thigh: the top corners as they are, the bottom ones cut at the knee
			for (int i = 0; i < 4; i++) {
				quad[i] = v[i].y() < KNEE ? v[i] : mid[i];
			}
			emit(pose, out, quad, knee, 0, n, light, overlay, color, normal, at);
			// the shin: from the cut down
			for (int i = 0; i < 4; i++) {
				quad[i] = v[i].y() > KNEE ? v[i] : mid[i];
			}
			emit(pose, out, quad, knee, 0, knee.turn(new Vector3f(n)), light, overlay, color, normal, at);
			return true;
		}

		/** {@code whole}: 2 swings every corner with the shin; otherwise corners past the knee swing, those on it tilt halfway. */
		private static void emit(PoseStack.Pose pose, VertexConsumer out, ModelPart.Vertex[] v, Knee knee, int whole, Vector3f n, int light, int overlay,
			int color, Vector3f normal, Vector3f at) {
			pose.transformNormal(n, normal);
			Matrix4f matrix = pose.pose();
			for (ModelPart.Vertex vertex : v) {
				knee.place(vertex, whole == 2, at);
				matrix.transformPosition(at.x() / 16.0F, at.y() / 16.0F, at.z() / 16.0F, at);
				out.addVertex(at.x(), at.y(), at.z(), color, vertex.u(), vertex.v(), overlay, light, normal.x(), normal.y(), normal.z());
			}
		}
	}

	/** The bend: the shin swung back by {@code angle} about the leg's middle at the knee. */
	private record Knee(float angle, float zc, float sin, float cos, float tilt) {
		Knee(float angle, float zc) {
			this(angle, zc, Mth.sin(angle), Mth.cos(angle), (float) Math.tan(Mth.clamp(angle * 0.5F, -MAX_HALF, MAX_HALF)));
		}

		/** A corner where it's drawn (in the leg's pixels). */
		void place(ModelPart.Vertex v, boolean shin, Vector3f at) {
			float dy = v.y() - KNEE, dz = v.z() - this.zc;
			if (!shin && dy < -EPS) {
				at.set(v.x(), v.y(), v.z());
			} else if (!shin && dy <= EPS) {
				// on the cut: tilted halfway, stretched so the leg keeps its depth
				at.set(v.x(), KNEE - dz * this.tilt, v.z());
			} else {
				at.set(v.x(), KNEE + dy * this.cos - dz * this.sin, this.zc + dy * this.sin + dz * this.cos);
			}
		}

		Vector3f turn(Vector3f n) {
			return n.set(n.x(), n.y() * this.cos - n.z() * this.sin, n.y() * this.sin + n.z() * this.cos);
		}
	}
}
