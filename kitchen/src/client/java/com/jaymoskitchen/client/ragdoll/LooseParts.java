package com.jaymoskitchen.client.ragdoll;

import com.jaymoskitchen.client.mixin.ModelPartAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * (0.5.40) A dead body's loose parts, whatever model it has: wings, tails, necks and heads, feet, ears, fins,
 * tentacles, and the legs of modded mobs whose parts the ragdoll doesn't know by name. Andy: "vultures and all other
 * dead mobs should ragdoll instead of falling limp" (they lay stiff, wings straight out).
 * <p>
 * Nothing is known about the model beforehand. Its biggest part is the trunk (it goes where the ragdoll's torso goes);
 * every other part that reaches out from where it hinges hangs toward the ground, as far as it can swing, and comes to
 * rest on it rather than through it. Each swings on a damped spring, so when the body tumbles, lands or is shoved, they
 * flop about and settle. Parts on those parts (a wing's tip, the head on a neck) hang a little further.
 * <p>
 * Run after the ragdoll has set the limbs it knows (see {@link RagdollPose#pose}); never on humanoids, whose every
 * limb the ragdoll drives.
 */
public final class LooseParts {
	/** How much of the way to hanging straight down a loose part goes (0 to 1). */
	private static final float DROOP = 0.85F;
	/** The most a part turns from how it stood (radians): parts on the trunk, and parts on those. */
	private static final float LIMIT = 1.25F, CHILD_LIMIT = 0.8F;
	/** The spring pulling each part toward where it hangs (a tick), and how much of its swing survives a tick. */
	private static final float STIFFNESS = 0.22F, DAMPING = 0.7F;
	/** A part (with everything on it) holding at least this share of the model's bulk is the trunk, not loose. */
	private static final float TRUNK_SHARE = 0.35F;
	/** A part only hangs if its middle lies at least this far from its hinge (a share of its half size). */
	private static final float LEVER = 0.35F;
	/** How deep loose parts go: on the trunk (0), and on those (1). */
	private static final int DEPTH = 2;
	/** A part lying on the ground keeps its far end this far above it (blocks). */
	private static final float GROUND_GAP = 0.02F;
	/** Parts that don't hang loose whatever their shape (worn, carried, or bony). */
	private static final String[] RIGID = {"saddle", "chest", "bag", "armor", "armour", "horn", "antler", "tusk", "beak",
		"bill", "eye", "mouth", "jaw", "nose", "teeth", "tooth", "item", "held", "shell", "hat", "jacket", "sleeve", "pants",
		"collar", "harness", "bridle", "rein", "crown", "udder", "crest", "body"};

	/** A loose part: the parts from the model's root down to its parent, where it reaches (its own frame, unit), how far. */
	private record Node(String path, String name, ModelPart part, ModelPart[] chain, int level, float dirX, float dirY,
		float dirZ, float length) {
	}

	/**
	 * (0.5.40) Set on a body drawn without the physics ragdoll (carried, falling from someone's arms, slung over a
	 * horse): where it's placed, as {@link RagdollPose#body} is, and what {@link #apply} needs besides.
	 */
	public static final RenderStateDataKey<Frame> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:loose_parts");

	public record Frame(Motion motion, Matrix4f body, float time, boolean grounded, float scale) {
	}

	private static final Map<ModelPart, List<Node>> PLANS = new WeakHashMap<>();
	private static final Map<Object, Motion> MOTIONS = new WeakHashMap<>();

	/** One body's swinging parts, by path: where each points now (model space) and how fast that's changing. */
	public static final class Motion {
		private final Map<String, float[]> parts = new java.util.HashMap<>();
	}

	private LooseParts() {
	}

	/** The swinging parts of the body {@code owner} (a corpse's ragdoll track), kept while it's alive. */
	public static Motion motion(Object owner) {
		return MOTIONS.computeIfAbsent(owner, o -> new Motion());
	}

	/**
	 * (0.5.40) On a just-animated model of a body drawn without the ragdoll (see {@link #KEY}): its loose parts hang
	 * from however its limp pose left them. Not humanoids, whose limbs that pose already hangs.
	 */
	static void apply(Model<?> model, EntityRenderState state) {
		Frame frame = state.getData(KEY);
		if (frame == null) {
			return;
		}
		ModelPart root = model.root();
		if (root.hasChild("right_arm") || root.hasChild("body") && root.getChild("body").hasChild("right_arm")) {
			return;
		}
		apply(root, frame.body, frame.scale, frame.motion, frame.time, frame.grounded, Set.of());
	}

	/**
	 * Hangs {@code root}'s loose parts. {@code body} is the ragdoll's placement (see {@link RagdollPose#body}),
	 * {@code scale} the body's drawn size, {@code time} in ticks; {@code grounded}: lying on the ground (y 0 in the
	 * placement's frame), so parts rest on it. Parts named in {@code skip} were posed already.
	 */
	static void apply(ModelPart root, Matrix4f body, float scale, Motion motion, float time, boolean grounded,
		Set<String> skip) {
		List<Node> nodes = PLANS.computeIfAbsent(root, LooseParts::plan);
		if (nodes.isEmpty()) {
			return;
		}
		// gravity in the model's space: back through the ragdoll's turn, then the model's flip (y and z)
		Vector3f g = new Matrix3f(body).transpose().transform(new Vector3f(0.0F, -1.0F, 0.0F));
		g.set(g.x, -g.y, -g.z).normalize();
		// model space (pixels) to the placement's frame (blocks), as LivingEntityRenderer draws a standing model
		Matrix4f toWorld = new Matrix4f(body).scale(scale).scale(1.0F, -1.0F, -1.0F).translate(0.0F, -1.501F, 0.0F)
			.scale(1.0F / 16.0F);
		Matrix4f chainMat = new Matrix4f();
		Quaternionf chainTurn = new Quaternionf();
		Quaternionf partTurn = new Quaternionf();
		Vector3f pivot = new Vector3f(), rest = new Vector3f(), target = new Vector3f(), tip = new Vector3f();
		for (Node node : nodes) {
			if (skip.contains(node.name) || !node.part.visible) {
				continue;
			}
			// the parent's place and turn in model space, as drawn (parents earlier in the list are already hung)
			chainMat.identity();
			chainTurn.identity();
			for (ModelPart p : node.chain) {
				chainMat.translate(p.x, p.y, p.z).rotate(partTurn.rotationZYX(p.zRot, p.yRot, p.xRot));
				chainTurn.mul(partTurn);
			}
			ModelPart part = node.part;
			chainMat.transformPosition(pivot.set(part.x, part.y, part.z));
			Quaternionf own = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
			Quaternionf standing = new Quaternionf(chainTurn).mul(own);
			standing.transform(rest.set(node.dirX, node.dirY, node.dirZ));
			float limit = node.level == 0 ? LIMIT : CHILD_LIMIT;
			float full = Math.min(angle(rest, g) * DROOP, limit);
			// as far toward hanging as it goes without its far end sinking into the ground; turned up off it if need be
			float swing = full;
			if (grounded && below(rest, g, full, pivot, node.length, toWorld, tip)) {
				if (below(rest, g, -limit, pivot, node.length, toWorld, tip)) {
					swing = -limit;
				} else {
					float lo = -limit, hi = full;
					for (int i = 0; i < 8; i++) {
						float mid = (lo + hi) * 0.5F;
						if (below(rest, g, mid, pivot, node.length, toWorld, tip)) {
							hi = mid;
						} else {
							lo = mid;
						}
					}
					swing = lo;
				}
			}
			turnToward(rest, g, swing, target);
			float[] s = step(motion, node.path, rest, target, time);
			Vector3f now = new Vector3f(s[0], s[1], s[2]);
			if (grounded && now.dot(target) < 0.9999F && sinks(now, pivot, node.length, toWorld, tip)) {
				// swung down into the ground: it stops where it lies
				now.set(target);
				s[0] = target.x;
				s[1] = target.y;
				s[2] = target.z;
				s[3] = s[4] = s[5] = 0.0F;
			}
			// the turn from standing to now, in model space, put into the part's own rotation
			Quaternionf bend = new Quaternionf().rotationTo(rest, now);
			Quaternionf local = new Quaternionf(chainTurn).conjugate().mul(bend).mul(standing);
			Vector3f euler = local.getEulerAnglesZYX(new Vector3f());
			part.xRot = euler.x;
			part.yRot = euler.y;
			part.zRot = euler.z;
		}
	}

	/** Steps one part's swing toward {@code target} (once a frame, however many models draw it): x, y, z, speed x, y, z. */
	private static float[] step(Motion motion, String path, Vector3f rest, Vector3f target, float time) {
		float[] s = motion.parts.get(path);
		if (s == null) {
			// fresh: it starts from how the body stood, and falls
			s = new float[] {rest.x, rest.y, rest.z, 0.0F, 0.0F, 0.0F, time};
			motion.parts.put(path, s);
			return s;
		}
		float dt = Math.min(Math.max(time - s[6], 0.0F), 2.0F);
		if (dt <= 0.0F) {
			return s;
		}
		s[6] = time;
		float keep = (float) Math.pow(DAMPING, dt);
		for (int k = 0; k < 3; k++) {
			float want = k == 0 ? target.x : k == 1 ? target.y : target.z;
			s[3 + k] = (s[3 + k] + (want - s[k]) * STIFFNESS * dt) * keep;
			s[k] += s[3 + k] * dt;
		}
		float len = (float) Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
		if (len < 1.0E-4F || Float.isNaN(len)) {
			s[0] = target.x;
			s[1] = target.y;
			s[2] = target.z;
			s[3] = s[4] = s[5] = 0.0F;
		} else {
			s[0] /= len;
			s[1] /= len;
			s[2] /= len;
		}
		return s;
	}

	/** Whether the part, swung {@code swing} radians from {@code rest} toward {@code g}, reaches below the ground. */
	private static boolean below(Vector3f rest, Vector3f g, float swing, Vector3f pivot, float length, Matrix4f toWorld,
		Vector3f tip) {
		turnToward(rest, g, swing, tip);
		return sinks(tip, pivot, length, toWorld, tip);
	}

	/** Whether a part pointing {@code dir} (model space) from {@code pivot} reaches below the ground. */
	private static boolean sinks(Vector3f dir, Vector3f pivot, float length, Matrix4f toWorld, Vector3f tip) {
		tip.set(dir).mul(length).add(pivot);
		toWorld.transformPosition(tip);
		return tip.y < GROUND_GAP;
	}

	/** {@code from} turned {@code swing} radians toward {@code to} (away, if negative), into {@code out}. */
	private static void turnToward(Vector3f from, Vector3f to, float swing, Vector3f out) {
		Vector3f axis = new Vector3f(from).cross(to);
		if (axis.lengthSquared() < 1.0E-8F) {
			if (from.dot(to) > 0.0F) {
				out.set(from);
				return;
			}
			// straight up: any way down will do
			axis.set(Math.abs(from.x) < 0.9F ? 1.0F : 0.0F, Math.abs(from.x) < 0.9F ? 0.0F : 1.0F, 0.0F).cross(from);
		}
		axis.normalize();
		new Quaternionf().rotationAxis(swing, axis).transform(out.set(from));
	}

	private static float angle(Vector3f a, Vector3f b) {
		return (float) Math.acos(Math.max(-1.0F, Math.min(1.0F, a.dot(b))));
	}

	/** Works out a model's loose parts, parents before their children. */
	private static List<Node> plan(ModelPart root) {
		List<Node> out = new ArrayList<>();
		float total = bulk(root);
		if (total > 0.0F) {
			walk(root, "", new ModelPart[] {root}, 0, total, out);
		}
		return out;
	}

	private static void walk(ModelPart part, String path, ModelPart[] chain, int level, float total, List<Node> out) {
		for (Map.Entry<String, ModelPart> e : children(part).entrySet()) {
			String name = e.getKey();
			ModelPart child = e.getValue();
			if (rigid(name)) {
				// the trunk by name: its parts are the loose ones
				if (name.equals("body") && level == 0) {
					walk(child, path + "/" + name, extend(chain, child), 0, total, out);
				}
				continue;
			}
			String childPath = path + "/" + name;
			ModelPart[] next = extend(chain, child);
			if (level == 0 && bulk(child) >= total * TRUNK_SHARE) {
				// the trunk (or a wrapper round the whole body): its parts are the loose ones
				walk(child, childPath, next, 0, total, out);
				continue;
			}
			Node node = node(childPath, name, child, chain, level);
			if (node != null) {
				out.add(node);
			}
			if (level + 1 < DEPTH || runsOn(name, child)) {
				// (0.5.47) a run of the same part (tail, tail2, tail3...) hangs to its end, every link loose
				walk(child, childPath, next, level + 1, total, out);
			}
		}
	}

	/** A loose part, if it reaches out from its hinge; null for one centred on it (a lump, a plate) or with nothing drawn. */
	private static Node node(String path, String name, ModelPart part, ModelPart[] chain, int level) {
		List<ModelPart.Cube> cubes = cubes(part);
		if (cubes.isEmpty()) {
			return null;
		}
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (ModelPart.Cube c : cubes) {
			minX = Math.min(minX, c.minX);
			minY = Math.min(minY, c.minY);
			minZ = Math.min(minZ, c.minZ);
			maxX = Math.max(maxX, c.maxX);
			maxY = Math.max(maxY, c.maxY);
			maxZ = Math.max(maxZ, c.maxZ);
		}
		float cx = (minX + maxX) * 0.5F, cy = (minY + maxY) * 0.5F, cz = (minZ + maxZ) * 0.5F;
		float hx = (maxX - minX) * 0.5F, hy = (maxY - minY) * 0.5F, hz = (maxZ - minZ) * 0.5F;
		float half = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
		float reach = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
		if (half < 0.5F || reach < half * LEVER) {
			return null;
		}
		float dx = cx / reach, dy = cy / reach, dz = cz / reach;
		// how far its far end reaches along that way
		float length = 0.0F;
		for (int i = 0; i < 8; i++) {
			float x = (i & 1) == 0 ? minX : maxX, y = (i & 2) == 0 ? minY : maxY, z = (i & 4) == 0 ? minZ : maxZ;
			length = Math.max(length, x * dx + y * dy + z * dz);
		}
		return new Node(path, name, part, chain, level, dx, dy, dz, length);
	}

	/** (0.5.47) Whether {@code part} (called {@code name}) carries the next of its run: "tail" holding "tail2". */
	private static boolean runsOn(String name, ModelPart part) {
		String stem = name.toLowerCase(Locale.ROOT).replaceAll("[0-9_]+$", "");
		if (stem.isEmpty()) {
			return false;
		}
		for (String child : children(part).keySet()) {
			if (child.toLowerCase(Locale.ROOT).startsWith(stem)) {
				return true;
			}
		}
		return false;
	}

	private static ModelPart[] extend(ModelPart[] chain, ModelPart part) {
		ModelPart[] next = java.util.Arrays.copyOf(chain, chain.length + 1);
		next[chain.length] = part;
		return next;
	}

	private static boolean rigid(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		for (String r : RIGID) {
			if (n.contains(r)) {
				// "body" only by itself, not "body_tail" or "upper_body_fin"
				if (!r.equals("body") || n.equals("body")) {
					return true;
				}
			}
		}
		return false;
	}

	/** How much is drawn in a part and everything on it (cubic pixels). */
	private static float bulk(ModelPart part) {
		float sum = 0.0F;
		for (ModelPart.Cube c : cubes(part)) {
			sum += (c.maxX - c.minX) * (c.maxY - c.minY) * (c.maxZ - c.minZ);
		}
		for (ModelPart child : children(part).values()) {
			sum += bulk(child);
		}
		return sum;
	}

	private static Map<String, ModelPart> children(ModelPart part) {
		return ((ModelPartAccessor) (Object) part).jaymoskitchen$children();
	}

	private static List<ModelPart.Cube> cubes(ModelPart part) {
		return ((ModelPartAccessor) (Object) part).jaymoskitchen$cubes();
	}
}
