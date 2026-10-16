package com.jaymospowers.client.power.web;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The suit torn off (Spider-Man 3's bell tower, Andy's reference): whenever the suit comes off by tearing (rejection,
 * death, the radial toggle, or it withdrawing after its debut: the synced {@code WebSymbiote.TORN}), instead of the
 * plain withdraw ({@link WebSuitGrowth}) everyone sees it clawed off the chest. Over {@link #TEAR_TICKS}:
 * <ol>
 * <li><b>Claw</b> (to {@link #CLAW_END}): hands dig into the chest, head down; the mask slides off the face into the
 * neck (by {@link #MASK_END}).</li>
 * <li><b>Rip</b> (to {@link #RIP_END}): the arms wrench outward, head thrown back; the front of the suit peels off the
 * chest from the middle outward, a strip at a time ({@link #CENTER_FIRST}), each strip pulled into the hand on its
 * side, its ends clinging to the body longest, so it stretches in ribbons; sticky strands string between the bared
 * chest and the strips, thinning as they stretch and snapping.</li>
 * <li><b>Shed</b> (to the end): arms flung wide and up, roaring; the back peels round to the hands too, the sleeves
 * slide off over the hands, the legs' suit draws up into the hips, and tendrils thrash out of the clumps in the hands
 * as it lets go, shrinking away to nothing.</li>
 * </ol>
 * The pose ({@link #rotate}, {@link #applyModel}) and the suit's own models ({@link #applyModel} on the suit layer's)
 * go by the same synced change, so it's the same for everyone; the torn strips and strands are drawn by
 * {@link WebSuitWrapLayer} ({@link #submit}).
 */
public final class WebSuitTear {
	/** How long the tear-off takes (ticks; the plain withdraw takes {@code WebSuitGrowth.TICKS} 36). */
	static final float TEAR_TICKS = 46.0F;
	/** It counts as torn if {@code TORN} was set within this many ticks of the suit coming off. */
	static final int TORN_MATCH_TICKS = 6;
	/** The phases (shares of the tear): claw until {@code CLAW_END}, rip until {@code RIP_END}, then shed. The mask is gone by {@code MASK_END}. */
	static final float CLAW_END = 0.2F, RIP_END = 0.62F, MASK_END = 0.25F;
	/** Each strip takes this share to peel; the outermost strips start this much later than the middle ones. */
	static final float PULL_SPAN = 0.3F, CENTER_FIRST = 0.16F;
	/** Strips across the torso (front and back), and points down each one. */
	static final int COLUMNS = 8, ROWS = 7;
	/** A strip's half-width and half-thickness (pixels); how far it sags as it stretches (blocks); how much it writhes. */
	static final float STRIP_WIDTH = 0.55F, STRIP_THICK = 0.14F, STRIP_SAG = 0.14F, STRIP_WRITHE = 0.05F;
	/** Sticky strands each side; they snap stretched between {@code SNAP_MIN} and {@code SNAP_MAX} blocks; their radius. */
	static final int STICKY = 8;
	static final double SNAP_MIN = 0.45, SNAP_MAX = 1.0, STICKY_RADIUS = 0.016;
	/** Shedding: tendrils thrashing out of each hand's clump, how long (blocks) and how wild. */
	static final int THRASH_TENDRILS = 4;
	static final double THRASH_LENGTH = 0.55, THRASH_WILD = 0.6;
	/** The body: leaning in to claw, arched back as it rips and sheds (degrees), and how hard it shudders. */
	static final float LEAN_CLAW = -14.0F, LEAN_RIP = 16.0F, LEAN_SHED = 24.0F, SHUDDER = 2.5F;

	private WebSuitTear() {
	}

	/** How far through the tear-off (0-1), or null when the suit isn't being torn off. */
	static @Nullable Float tear(LivingEntityRenderState state) {
		WebSuitGrowth.Growth growth = state.getData(WebSuitGrowth.KEY);
		return growth != null && growth.tear() ? growth.t() : null;
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** The phases' weights at {@code t}: clawing at the chest, wrenching it outward, shedding it, and all of it fading out at the end. */
	private static float claw(float t) {
		return ease(t / 0.1F) * (1.0F - ease((t - CLAW_END) / 0.1F));
	}

	private static float rip(float t) {
		return ease((t - CLAW_END + 0.04F) / 0.22F) * (1.0F - ease((t - RIP_END) / 0.12F));
	}

	private static float shed(float t) {
		return ease((t - RIP_END + 0.04F) / 0.16F);
	}

	private static float fade(float t) {
		return 1.0F - ease((t - 0.9F) / 0.1F);
	}

	/** After the body faces its way: leaning in over the chest, then arched back. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Float t = tear(state);
		if (t == null || state.deathTime > 0.0F) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (state instanceof AvatarRenderState avatar && minecraft.player != null && avatar.id == minecraft.player.getId()
			&& minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		float hips = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.45F;
		float fade = fade(t);
		float shudder = Mth.sin(state.ageInTicks * 3.1F) * SHUDDER * Math.max(rip(t), shed(t));
		float pitch = (LEAN_CLAW * claw(t) + LEAN_RIP * rip(t) + LEAN_SHED * shed(t)) * fade;
		poseStack.translate(0.0F, hips, 0.0F);
		poseStack.rotateDegrees(Axis.XP, -pitch);
		poseStack.rotateDegrees(Axis.ZP, shudder * fade);
		poseStack.translate(0.0F, -hips, 0.0F);
	}

	/**
	 * After the model animates: the body clawing it away (every model drawn for the wearer); on the suit layer's own
	 * models, the mask sliding off, the sleeves sliding off over the hands, the legs' suit drawn up, the torso hidden
	 * (its strips are drawn by {@link #submit}), and the goo gone.
	 */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Float tear = tear(avatar);
		if (tear == null) {
			return;
		}
		float t = tear;
		float fade = fade(t);
		float claw = claw(t) * fade, rip = rip(t) * fade, shed = shed(t) * fade;
		float tremble = Mth.sin(avatar.ageInTicks * 2.9F) * 0.08F * Math.max(claw, rip);
		// clawing at the chest, fingers dug in
		set(h.rightArm, -1.6F + tremble, -0.75F, 0.1F, claw);
		set(h.leftArm, -1.6F - tremble, 0.75F, -0.1F, claw);
		// wrenching it outward, elbows high, the hands dragging it off to the sides
		set(h.rightArm, -1.45F - tremble, 0.3F, 1.15F, rip);
		set(h.leftArm, -1.45F + tremble, -0.3F, -1.15F, rip);
		// flung wide and up as it lets go
		set(h.rightArm, -0.9F, 0.0F, 1.8F + tremble, shed);
		set(h.leftArm, -0.9F, 0.0F, -1.8F - tremble, shed);
		set(h.rightLeg, -0.1F, 0.0F, 0.22F, Math.max(rip, shed));
		set(h.leftLeg, 0.1F, 0.0F, -0.22F, Math.max(rip, shed));
		// looking down at it, then the head thrown back, shaking
		h.head.xRot = Mth.lerp(claw, h.head.xRot, 0.5F);
		h.head.xRot = Mth.lerp(Math.max(rip, shed), h.head.xRot, -0.55F - 0.3F * shed + Mth.sin(avatar.ageInTicks * 2.3F) * 0.08F);
		WebSuitGrowth.Role role = WebSuitGrowth.role(model);
		if (role == WebSuitGrowth.Role.GOO) {
			model.root().visible = false;
		} else if (role == WebSuitGrowth.Role.SUIT) {
			float mask = 1.0F - ease(t / MASK_END);
			Float face = avatar.getData(WebSuitGrowth.FACE);
			if (face != null) {
				// (the fight won: the face stays bare as the rest settles back)
				mask = Math.min(mask, 1.0F - ease(face));
			}
			shrink(h.head, mask);
			// the torso is drawn as torn strips (submit) once they start to come away; until then it's whole
			h.body.visible = !strips(t);
			float legs = 1.0F - ease((t - 0.6F) / 0.3F);
			shrink(h.rightLeg, legs);
			shrink(h.leftLeg, legs);
			float sleeves = 1.0F - ease((t - 0.55F) / 0.35F);
			slide(h.rightArm, sleeves);
			slide(h.leftArm, sleeves);
		}
	}

	/** The torso is drawn as torn strips: from when the first strip starts to come away (before, the suit's own torso). */
	static boolean strips(float t) {
		return t > CLAW_END;
	}

	/** Shrinks a part toward its joint (its pivot), {@code cover} of it left. */
	private static void shrink(ModelPart part, float cover) {
		part.yScale = cover;
		part.xScale = Mth.lerp(cover, 0.9F, 1.0F);
		part.zScale = Mth.lerp(cover, 0.9F, 1.0F);
		part.visible = cover > 0.02F;
	}

	/** Slides an arm's sleeve down the arm and off over the hand, {@code cover} of it left (bunched at the hand). */
	private static void slide(ModelPart arm, float cover) {
		if (cover >= 1.0F) {
			return;
		}
		// the arm's box runs from 2 above its pivot to 10 below; scaled about the pivot, its hand end would move up to
		// 10 x cover: slid back down along the arm by the difference, it stays at the hand
		float shift = 10.0F * (1.0F - cover);
		org.joml.Vector3f along = new org.joml.Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).transform(new org.joml.Vector3f(0.0F, shift, 0.0F));
		arm.x += along.x;
		arm.y += along.y;
		arm.z += along.z;
		arm.yScale = cover;
		arm.xScale = 1.0F + 0.25F * (1.0F - cover);
		arm.zScale = 1.0F + 0.25F * (1.0F - cover);
		arm.visible = cover > 0.03F;
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}

	// ---- the torn strips, strands and thrashing tendrils (from WebSuitWrapLayer, the wearer's model posed) ----

	/** A point of a part (pixels, its own space) in the model's root space (blocks). */
	private static Vec3 at(org.joml.Matrix4f part, float x, float y, float z) {
		org.joml.Vector3f v = part.transformPosition(new org.joml.Vector3f(x / 16.0F, y / 16.0F, z / 16.0F));
		return new Vec3(v.x, v.y, v.z);
	}

	private static org.joml.Matrix4f matrix(ModelPart part) {
		PoseStack stack = new PoseStack();
		part.translateAndRotate(stack);
		return new org.joml.Matrix4f(stack.last().pose());
	}

	/** Draws the tear-off's strips, strands and tendrils ({@code poseStack} at the layer's, before the model root). */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state, PlayerModel parent, float t) {
		if (state.isInvisible || !strips(t)) {
			return;
		}
		float time = state.ageInTicks;
		int light = state.lightCoords;
		org.joml.Matrix4f body = matrix(parent.body);
		Vec3 rightHand = at(matrix(parent.rightArm), -1.0F, 10.0F, 0.0F);
		Vec3 leftHand = at(matrix(parent.leftArm), 1.0F, 10.0F, 0.0F);
		Vec3 middle = at(body, 0.0F, 5.0F, 0.0F);
		float shed = ease((t - RIP_END) / 0.25F);
		float gone = ease((t - 0.88F) / 0.12F);
		// each strip: front then back, as point rows (rest on the body, and where it is now)
		java.util.List<Vec3[]> strips = new java.util.ArrayList<>();
		java.util.List<Vec3[]> rests = new java.util.ArrayList<>();
		java.util.List<Float> pulls = new java.util.ArrayList<>();
		for (int face = 0; face < 2; face++) {
			float z = face == 0 ? -2.2F : 2.2F;
			for (int c = 0; c < COLUMNS; c++) {
				float x = -4.0F + (c + 0.5F) * 8.0F / COLUMNS;
				float delay = CENTER_FIRST * Math.abs(x) / 3.5F;
				// the front from the middle out as it rips; the back round to the hands as it sheds
				float pull = face == 0 ? ease((t - CLAW_END - delay) / PULL_SPAN) : ease((t - RIP_END + 0.06F - delay * 0.6F) / 0.28F);
				Vec3 hand = x < 0.0F ? rightHand : leftHand;
				Vec3[] rest = new Vec3[ROWS + 1];
				Vec3[] now = new Vec3[ROWS + 1];
				for (int j = 0; j <= ROWS; j++) {
					float f = j / (float)ROWS;
					Vec3 r = at(body, x, f * 12.0F, z);
					// the ends cling longest: the middle comes away first, stretching it into a ribbon
					float a = Math.max(ease(pull * 1.3F - (1.0F - Mth.sin(f * Mth.PI)) * 0.45F), Math.min(pull, shed));
					Vec3 clump = hand.add(r.subtract(middle).scale(0.22));
					Vec3 p = r.lerp(clump, a);
					double writhe = STRIP_WRITHE * a * (1.0 - a) * 4.0;
					p = p.add(Mth.sin(time * 1.3F + j * 1.1F + c) * writhe, STRIP_SAG * Mth.sin(a * Mth.PI) + Mth.cos(time * 1.1F + j + c * 0.7F) * writhe,
						Mth.sin(time * 0.9F + j * 0.8F + c * 1.9F) * writhe);
					rest[j] = r;
					now[j] = p;
				}
				strips.add(now);
				rests.add(rest);
				pulls.add(pull);
			}
		}
		long seed = state.id * 92821L;
		poseStack.pushPose();
		parent.root().translateAndRotate(poseStack);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebStrainLook.texture(WebTubes.TEXTURE, state)), (pose, buffer) -> {
			Vec3 across = at(body, 1.0F, 0.0F, 0.0F).subtract(at(body, 0.0F, 0.0F, 0.0F));
			double width = STRIP_WIDTH * 8.0 / COLUMNS / 16.0 * 1.9 * (1.0 - gone), thick = STRIP_THICK / 16.0 * (1.0 - gone);
			if (width > 1.0E-4) {
				for (int s = 0; s < strips.size(); s++) {
					Vec3[] points = strips.get(s);
					for (int j = 0; j < ROWS; j++) {
						WebTubes.prism(buffer, pose, points[j], points[j + 1], across, width, thick, width, thick, (s + j) % 4 == 0 ? WebTubes.SHEEN : WebTubes.BLACK,
							light);
					}
				}
			}
			// sticky strands strung between the bared body and the strips, thinning as they stretch, snapping
			for (int k = 0; k < STICKY * 2; k++) {
				// mostly off the front strips, a few off the back
				int s = (int)(hash(seed + k) * COLUMNS) + (hash(seed + k + 50) < 0.8 ? 0 : COLUMNS);
				s = Math.min(strips.size() - 1, s);
				float pull = pulls.get(s);
				int j = 1 + (int)(hash(seed + k * 3L + 7) * (ROWS - 1));
				Vec3 from = rests.get(s)[j], to = strips.get(s)[j];
				double dist = from.distanceTo(to);
				double snap = Mth.lerp(hash(seed + k * 5L + 3), SNAP_MIN, SNAP_MAX);
				if (pull <= 0.02F || dist > snap || dist < 1.0E-3 || gone > 0.5F) {
					continue;
				}
				double r = STICKY_RADIUS * Mth.clamp(0.25 / dist, 0.3, 1.0);
				Vec3 sag = new Vec3(0.0, dist * 0.3, 0.0);
				Vec3 prev = from;
				for (int n = 1; n <= 4; n++) {
					double u = n / 4.0;
					Vec3 p = from.lerp(to, u).add(sag.scale(4.0 * u * (1.0 - u)));
					WebTubes.cord(buffer, pose, prev, p, r, r * (n == 2 ? 0.7 : 1.0), WebTubes.SHEEN, light);
					prev = p;
				}
			}
			// shedding: tendrils thrash out of the clump in each hand as it lets go, shrinking away
			float thrash = shed * (1.0F - gone);
			if (thrash > 0.02F) {
				for (int side = 0; side < 2; side++) {
					Vec3 hand = side == 0 ? rightHand : leftHand;
					Vec3 outward = hand.subtract(middle).normalize();
					for (int k = 0; k < THRASH_TENDRILS; k++) {
						long h = seed + side * 1000L + k * 37L;
						Vec3 dir = outward.add(hash(h) - 0.5, hash(h + 1) - 0.8, hash(h + 2) - 0.5).normalize();
						double length = THRASH_LENGTH * thrash * (0.7 + 0.6 * hash(h + 3));
						Vec3 prev = hand;
						for (int n = 1; n <= 6; n++) {
							double u = n / 6.0;
							double whip = Math.sin(time * 1.4 + k * 2.0 + side - u * 6.0) * THRASH_WILD * length * u;
							double whip2 = Math.cos(time * 1.1 + k * 1.3 - u * 5.0) * THRASH_WILD * 0.7 * length * u;
							Vec3 p = hand.add(dir.scale(length * u)).add(whip, whip2, Math.sin(time * 0.8 + k - u * 4.0) * THRASH_WILD * 0.5 * length * u);
							WebTubes.cord(buffer, pose, prev, p, 0.035 * (1.0 - 0.7 * (u - 1.0 / 6.0)) * thrash, 0.035 * (1.0 - 0.7 * u) * thrash,
								n % 2 == 0 ? WebTubes.SHEEN : WebTubes.BLACK, light);
							prev = p;
						}
					}
				}
			}
		});
		poseStack.popPose();
	}

	/** A steady random number 0-1 for {@code seed}. */
	private static double hash(long seed) {
		long z = seed * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) * 0x1.0p-53;
	}
}
