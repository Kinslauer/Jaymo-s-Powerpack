package com.jaymospowers.client;

import com.jaymospowers.client.mixin.ModelPartCubesAccessor;
import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * (0.19.45) Andy: "is there any way to stop drawing the top of the torso in first person? there's no reason we should ever
 * see that, it's meant to show the body when you look down". First-person Model draws your whole body from just under the
 * camera, so the neck, collar and shoulder tops (and whatever is worn there: shirts, MCA clothes, chestplates) filled the
 * bottom of the screen, worst while riding. For the one draw of your own body in first person, every humanoid model drawn
 * for it (skin, clothes, armour layers) has its torso cut level at {@link #BODY_CUT} and its arms at the same height, the
 * cut capped with the top's own texture so looking down shows a closed waist, not a hollow box. Put back once drawn
 * ({@link #restore}). Boxes are swapped the way {@link KneeBend} swaps them, and a bent elbow still bends the cut arm.
 */
public final class FirstPersonTrim {
	/** The torso is drawn from this far down from the neck (pixels; a torso is 12). Raise it to show more of the chest. */
	static final float BODY_CUT = 6.0F;
	/** An arm hangs from 2 pixels down the torso, so its cut sits this far down its own part to line up with the torso's. */
	static final float ARM_CUT = BODY_CUT - 2.0F;
	private static final float EPS = 0.001F;

	/** Parts drawn cut just now → the boxes they had. */
	private static final Map<ModelPart, List<ModelPart.Cube>> SWAPPED = new IdentityHashMap<>();
	/** Parts hidden just now (pieces hung above the cut, like a chest ornament) → whether they were showing. */
	private static final Map<ModelPart, Boolean> HIDDEN = new IdentityHashMap<>();
	/** Each box's cut copy at each cut height, made once; a box wholly below the cut maps to itself, one wholly above to an empty box. */
	private static final Map<ModelPart.Cube, ModelPart.Cube> AT_BODY = new WeakHashMap<>();
	private static final Map<ModelPart.Cube, ModelPart.Cube> AT_ARM = new WeakHashMap<>();

	private FirstPersonTrim() {
	}

	/** Right before {@code model} is drawn: if it's your own body in first person, cut its top off until {@link #restore}. */
	public static void apply(Model<?> model, Object renderState) {
		if (!(model instanceof HumanoidModel<?> h) || !FirstPersonCompat.isCameraBody(renderState)) {
			return;
		}
		cut(h.body, BODY_CUT, AT_BODY);
		cut(h.rightArm, ARM_CUT, AT_ARM);
		cut(h.leftArm, ARM_CUT, AT_ARM);
	}

	private static void cut(ModelPart root, float at, Map<ModelPart.Cube, ModelPart.Cube> cache) {
		if (!root.visible || root.skipDraw) {
			return;
		}
		for (ModelPart part : root.getAllParts()) {
			if (part != root && (part.x != 0.0F || part.y != 0.0F || part.z != 0.0F || part.xRot != 0.0F || part.yRot != 0.0F
				|| part.zRot != 0.0F)) {
				// a piece hung somewhere else on the part (only its pivot is known): gone if it hangs above the cut
				if (part.y < at && part.visible && !HIDDEN.containsKey(part)) {
					HIDDEN.put(part, Boolean.TRUE);
					part.visible = false;
				}
				continue;
			}
			ModelPartCubesAccessor access = (ModelPartCubesAccessor) (Object) part;
			List<ModelPart.Cube> cubes = access.jaymospowers$cubes();
			if (cubes.isEmpty() || SWAPPED.containsKey(part)) {
				continue;
			}
			List<ModelPart.Cube> cut = new ArrayList<>(cubes.size());
			boolean changed = false;
			for (ModelPart.Cube cube : cubes) {
				ModelPart.Cube c = cache.computeIfAbsent(cube, k -> Cut.of(k, at));
				cut.add(c);
				changed |= c != cube;
			}
			if (changed) {
				SWAPPED.put(part, cubes);
				access.jaymospowers$setCubes(cut);
			}
		}
	}

	/** Every cut part back to its own boxes, and hidden pieces shown again. Call after {@link KneeBend#restore}. */
	public static void restore() {
		if (!SWAPPED.isEmpty()) {
			SWAPPED.forEach((part, cubes) -> ((ModelPartCubesAccessor) (Object) part).jaymospowers$setCubes(cubes));
			SWAPPED.clear();
		}
		if (!HIDDEN.isEmpty()) {
			HIDDEN.forEach((part, was) -> part.visible = was);
			HIDDEN.clear();
		}
	}

	/**
	 * A box with everything above the cut taken off: sides shortened (their texture cropped with them), the top face lowered
	 * onto the cut. Its {@code polygons} hold the cut faces (a face lost wholly is left as a point) so {@link KneeBend} can
	 * still bend it; it draws itself, as other mods' fast paths (Sodium's) only know the box it was built with.
	 */
	static final class Cut extends ModelPart.Cube {
		private final ModelPart.Polygon[] kept;

		private Cut(ModelPart.Cube cube, ModelPart.Polygon[] faces, ModelPart.Polygon[] kept) {
			super(0, 0, cube.minX, cube.minY, cube.minZ, cube.maxX - cube.minX, cube.maxY - cube.minY, cube.maxZ - cube.minZ, 0.0F, 0.0F, 0.0F,
				false, 64.0F, 64.0F, firstFaces(faces.length));
			System.arraycopy(faces, 0, this.polygons, 0, Math.min(faces.length, this.polygons.length));
			this.kept = kept;
		}

		/** {@code cube} cut at {@code at}: the cube itself when it's all below, an empty box when it's all above. */
		static ModelPart.Cube of(ModelPart.Cube cube, float at) {
			float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
			for (ModelPart.Polygon polygon : cube.polygons) {
				for (ModelPart.Vertex v : polygon.vertices()) {
					lo = Math.min(lo, v.y());
					hi = Math.max(hi, v.y());
				}
			}
			if (cube.polygons.length == 0 || lo >= at - EPS) {
				return cube;
			}
			if (cube.polygons.length > Direction.values().length) {
				return cube;
			}
			if (hi <= at + EPS) {
				return new Cut(cube, new ModelPart.Polygon[0], new ModelPart.Polygon[0]);
			}
			ModelPart.Polygon[] faces = new ModelPart.Polygon[cube.polygons.length];
			List<ModelPart.Polygon> kept = new ArrayList<>(faces.length);
			for (int i = 0; i < faces.length; i++) {
				ModelPart.Polygon face = cutFace(cube.polygons[i], at);
				if (face == null) {
					ModelPart.Vertex p = cube.polygons[i].vertices()[0];
					ModelPart.Vertex point = new ModelPart.Vertex(p.x(), at, p.z(), p.u(), p.v());
					ModelPart.Vertex[] none = new ModelPart.Vertex[cube.polygons[i].vertices().length];
					java.util.Arrays.fill(none, point);
					faces[i] = new ModelPart.Polygon(none, cube.polygons[i].normal());
				} else {
					faces[i] = face;
					kept.add(face);
				}
			}
			return new Cut(cube, faces, kept.toArray(new ModelPart.Polygon[0]));
		}

		/** One face cut at {@code at}, or null if none of it is left. */
		private static ModelPart.Polygon cutFace(ModelPart.Polygon polygon, float at) {
			ModelPart.Vertex[] v = polygon.vertices();
			boolean above = true, below = true;
			float y0 = v[0].y();
			boolean flat = true;
			for (ModelPart.Vertex vertex : v) {
				above &= vertex.y() <= at + EPS;
				below &= vertex.y() >= at - EPS;
				flat &= Math.abs(vertex.y() - y0) < EPS;
			}
			if (below) {
				return polygon;
			}
			if (above) {
				if (!flat) {
					return null;
				}
				// the top: lowered onto the cut, closing it
				ModelPart.Vertex[] cap = new ModelPart.Vertex[v.length];
				for (int i = 0; i < v.length; i++) {
					cap[i] = new ModelPart.Vertex(v[i].x(), at, v[i].z(), v[i].u(), v[i].v());
				}
				return new ModelPart.Polygon(cap, polygon.normal());
			}
			// a side across the cut: each corner above it slides down its edge to the cut
			ModelPart.Vertex[] side = new ModelPart.Vertex[v.length];
			for (int i = 0; i < v.length; i++) {
				if (v[i].y() >= at - EPS) {
					side[i] = v[i];
					continue;
				}
				ModelPart.Vertex partner = null;
				for (ModelPart.Vertex o : v) {
					if (o != v[i] && Math.abs(o.x() - v[i].x()) < EPS && Math.abs(o.z() - v[i].z()) < EPS && o.y() > at) {
						partner = o;
						break;
					}
				}
				if (partner == null) {
					// not a plain box side: leave it whole
					return polygon;
				}
				float f = (at - v[i].y()) / (partner.y() - v[i].y());
				side[i] = new ModelPart.Vertex(v[i].x(), at, v[i].z(), Mth.lerp(f, v[i].u(), partner.u()), Mth.lerp(f, v[i].v(), partner.v()));
			}
			return new ModelPart.Polygon(side, polygon.normal());
		}

		private static EnumSet<Direction> firstFaces(int n) {
			EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
			for (Direction d : Direction.values()) {
				if (faces.size() >= n) {
					break;
				}
				faces.add(d);
			}
			return faces;
		}

		@Override
		public void compile(PoseStack.Pose pose, VertexConsumer out, int light, int overlay, int color) {
			Matrix4f matrix = pose.pose();
			Vector3f normal = new Vector3f();
			Vector3f at = new Vector3f();
			for (ModelPart.Polygon polygon : this.kept) {
				pose.transformNormal(polygon.normal(), normal);
				for (ModelPart.Vertex vertex : polygon.vertices()) {
					matrix.transformPosition(vertex.x() / 16.0F, vertex.y() / 16.0F, vertex.z() / 16.0F, at);
					out.addVertex(at.x(), at.y(), at.z(), color, vertex.u(), vertex.v(), overlay, light, normal.x(), normal.y(), normal.z());
				}
			}
		}
	}
}
