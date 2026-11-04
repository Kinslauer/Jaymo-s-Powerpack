package com.jaymospowers.client.power.xenoform.ultimate;

import com.jaymospowers.client.power.xenoform.XenoFormModels;
import com.jaymospowers.client.power.xenoform.XenoMotion;
import com.jaymospowers.client.power.xenoform.XenoformClient;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/**
 * (0.20.21, Ultimates patch 1) Prism's Ultimate, Geode: Prism's faceted crystal core on six thin shard legs, with the same tripod
 * crab gait, grown heavier and plated. The core is cased in dark amethyst plates (two side plates, a front and a back plate, two
 * ribs along the top either side of the insignia), spikes stand off its sides and front, the six legs have plated hips, and the ten
 * spires on top are taller, faceted and end in sharp tips. Its skin glows along the plate seams and brightest in the two front
 * facets (the glow map, drawn fullbright by the layer). It steps slower and heavier than Prism, and the spires jiggle harder with
 * each step. The skin is {@code tools/ultimates/prism.py}, 128x128; {@link #UV} here is the table that script prints
 * ({@code prism.py layout}).
 * <p>
 * The change-in film zooms on {@code leg0..leg5}, {@code core} and the spires {@code crystal0..}: those names are parts of this mesh.
 */
public final class UltimateGeode extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** The core's centre height (model y; the hips are at it) and how far it drops when sneaking, rooted, and per unit of landing. */
	static final float CORE_Y = 13.0F, CROUCH_DROP = 2.5F, ROOT_DROP = 2.2F, LAND_SQUASH = 2.5F;
	/** A sneaking player is drawn 2/16 of a block lower: crouched, the ground is that much higher in model space. */
	private static final float CROUCH_SINK = 2.0F / 0.9375F;
	/** The shard legs' length (the three boxes in the skin, 4 + 4 + 4 pixels) and the clamp on how far they stretch or shrink. */
	static final float LEG_LEN = 12.0F, LEG_MIN = 0.6F, LEG_MAX = 1.3F;
	/** The gait (slower and wider than Prism's): how fast the legs step against the walk, how far a foot strides, how high it lifts, the core's bob, how fast the walk speed reaches full stride. */
	static final float GAIT = 0.62F, STRIDE = 3.6F, LIFT = 2.6F, BOB = 0.55F, AMP_GAIN = 1.4F;
	/** How far a lifted foot draws in toward the body (fraction of its lift). */
	static final float LIFT_TUCK = 0.25F;
	/** Splay (pixels the feet go farther out): sneaking, rooted, in the air; and how far a rooted foot spreads fore and aft, how deep its tip digs, how far the legs tuck up in the air. */
	static final float CROUCH_SPLAY = 2.0F, ROOT_SPLAY = 3.2F, AIR_SPLAY = 1.5F, ROOT_REACH = 1.6F, ROOT_SINK = 0.8F, AIR_TUCK = 2.5F;
	/** The spires: idle sway (radians) and its rate, the walk jiggle (harder than Prism's), how far a turn leans them, a fall's flare, a root's flare and growth. */
	static final float SWAY = 0.02F, SWAY_RATE = 0.06F, JIGGLE = 0.06F, TRAIL_LEAN = 0.12F, FALL_FLARE = 0.18F, ROOT_FLARE = 0.12F, ROOT_GROW = 0.1F;
	/** An attack swing thrusts the top spires: the tilt forward (radians) and the shift forward (pixels), at full weight. */
	static final float THRUST_TILT = 0.8F, THRUST_SHIFT = 2.2F;
	/** The hips: how far out from the centre, and each row's z (front to hind). */
	static final float HIP_X = 4.0F;
	private static final float[] HIP_Z = {-4.5F, 0.0F, 4.5F};
	/** Each row's rest foot: how far out of its hip, and its z from the hip. */
	private static final float[] FOOT_OUT = {5.5F, 6.5F, 5.5F};
	private static final float[] FOOT_DZ = {-3.0F, 0.0F, 3.0F};
	/** The spires' bases sit this far above the core's centre (inside its 6-pixel-tall top). */
	static final float BASE_Y = -2.3F;
	static final int CRYSTALS = 10;
	/** Each spire: its base x and z on the core, rest tilt about x and z, and its weight in a thrust (1 = the tall ones). */
	private static final float[] CX = {0.0F, -3.4F, 3.4F, -1.0F, -3.6F, 3.6F, -3.8F, 3.8F, -3.2F, 2.4F};
	private static final float[] CZ = {4.4F, 1.8F, 2.8F, 5.8F, -4.8F, -5.0F, -0.8F, 0.2F, 5.0F, 5.6F};
	private static final float[] CRX = {-0.1F, 0.0F, -0.1F, -0.5F, 0.55F, 0.5F, 0.1F, -0.1F, -0.3F, -0.35F};
	private static final float[] CRZ = {0.0F, -0.36F, 0.4F, -0.1F, -0.3F, 0.3F, -0.95F, 0.9F, -0.55F, 0.5F};
	private static final float[] CW = {1.0F, 0.9F, 0.9F, 0.7F, 0.5F, 0.5F, 0.3F, 0.3F, 0.6F, 0.6F};

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/prism.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("core", 0, 0), uv("side", 44, 0), uv("front", 64, 0), uv("back", 80, 0), uv("rib", 96, 0), uv("spike", 118, 0),
		uv("leg_a", 0, 19), uv("leg_b", 12, 19), uv("leg_c", 20, 19), uv("hip", 24, 19),
		uv("cA", 40, 19), uv("cB", 56, 19), uv("cC", 68, 19), uv("cD", 80, 19), uv("cE", 92, 19),
		uv("tA", 100, 19), uv("tB", 108, 19), uv("tC", 112, 19));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart core;
	private final ModelPart[] crystals = new ModelPart[CRYSTALS];
	/** Right side front to hind (0-2), then left (3-5). */
	private final ModelPart[] legs = new ModelPart[6];

	public UltimateGeode() {
		super(XenoFormModels.bake(mesh(), 128, 128), "prism");
		ModelPart root = this.root();
		this.core = root.getChild("core");
		for (int i = 0; i < CRYSTALS; i++) {
			this.crystals[i] = this.core.getChild("crystal" + i);
		}
		for (int i = 0; i < 6; i++) {
			this.legs[i] = root.getChild("leg" + i);
		}
		// laid face-up on the core (half its 6 pixels tall above the body's centre), a little forward of the middle
		this.insigniaY = 0.8F;
		this.insigniaZ = -3.0F;
		this.insigniaSize = 0.8F;
	}

	/** A box of {@code key}'s patch of the skin, its size {@code w x h x d} (the same as {@code DIMS} in the painter). */
	private static CubeListBuilder cube(String key, float x, float y, float z, int w, int h, int d) {
		int[] at = UV.get(key);
		return CubeListBuilder.create().texOffs(at[0], at[1]).addBox(x, y, z, w, h, d);
	}

	private static MeshDefinition mesh() {
		// the man's parts stay (hidden) for the first-person hand and the insignia plate
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
		PartDefinition root = mesh.getRoot();
		PartDefinition core = root.addOrReplaceChild("core", cube("core", -4.5F, -3.0F, -6.5F, 9, 6, 13), PartPose.offset(0.0F, CORE_Y, 0.0F));
		// the plates: a side plate each side, a front plate with the two bright facets, a back plate, a rib each side of the top
		core.addOrReplaceChild("plate_r", cube("side", -5.5F, -2.5F, -4.5F, 1, 3, 9), PartPose.ZERO);
		core.addOrReplaceChild("plate_l", cube("side", 4.5F, -2.5F, -4.5F, 1, 3, 9), PartPose.ZERO);
		core.addOrReplaceChild("plate_front", cube("front", -3.5F, -2.5F, -7.5F, 7, 4, 1), PartPose.ZERO);
		core.addOrReplaceChild("plate_back", cube("back", -3.5F, -2.5F, 6.5F, 7, 4, 1), PartPose.ZERO);
		core.addOrReplaceChild("rib_r", cube("rib", -4.5F, -4.0F, -5.0F, 1, 1, 10), PartPose.ZERO);
		core.addOrReplaceChild("rib_l", cube("rib", 3.5F, -4.0F, -5.0F, 1, 1, 10), PartPose.ZERO);
		// spikes: three off each side plate, two forward off the front plate
		for (int i = 0; i < 3; i++) {
			float z = -3.0F + 3.0F * i;
			core.addOrReplaceChild("spike_r" + i, cube("spike", -0.5F, -4.0F, -0.5F, 1, 4, 1), PartPose.offsetAndRotation(-5.0F, -2.2F, z, 0.0F, 0.0F, -0.95F));
			core.addOrReplaceChild("spike_l" + i, cube("spike", -0.5F, -4.0F, -0.5F, 1, 4, 1), PartPose.offsetAndRotation(5.0F, -2.2F, z, 0.0F, 0.0F, 0.95F));
		}
		core.addOrReplaceChild("prong_r", cube("spike", -0.5F, -4.0F, -0.5F, 1, 4, 1), PartPose.offsetAndRotation(-2.5F, -2.0F, -7.0F, 0.95F, 0.0F, -0.2F));
		core.addOrReplaceChild("prong_l", cube("spike", -0.5F, -4.0F, -0.5F, 1, 4, 1), PartPose.offsetAndRotation(2.5F, -2.0F, -7.0F, 0.95F, 0.0F, 0.2F));
		// the spires: a body box, a narrower tip on it and, on the tallest, a needle on that
		crystal(core, 0, "cA", 4, 13, "tA", 2, 4);
		crystal(core, 1, "cB", 3, 10, "tB", 1, 4);
		crystal(core, 2, "cC", 3, 9, "tB", 1, 4);
		crystal(core, 3, "cD", 3, 7, "tC", 1, 3);
		for (int i = 4; i < CRYSTALS; i++) {
			crystal(core, i, "cE", 2, 5, "tC", 1, 3);
		}
		// six shard legs from the core's sides, hanging from the hip: a 3x3, a 2x2 and a 1x1 section, each 4 long, under a plated hip
		for (int i = 0; i < 6; i++) {
			boolean left = i >= 3;
			CubeListBuilder leg = cube("leg_a", -1.5F, 0.0F, -1.5F, 3, 4, 3);
			int[] b = UV.get("leg_b"), c = UV.get("leg_c");
			leg.texOffs(b[0], b[1]).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 4.0F, 2.0F);
			leg.texOffs(c[0], c[1]).addBox(-0.5F, 8.0F, -0.5F, 1.0F, 4.0F, 1.0F);
			PartDefinition part = root.addOrReplaceChild("leg" + i, leg, PartPose.offset(left ? HIP_X : -HIP_X, CORE_Y, HIP_Z[i % 3]));
			part.addOrReplaceChild("hip" + i, cube("hip", -2.0F, 0.5F, -2.0F, 4, 2, 4), PartPose.ZERO);
		}
		return mesh;
	}

	private static void crystal(PartDefinition core, int i, String body, int w, int bodyH, String tip, int tipW, int tipH) {
		int[] b = UV.get(body), t = UV.get(tip);
		CubeListBuilder c = CubeListBuilder.create()
			.texOffs(b[0], b[1]).addBox(-w / 2.0F, -bodyH, -w / 2.0F, w, bodyH, w)
			.texOffs(t[0], t[1]).addBox(-tipW / 2.0F, -bodyH - tipH, -tipW / 2.0F, tipW, tipH, tipW);
		if (i == 0) {
			// the tallest spire ends in a needle
			int[] n = UV.get("tC");
			c.texOffs(n[0], n[1]).addBox(-0.5F, -bodyH - tipH - 3.0F, -0.5F, 1.0F, 3.0F, 1.0F);
		}
		core.addOrReplaceChild("crystal" + i, c, PartPose.offsetAndRotation(CX[i], BASE_Y, CZ[i], CRX[i], 0.0F, CRZ[i]));
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		this.head.visible = this.hat.visible = this.body.visible = false;
		this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
		XenoMotion.Read m = XenoMotion.read(state);
		float age = state.ageInTicks;
		boolean rooted = "root".equals(state.getData(XenoformClient.ACT));
		boolean crouch = state.isCrouching;
		float air = Mth.clamp(m.air(), 0.0F, 1.0F);
		// rooted it doesn't step at all
		float amp = rooted ? 0.0F : Math.min(1.0F, state.walkAnimationSpeed * AMP_GAIN);
		float walk = state.walkAnimationPos * GAIT;
		float swing = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
		// the ground line in model space: sneaking, the renderer draws the whole model CROUCH_SINK lower
		float sink = crouch ? CROUCH_SINK : 0.0F;
		float ground = 24.0F - sink;
		float drop = (rooted ? ROOT_DROP : crouch ? CROUCH_DROP : 0.0F) + Math.max(0.0F, m.land()) * LAND_SQUASH;
		float hipY = CORE_Y + drop - sink - Mth.sin(walk * 2.0F) * BOB * amp;
		this.core.resetPose();
		this.core.y = hipY;
		// the hidden body lies face-up on the core, so the insignia plate sits on its top
		this.body.resetPose();
		this.body.y = hipY;
		this.body.xRot = -(float)Math.PI * 0.5F;
		float splay = rooted ? ROOT_SPLAY : crouch ? CROUCH_SPLAY : air * AIR_SPLAY;
		for (int i = 0; i < 6; i++) {
			int row = i % 3;
			int side = i / 3;
			float out = side == 0 ? -1.0F : 1.0F;
			// the tripod: the right front, left mid and right hind swing together, then the other three
			float phase = walk + ((row + side) % 2) * (float)Math.PI;
			float stride = Mth.cos(phase) * STRIDE * amp;
			// swinging forward (sin > 0) the foot lifts; on the ground (sin <= 0) it slides back
			float lift = Math.max(0.0F, Mth.sin(phase)) * LIFT * amp;
			float idle = Mth.sin(age * 0.05F + i * 1.7F) * 0.15F * (1.0F - amp);
			// where the tip goes, from the hip: out, down to the ground (a rooted tip digs in, an airborne one tucks up) and along
			float tx = out * (FOOT_OUT[row] + splay - lift * LIFT_TUCK);
			float ty = ground - hipY - lift + (rooted ? ROOT_SINK : 0.0F) - air * AIR_TUCK;
			float tz = FOOT_DZ[row] + stride + idle + (rooted ? (row - 1) * ROOT_REACH : 0.0F);
			float len = (float)Math.sqrt(tx * tx + ty * ty + tz * tz);
			ModelPart leg = this.legs[i];
			leg.resetPose();
			leg.y = hipY;
			// the leg hangs along +y; x then z turn it (z outward, x fore-aft) onto the tip, and it stretches to reach
			leg.xRot = (float)Math.asin(Mth.clamp(tz / len, -1.0F, 1.0F));
			leg.zRot = (float)Math.atan2(-tx, ty);
			leg.yScale = Mth.clamp(len / LEG_LEN, LEG_MIN, LEG_MAX);
		}
		float flareFall = Math.max(0.0F, m.fall()) * air * FALL_FLARE;
		for (int i = 0; i < CRYSTALS; i++) {
			ModelPart c = this.crystals[i];
			c.resetPose();
			float w = CW[i];
			float side = Math.signum(CRZ[i]);
			float sway = Mth.sin(age * SWAY_RATE + i * 1.3F) * SWAY;
			// each step jolts the spires, harder than Prism's
			c.zRot = CRZ[i] + sway + Mth.sin(walk * 2.0F + i) * JIGGLE * amp - m.trail() * TRAIL_LEAN * w
				+ side * (flareFall + (rooted ? ROOT_FLARE : 0.0F));
			c.xRot = CRX[i] + Mth.cos(age * SWAY_RATE * 0.8F + i * 2.1F) * SWAY * 0.6F + Mth.cos(walk * 2.0F + i * 0.7F) * JIGGLE * 0.5F * amp
				+ swing * THRUST_TILT * w + (crouch ? 0.12F : 0.0F);
			c.z = CZ[i] - swing * THRUST_SHIFT * w;
			float grow = rooted ? 1.0F + ROOT_GROW + Mth.sin(age * 0.15F + i) * 0.02F : 1.0F + Mth.sin(age * 0.1F + i) * 0.012F;
			c.yScale = grow;
			c.xScale = c.zScale = 1.0F + (grow - 1.0F) * 0.5F;
		}
	}
}
