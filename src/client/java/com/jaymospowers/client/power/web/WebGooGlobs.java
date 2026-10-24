package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebSymbiote;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Living Armor's goo (Andy: "there should be more globs around the body moving constantly like an amorphous living
 * puddle of goo"): while the tendrils are out, {@link #GLOBS} living blobs of symbiote creep slowly over the torso (its
 * back, sides and belly), the shoulders, the arms and the legs, and one over the back of the skull, each riding its
 * body part's pose. They swell and shrink, and each keeps pinching off a little lobe of itself and taking it back (a
 * neck of goo between them that thins and snaps, then flows back together). Never over the face, the pecs (the front
 * of the chest's upper half) or the soles of the feet.
 *
 * <p>The first twelve each carry a tendril ({@link WebTendrilLayer}, tendril {@code i} out of glob {@code i}): it grows
 * from wherever its glob has crept to, out along the surface's outward way. A tendril on a job that needs its old root
 * (a strike, the weapon, a walker on a wall, a tether, a barrage stab, something held; everything in first person)
 * has its glob flow over to that root ({@link #ROOT_IN_TICKS}) and stay there until it's done, then creep back off
 * ({@link #ROOT_OUT_TICKS}). Struck while they're out ({@link WebSymbioteClient#FLAIL}), the globs heave and ripple.
 * Drawn with the tendrils' texture (in the strain's twin), from their layer.
 */
final class WebGooGlobs {
	/** Per tendril, for its model ({@link #STRIDE} floats each): its root (body space, pixels), its glob's outward way (body space), and how far it's on its glob (0 at its old root). */
	static final RenderStateDataKey<float[]> ROOTS = RenderStateDataKey.create(() -> "jaymospowers:web_goo_roots");
	static final int STRIDE = 7;

	/** How many globs: the first {@code TENDRIL_GLOBS} carry a tendril each (as many as WebTendrilLayer has), then one on the skull and one more on the torso. */
	static final int GLOBS = 14, TENDRIL_GLOBS = 12;
	/** A glob's half-width (pixels, between these, per glob) and how flat it lies on the body (its thickness, of that). */
	private static final float GLOB_MIN = 1.7F, GLOB_MAX = 2.7F, GLOB_FLAT = 0.55F;
	/** On the arms and legs a glob is this much smaller; on the big body every one this much bigger. */
	private static final float LIMB_SIZE = 0.8F, BULK_SIZE = 1.35F;
	/** The big body's muscles stand this far out of the player model's boxes (pixels): its globs sit on top of them. */
	private static final float BULK_OUT_TORSO = 3.0F, BULK_OUT_LIMB = 2.5F, BULK_OUT_HEAD = 1.0F;
	/** How fast they creep over the body (radians of their wander a tick; a full wander takes ~2 pi / this). */
	private static final float CREEP = 0.018F;
	/** Swelling and shrinking: how much (of its size) and how fast. */
	private static final float SWELL = 0.22F, SWELL_RATE = 0.07F;
	/** The surface heaving in slow waves (of its size), as the roaming blobs' bodies do (WebBlobRenderer). */
	private static final float WOBBLE = 0.12F;
	/**
	 * Splitting: each glob's lobe drifts out from {@code SPLIT_NEAR} to {@code SPLIT_FAR} of its size from the middle
	 * (merged, then pinched off) and back, at this rate; the lobe is {@code LOBE} of the glob's size, the neck between
	 * them {@code NECK} of it (thinning to nothing as it pulls apart).
	 */
	private static final float SPLIT_NEAR = 0.35F, SPLIT_FAR = 1.7F, SPLIT_RATE = 0.035F, LOBE = 0.6F, NECK = 0.5F;
	/** They're out (full size) by this share of the tendrils' growth. */
	private static final float GROWN_AT = 0.6F;
	/** A glob flows over to its tendril's old root in this many ticks when a job needs it, and creeps back off in this many. */
	private static final float ROOT_IN_TICKS = 3.0F, ROOT_OUT_TICKS = 14.0F;
	/** Struck (the hit flail): they heave this much (of their size), ripple this much harder, and churn this much faster; easing out over the last ticks. */
	private static final float FLAIL_HEAVE = 0.35F, FLAIL_RIPPLE = 0.22F, FLAIL_CHURN = 2.5F, FLAIL_EASE_TICKS = 8.0F;
	/** A tendril out of a glob on the front (the belly) is turned out to its side this much, so it never curls up past the face. */
	private static final float FRONT_OUT = 0.9F;
	/** Every tendril out of a glob leans at least this much (of its outward way) out to its own side. */
	private static final float MIN_OUT = 0.25F;
	/** The sphere each lobe is built from. */
	private static final int RINGS = 4, SEGMENTS = 7;

	/** Where on the body a glob wanders. */
	private static final int TORSO = 0, ARM = 1, LEG = 2, HEAD = 3, SHOULDER = 4;
	/** The torso (back round to the belly: 0 the middle of the back, pi the middle of the front) and how low on it the front starts being allowed (below the pecs). */
	private static final float TORSO_ROUND = 2.85F, BELLY_FROM = 2.0F, BELLY_TOP = 7.5F;

	/** Per wearer: its globs' layout (from its entity id) and how far each one has flowed to its tendril's old root. */
	private static final class Goo {
		final int[] region = new int[GLOBS];
		final float[] side = new float[GLOBS];
		/** Per glob: round mid, round half, height mid, height half, size, then phases. */
		final float[][] param = new float[GLOBS][12];
		final float[] rooted = new float[TENDRIL_GLOBS];
		long at;
		boolean fresh = true;
	}

	private static final Map<Integer, Goo> GOO = new HashMap<>();

	private WebGooGlobs() {
	}

	/**
	 * Lays out this wearer's globs (the pose stack at the layer's, the parent model posed), puts its tendrils' roots on
	 * its render state ({@link #ROOTS}) and draws them. {@code fixed}: each tendril's old root (WebTendrilLayer's
	 * {@code ROOTS}); {@code onJob}: which tendrils need it right now.
	 */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, PlayerModel model,
		WebSymbioteClient.Tendrils tendrils, float[][] fixed, boolean[] onJob, Identifier texture) {
		Goo goo = goo(state.id);
		float time = state.ageInTicks;
		// flowing to (or off) the old roots
		long now = System.nanoTime();
		float ticks = goo.fresh ? 0.0F : Math.min(0.25F, (now - goo.at) / 1.0E9F) * 20.0F;
		goo.at = now;
		for (int i = 0; i < TENDRIL_GLOBS; i++) {
			boolean job = i < onJob.length && onJob[i];
			if (goo.fresh) {
				goo.rooted[i] = job ? 1.0F : 0.0F;
			} else if (job) {
				goo.rooted[i] = Math.min(1.0F, goo.rooted[i] + ticks / ROOT_IN_TICKS);
			} else {
				goo.rooted[i] = Math.max(0.0F, goo.rooted[i] - ticks / ROOT_OUT_TICKS);
			}
		}
		goo.fresh = false;
		boolean slim = state.skin != null && state.skin.model() == PlayerModelType.SLIM;
		boolean bulk = tendrils.armor();
		Float flailAt = state.getData(WebSymbioteClient.FLAIL);
		float flail = flailAt == null ? 0.0F : 1.0F - smooth((flailAt - (WebSymbiote.FLAIL_TICKS - FLAIL_EASE_TICKS)) / FLAIL_EASE_TICKS);
		float grown = smooth(tendrils.growth() / GROWN_AT);

		Matrix4f body = matrix(model.body);
		Matrix4f toBody = new Matrix4f(body).invert();
		float[] roots = new float[TENDRIL_GLOBS * STRIDE];
		// each lobe: middle, then its three axes (unit) and their radii (blocks), then its wobble's phase and size
		java.util.List<float[]> lobes = new java.util.ArrayList<>();
		float[] at = new float[6];
		for (int i = 0; i < GLOBS; i++) {
			int region = goo.region[i];
			float s = goo.side[i];
			float[] p = goo.param[i];
			if (region == HEAD && tendrils.firstPerson()) {
				// not in front of your own eyes
				continue;
			}
			// creeping: two slow waves each way, never in step
			float u = p[0] + p[1] * wander(time, p[5], p[6]);
			float y = p[2] + p[3] * wander(time, p[7], p[8]);
			place(region, s, u, y, slim, at);
			float size = p[4] * (region == TORSO || region == HEAD ? 1.0F : LIMB_SIZE) * (bulk ? BULK_SIZE : 1.0F);
			float out = !bulk ? 0.0F : region == TORSO ? BULK_OUT_TORSO : region == HEAD ? BULK_OUT_HEAD : BULK_OUT_LIMB;
			float thick = size * GLOB_FLAT;
			ModelPart part = part(model, region, s);
			Matrix4f m = part == model.body ? body : matrix(part);
			// half sunk into the body
			float lift = out + thick * 0.35F;
			Vector3f c = m.transformPosition(new Vector3f((at[0] + at[3] * lift) / 16.0F, (at[1] + at[4] * lift) / 16.0F, (at[2] + at[5] * lift) / 16.0F));
			Vector3f n = m.transformDirection(new Vector3f(at[3], at[4], at[5])).normalize();
			Vector3f axis = m.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
			if (i < TENDRIL_GLOBS) {
				// its tendril's root, flowed over to the old one while a job needs it
				float on = smooth(goo.rooted[i]);
				Vector3f gn = toBody.transformDirection(new Vector3f(n)).normalize();
				float side = Math.signum(fixed[i][0]);
				// turned out to its own side (a belly one well out, so it never curls up in front of the face)
				if (gn.z < -0.2F) {
					gn.x += side * FRONT_OUT * -gn.z;
				}
				if (gn.x * side < MIN_OUT) {
					gn.x = side * MIN_OUT;
				}
				gn.normalize();
				if (on > 0.0F) {
					float[] r = fixed[i];
					Vector3f old = body.transformPosition(new Vector3f(r[0] / 16.0F, r[1] / 16.0F, r[2] / 16.0F));
					Vector3f way = body.transformDirection(new Vector3f(Mth.cos(r[4]) * Mth.sin(r[3]), -Mth.sin(r[4]), Mth.cos(r[4]) * Mth.cos(r[3])))
						.normalize();
					c.lerp(old, on);
					n.lerp(way, on);
					if (n.lengthSquared() < 1.0E-6F) {
						n.set(way);
					}
					n.normalize();
					axis.lerp(body.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize(), on).normalize();
				}
				Vector3f root = toBody.transformPosition(new Vector3f(c));
				int o = i * STRIDE;
				roots[o] = root.x * 16.0F;
				roots[o + 1] = root.y * 16.0F;
				roots[o + 2] = root.z * 16.0F;
				roots[o + 3] = gn.x;
				roots[o + 4] = gn.y;
				roots[o + 5] = gn.z;
				roots[o + 6] = 1.0F - on;
			}
			if (grown < 0.03F) {
				continue;
			}
			// swelling and shrinking (heaving when struck)
			float swell = 1.0F + SWELL * Mth.sin(time * SWELL_RATE * (0.7F + 0.6F * p[9]) + p[10])
				+ FLAIL_HEAVE * flail * Mth.sin(time * 1.5F + i * 2.1F);
			float r = size * swell * grown / 16.0F;
			float rt = thick * swell * grown / 16.0F;
			Vector3f e1 = new Vector3f(n).cross(axis);
			if (e1.lengthSquared() < 1.0E-4F) {
				e1 = new Vector3f(n).cross(new Vector3f(1.0F, 0.0F, 0.0F));
			}
			e1.normalize();
			// (e1, n, e2) right-handed, so the faces wind outward
			Vector3f e2 = new Vector3f(e1).cross(n).normalize();
			float wobble = WOBBLE + FLAIL_RIPPLE * flail;
			float phase = p[11];
			lobes.add(lobe(c, e1, n, e2, r, rt, r * 0.85F, phase, wobble));
			// its lobe, pinching off and flowing back, round the glob as it goes
			float split = smooth(0.5F + 0.5F * Mth.sin(time * SPLIT_RATE * (0.8F + 0.4F * p[9]) + p[11] * 1.7F));
			float turn = p[11] + time * 0.011F;
			Vector3f dir = new Vector3f(e1).mul(Mth.cos(turn)).add(new Vector3f(e2).mul(Mth.sin(turn)));
			float apart = r * Mth.lerp(split, SPLIT_NEAR, SPLIT_FAR);
			Vector3f lc = new Vector3f(c).add(new Vector3f(dir).mul(apart)).sub(new Vector3f(n).mul(rt * 0.25F));
			lobes.add(lobe(lc, e1, n, e2, r * LOBE, rt * 0.8F, r * LOBE, phase + 2.0F, wobble));
			float neck = NECK * (1.0F - split);
			if (neck > 0.12F) {
				Vector3f nc = new Vector3f(c).add(new Vector3f(dir).mul(apart * 0.5F));
				lobes.add(lobe(nc, dir, n, new Vector3f(dir).cross(n).normalize(), apart * 0.55F, rt * neck * 1.4F, r * neck, phase + 4.0F, wobble * 0.5F));
			}
		}
		state.setData(ROOTS, roots);
		if (lobes.isEmpty()) {
			return;
		}
		float churn = 1.0F + FLAIL_CHURN * flail;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, buffer) -> {
			for (float[] lobe : lobes) {
				sphere(buffer, pose, lobe, time * churn, light);
			}
		});
		if (GOO.size() > 64) {
			GOO.values().removeIf(g -> now - g.at > 10_000_000_000L);
		}
	}

	/** A slow wander in -1 to 1: two waves at this speed and phase, out of step. */
	private static float wander(float time, float speed, float phase) {
		float a = time * CREEP * (0.6F + 0.8F * speed) + phase;
		return 0.65F * Mth.sin(a) + 0.35F * Mth.sin(a * 2.31F + phase * 1.7F);
	}

	/** This wearer's globs, laid out the first time from its entity id (the same for everyone who sees them). */
	private static Goo goo(int id) {
		return GOO.computeIfAbsent(id, key -> {
			Goo goo = new Goo();
			long seed = key * 7919L + 31L;
			// per side: tendrils 2, 4 and 6 on the torso, an arm and a leg (2 never on a leg: it strikes), the spares on the torso and a leg
			for (int s = 0; s < 2; s++) {
				int[] mid = {TORSO, ARM, LEG};
				for (int k = 2; k > 0; k--) {
					int j = (int)(hash(seed + s * 13L + k) * (k + 1));
					int swap = mid[k];
					mid[k] = mid[j];
					mid[j] = swap;
				}
				if (mid[0] == LEG) {
					mid[0] = mid[1];
					mid[1] = LEG;
				}
				boolean legFirst = hash(seed + s * 17L + 5L) < 0.5;
				int[] regions = {SHOULDER, mid[0], mid[1], mid[2], legFirst ? LEG : TORSO, legFirst ? TORSO : LEG};
				for (int j = 0; j < regions.length; j++) {
					int i = j * 2 + s;
					goo.region[i] = regions[j];
					goo.side[i] = s == 0 ? -1.0F : 1.0F;
				}
			}
			goo.region[12] = HEAD;
			goo.side[12] = hash(seed + 91L) < 0.5 ? -1.0F : 1.0F;
			goo.region[13] = TORSO;
			goo.side[13] = hash(seed + 92L) < 0.5 ? -1.0F : 1.0F;
			for (int i = 0; i < GLOBS; i++) {
				float[] p = goo.param[i];
				long g = seed + i * 1009L;
				float a = (float)hash(g + 1), b = (float)hash(g + 2);
				switch (goo.region[i]) {
					case TORSO -> {
						p[0] = Mth.lerp(a, 0.4F, TORSO_ROUND - 0.4F);
						p[1] = 0.7F;
						p[2] = Mth.lerp(b, 3.5F, 9.0F);
						p[3] = 2.5F;
					}
					case ARM -> {
						p[0] = Mth.lerp(a, -1.0F, 1.0F);
						p[1] = 0.8F;
						p[2] = Mth.lerp(b, 2.5F, 6.0F);
						p[3] = 2.5F;
					}
					case LEG -> {
						p[0] = Mth.lerp(a, -1.0F, 1.0F);
						p[1] = 0.9F;
						p[2] = Mth.lerp(b, 3.0F, 8.0F);
						p[3] = 2.2F;
					}
					case HEAD -> {
						p[0] = 0.35F;
						p[1] = 0.45F;
						p[2] = -5.0F;
						p[3] = 2.0F;
					}
					default -> {
						// the shoulder: over the top of the arm and down its outside
						p[0] = Mth.lerp(a, -0.5F, 0.9F);
						p[1] = 0.6F;
						p[2] = -0.4F;
						p[3] = 1.4F;
					}
				}
				p[4] = Mth.lerp((float)hash(g + 3), GLOB_MIN, GLOB_MAX);
				// speeds (0-1) and phases (radians) by turns; the last a phase too
				for (int k = 5; k < 12; k++) {
					p[k] = (float)hash(g + k) * (k % 2 == 0 || k == 11 ? Mth.TWO_PI : 1.0F);
				}
			}
			return goo;
		});
	}

	/**
	 * A point on a body part's surface and its outward way (into {@code out}: x, y, z, then the normal; the part's own
	 * space, pixels): {@code u} round it and {@code y} down it, for a glob on side {@code s} (-1 the wearer's right, model
	 * -X). Kept off the face, the pecs and the soles whatever the wander says.
	 */
	private static void place(int region, float s, float u, float y, boolean slim, float[] out) {
		float cx = 0.0F, hx, hz, dx, dz;
		switch (region) {
			case TORSO -> {
				// round from the back (0) past the side to the belly (pi), only low on the front (below the pecs)
				u = Mth.clamp(u, 0.1F, TORSO_ROUND);
				float low = Mth.lerp(smooth((u - BELLY_FROM) / 0.5F), 1.5F, BELLY_TOP);
				y = Mth.clamp(y, low, 11.0F);
				hx = 4.0F;
				hz = 2.0F;
				dx = s * Mth.sin(u);
				dz = Mth.cos(u);
			}
			case HEAD -> {
				// the back of the skull and over the top of it, never round to the face
				u = Mth.clamp(u, 0.0F, 0.85F);
				y = Mth.clamp(y, -7.5F, -2.0F);
				hx = 4.0F;
				hz = 4.0F;
				dx = s * Mth.sin(u);
				dz = Mth.cos(u);
			}
			default -> {
				// a limb: u round it from its outside (0) to the front (-) or the back (+), never its inside
				u = Mth.clamp(u, -1.9F, 1.9F);
				boolean leg = region == LEG;
				y = leg ? Mth.clamp(y, 1.0F, 10.0F) : region == SHOULDER ? Mth.clamp(y, -1.8F, 2.0F) : Mth.clamp(y, -0.5F, 8.5F);
				cx = leg ? 0.0F : s * (slim ? 0.5F : 1.0F);
				hx = leg || !slim ? 2.0F : 1.5F;
				hz = 2.0F;
				dx = s * Mth.cos(u);
				dz = Mth.sin(u);
			}
		}
		// onto the box, round its edges
		float k = 1.0F / Math.max(Math.abs(dx) / hx, Math.abs(dz) / hz);
		float px = dx * k, pz = dz * k;
		float nx = px / (hx * hx), nz = pz / (hz * hz), ny = 0.0F;
		float nl = Mth.sqrt(nx * nx + nz * nz);
		nx /= nl;
		nz /= nl;
		// over the top of a shoulder or the skull, it faces up
		float top = region == SHOULDER ? smooth((0.8F - y) / 2.6F) * 0.85F : region == HEAD ? smooth((-6.0F - y) / 1.5F) * 0.8F : 0.0F;
		if (top > 0.0F) {
			nx *= 1.0F - top;
			nz *= 1.0F - top;
			ny = -top;
			float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
			nx /= l;
			ny /= l;
			nz /= l;
			if (region == HEAD) {
				px *= 1.0F - top * 0.5F;
				pz *= 1.0F - top * 0.5F;
			}
		}
		out[0] = cx + px;
		out[1] = y;
		out[2] = pz;
		out[3] = nx;
		out[4] = ny;
		out[5] = nz;
	}

	private static ModelPart part(PlayerModel model, int region, float s) {
		return switch (region) {
			case TORSO -> model.body;
			case HEAD -> model.head;
			case LEG -> s < 0.0F ? model.rightLeg : model.leftLeg;
			default -> s < 0.0F ? model.rightArm : model.leftArm;
		};
	}

	/** A part's pose (blocks) in the model's space. */
	private static Matrix4f matrix(ModelPart part) {
		PoseStack stack = new PoseStack();
		part.translateAndRotate(stack);
		return new Matrix4f(stack.last().pose());
	}

	private static float[] lobe(Vector3f c, Vector3f e1, Vector3f n, Vector3f e2, float r1, float rn, float r2, float phase, float wobble) {
		return new float[] {c.x, c.y, c.z, e1.x, e1.y, e1.z, n.x, n.y, n.z, e2.x, e2.y, e2.z, r1, rn, r2, phase, wobble};
	}

	/** One lobe: a squashed ball whose surface swells and sinks in slow waves (WebBlobRenderer's body, smaller). */
	private static void sphere(VertexConsumer buffer, PoseStack.Pose pose, float[] l, float time, int light) {
		Vec3 c = new Vec3(l[0], l[1], l[2]);
		Vec3 e1 = new Vec3(l[3], l[4], l[5]), n = new Vec3(l[6], l[7], l[8]), e2 = new Vec3(l[9], l[10], l[11]);
		double r1 = l[12], rn = l[13], r2 = l[14];
		if (r1 < 1.0E-4 || rn < 1.0E-4 || r2 < 1.0E-4) {
			return;
		}
		Vec3[][] p = new Vec3[RINGS + 1][SEGMENTS + 1];
		Vec3[][] nn = new Vec3[RINGS + 1][SEGMENTS + 1];
		for (int r = 0; r <= RINGS; r++) {
			double lat = Math.PI * r / RINGS - Math.PI / 2.0;
			for (int s = 0; s <= SEGMENTS; s++) {
				double lon = Math.PI * 2.0 * (s % SEGMENTS) / SEGMENTS;
				double x = Math.cos(lat) * Math.cos(lon), y = Math.sin(lat), z = Math.cos(lat) * Math.sin(lon);
				double swell = 1.0 + l[16] * Math.sin(time * 0.17 + lat * 2.3 + lon * 3.0 + l[15])
					+ l[16] * 0.6 * Math.sin(time * 0.29 - lon * 2.0 + lat + l[15] * 1.3);
				p[r][s] = c.add(e1.scale(x * r1 * swell)).add(n.scale(y * rn * swell)).add(e2.scale(z * r2 * swell));
				Vec3 normal = e1.scale(x / r1).add(n.scale(y / rn)).add(e2.scale(z / r2));
				nn[r][s] = normal.lengthSqr() < 1.0E-12 ? n : normal.normalize();
			}
		}
		float u0 = 3.0F / 32.0F, u1 = 6.0F / 32.0F, v0 = 3.0F / 32.0F, v1 = 7.5F / 32.0F;
		for (int r = 0; r < RINGS; r++) {
			for (int s = 0; s < SEGMENTS; s++) {
				WebLineRenderer.vertex(buffer, pose, p[r][s], u0, v0, nn[r][s], light);
				WebLineRenderer.vertex(buffer, pose, p[r + 1][s], u0, v1, nn[r + 1][s], light);
				WebLineRenderer.vertex(buffer, pose, p[r + 1][s + 1], u1, v1, nn[r + 1][s + 1], light);
				WebLineRenderer.vertex(buffer, pose, p[r][s + 1], u1, v0, nn[r][s + 1], light);
			}
		}
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	private static double hash(long seed) {
		long z = seed * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) * 0x1.0p-53;
	}
}
