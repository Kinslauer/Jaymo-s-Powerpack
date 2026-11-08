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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * (0.21.37, Ultimates patch 2) Rivet's Ultimate, Ironpour: Rivet's own build (a low rippling puddle of liquid metal, a slab with a mound
 * on it, four lobes round the rim, a wake dragging behind; it stands up to fight into a hunched figure with the torso lifting upright, two
 * stubby legs rising out of the slab and arms growing out of the mass; it pours flat into a long streak) bigger and plated. Riveted
 * gunmetal plates float on the metal in the puddle ({@code plate0..3}) and ride the shoulders, belt, back, forearms and shins when it
 * stands ({@code pauldron_r}, {@code belt}, {@code back_plate}, {@code bracer_r} ...); a ridge of gear-tooth spikes runs down the back
 * ({@code gear0..3}, and {@code fin0..2} rising from the puddle's wake); the brow is plated and the head has a crown. Both arms whip on a
 * swing (the swinging one furthest). With {@code XenoformState.ACT} "slam" (Foundry Slam) it draws itself up into a tall column, arms
 * overhead while it rises and stretched down while it falls. Seams glow steel blue (the skin's glow map, drawn fullbright by the layer) and
 * flare white-hot on a blow ({@link #submitExtras}). It is {@code XenoFormModels.Rivet}'s rig, copied, since that class is not open to
 * other packages; the skin is {@code tools/ultimates/rivet.py}, 128x128, and {@link #UV} here is the table that script prints
 * ({@code rivet.py layout}). A melded shell is drawn by the layer, as the base alien's is.
 * <p>
 * The change-in film zooms on, in order, {@code pool}, {@code mound}, {@code right_arm+left_arm} and {@code head}: those names are parts
 * of this mesh.
 */
public final class UltimateIronpour extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** How fast it rises, melts back down (slowly, or quickly into a pour) and pours flat (share of the way per tick); how fast the slam's column draws up and sinks. */
	static final float RISE_UP = 0.16F, MELT_DOWN = 0.035F, POUR_MELT = 0.25F, POUR_IN = 0.3F, POUR_OUT = 0.1F, TALL_IN = 0.35F, TALL_OUT = 0.12F;
	/** The lying torso: its pivot's height, and the z of its hips' end; the risen stance's forward lean (radians). */
	static final float LIE_Y = 20.7F, LIE_Z = -3.0F, LEAN = 0.3F;
	/** How far down the torso (pixels) its base point is, and how long the torso is. */
	static final float TORSO_REACH = 7.5F, TORSO_LEN = 9.0F;
	/** The legs: hip height standing (the puddle's is {@link #HIP_LOW}), where the feet rest, the box's length and half depth, spread, swing (radians), foot lift and clock. */
	static final float HIP_RISEN = 14.8F, HIP_LOW = 23.0F, FOOT_Y = 23.9F, LEG_LEN = 11.6F, FOOT_HALF = 1.6F, LEG_X = 2.3F, LEG_SWING = 0.55F, FOOT_LIFT = 1.2F, LEG_RATE = 0.6662F;
	/** The arms: smallest scale (just a nub), shoulder spread and drop below the torso's top, box length, flare, the swinging arm's extra stretch and the other arm's share of it. */
	static final float ARM_MIN = 0.05F, ARM_X = 4.5F, ARM_DROP = 2.0F, ARM_LEN = 11.5F, ARM_FLARE = 0.18F, LASH_STRETCH = 1.1F, TWIN_SHARE = 0.55F;
	/** How much of its width the slab, the mound and the lobes' spread keep when it stands. */
	static final float POOL_RISEN = 0.75F, MOUND_RISEN = 0.6F, LOBE_IN = 0.55F;
	/** The head: where the dome sits in the puddle (y, z), how far its neck sinks into the torso, how squashed the dome is. */
	static final float HEAD_Y = 22.5F, HEAD_Z = -5.0F, NECK_SINK = 1.2F, DOME = 0.6F;
	/** The slam's column: how much higher the hips are drawn (pixels), how far the legs fuse together and thicken, the arms' overhead swing (radians) and the extra stretch of a falling arm. */
	static final float TALL_HIP = 6.0F, TALL_FUSE = 0.55F, TALL_THICK = 0.4F, ARMS_UP = -2.8F, SLAM_STRETCH = 1.0F;
	/** The plates on the puddle: how high they float (pixels), how far they bob and drift. */
	static final float PLATE_Y = 21.6F, PLATE_BOB = 0.35F, PLATE_DRIFT = 0.02F;
	/** How far a sneaking renderer sinks the model (pixels). */
	static final float CROUCH_SINK = 2.0F / 0.9375F;
	/** White-hot flare of the seams on a blow (share of full), and how far it lingers. */
	static final float BLOW_FLARE = 0.7F;
	static final int FULL_BRIGHT = 0xF000F0;
	/** The lobes' places on the slab (x, z). */
	private static final float[][] LOBES = {{-6.0F, -6.5F}, {6.0F, -6.0F}, {-6.5F, 5.0F}, {6.5F, 5.5F}};
	/** The plates' places on the slab (x, z). */
	private static final float[][] PLATES = {{-5.5F, -1.0F}, {5.5F, -1.5F}, {-3.0F, 5.5F}, {3.5F, 6.0F}};
	/** The gear spikes down the back (y up the torso from its top, in pixels) and the fins on the wake (z along it). */
	private static final float[] GEARS = {1.0F, 2.9F, 4.8F, 6.7F};
	private static final float[] FINS = {2.0F, 4.6F, 7.2F};
	/** Per-entity smoothing between frames: {age, rise, pour, tall}. */
	private static final Map<Integer, float[]> TRACKS = new java.util.HashMap<>();

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/rivet.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("head", 0, 0), uv("brow", 24, 0), uv("crown", 44, 0), uv("body", 60, 0), uv("pauld", 84, 0), uv("belt", 0, 13), uv("back", 28, 13),
		uv("gear", 42, 13), uv("tooth", 46, 13), uv("arm", 54, 13), uv("bracer", 70, 13), uv("leg", 90, 13), uv("greave", 106, 13),
		uv("pool", 0, 29), uv("mound", 60, 29), uv("lobe", 100, 29), uv("wake", 0, 47), uv("plate", 28, 47), uv("fin", 48, 47));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart pool, mound, wake;
	private final ModelPart[] lobes = new ModelPart[4], plates = new ModelPart[4], fins = new ModelPart[3];
	/** Every part on the root (set afresh each frame, so the crouch's raise never adds up). */
	private final ModelPart[] all;

	public UltimateIronpour() {
		super(XenoFormModels.bake(mesh(), 128, 128), "rivet");
		ModelPart root = this.root();
		this.pool = root.getChild("pool");
		this.mound = root.getChild("mound");
		this.wake = root.getChild("wake");
		java.util.List<ModelPart> every = new java.util.ArrayList<>(java.util.List.of(this.pool, this.mound, this.wake, this.head, this.body,
			this.rightArm, this.leftArm, this.rightLeg, this.leftLeg));
		for (int i = 0; i < this.lobes.length; i++) {
			this.lobes[i] = root.getChild("lobe" + i);
			this.plates[i] = root.getChild("plate" + i);
			every.add(this.lobes[i]);
			every.add(this.plates[i]);
		}
		for (int i = 0; i < this.fins.length; i++) {
			this.fins[i] = root.getChild("fin" + i);
			every.add(this.fins[i]);
		}
		this.all = every.toArray(new ModelPart[0]);
		// the plate sits on the torso's front (local -z), which faces up while it lies down and forward once it stands
		this.insigniaY = 4.5F;
		this.insigniaZ = -2.0F;
		this.insigniaSize = 0.8F;
	}

	/** A box of {@code key}'s patch of the skin, its size {@code w x h x d} (the same as {@code DIMS} in the painter). */
	private static CubeListBuilder cube(String key, float x, float y, float z, int w, int h, int d) {
		return cube(key, x, y, z, w, h, d, CubeDeformation.NONE);
	}

	private static CubeListBuilder cube(String key, float x, float y, float z, int w, int h, int d, CubeDeformation deform) {
		int[] at = UV.get(key);
		return CubeListBuilder.create().texOffs(at[0], at[1]).addBox(x, y, z, w, h, d, deform);
	}

	private static MeshDefinition mesh() {
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
		PartDefinition root = mesh.getRoot();
		// the face: a squat dome (squashed further in the puddle), under a plated brow and a small crown
		PartDefinition head = root.addOrReplaceChild("head", cube("head", -3.0F, -5.0F, -3.0F, 6, 5, 6), PartPose.offset(0.0F, HEAD_Y, HEAD_Z));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("brow", cube("brow", -3.5F, -4.5F, -4.5F, 7, 2, 3), PartPose.ZERO);
		head.addOrReplaceChild("crown", cube("crown", -2.0F, -6.0F, -2.0F, 4, 1, 4), PartPose.ZERO);
		// the torso: 9 long from its top (pivot) down; the puddle lays it face-up on the mound. Pauldrons, a belt, a back plate and a ridge of gear teeth sit on it.
		PartDefinition body = root.addOrReplaceChild("body", cube("body", -4.0F, 0.0F, -2.0F, 8, 9, 4), PartPose.offset(0.0F, LIE_Y, LIE_Z + TORSO_REACH));
		body.addOrReplaceChild("pauldron_r", cube("pauld", -6.5F, -1.0F, -2.5F, 5, 2, 5), PartPose.ZERO);
		body.addOrReplaceChild("pauldron_l", cube("pauld", 1.5F, -1.0F, -2.5F, 5, 2, 5), PartPose.ZERO);
		body.addOrReplaceChild("belt", cube("belt", -4.5F, 6.5F, -2.5F, 9, 2, 5), PartPose.ZERO);
		body.addOrReplaceChild("back_plate", cube("back", -3.0F, 1.0F, 2.0F, 6, 6, 1), PartPose.ZERO);
		for (int i = 0; i < GEARS.length; i++) {
			PartDefinition gear = body.addOrReplaceChild("gear" + i, cube("gear", -0.5F, -3.0F, -0.5F, 1, 3, 1),
				PartPose.offsetAndRotation(0.0F, GEARS[i], 3.2F, -0.7F, 0.0F, 0.0F));
			gear.addOrReplaceChild("tooth" + i, cube("tooth", -1.5F, -2.0F, -0.5F, 3, 1, 1), PartPose.ZERO);
		}
		// arms and legs: slim and centred on their pivots, grown out of the mass (scaled) when it stands; bracers and greaves on them
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", cube("arm", -2.0F, 0.0F, -2.0F, 4, 12, 4, new CubeDeformation(-0.5F)),
			PartPose.offset(-2.0F, LIE_Y, LIE_Z + 4.5F));
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", cube("arm", -2.0F, 0.0F, -2.0F, 4, 12, 4, new CubeDeformation(-0.5F)),
			PartPose.offset(2.0F, LIE_Y, LIE_Z + 4.5F));
		rightArm.addOrReplaceChild("bracer_r", cube("bracer", -2.5F, 6.0F, -2.5F, 5, 4, 5), PartPose.ZERO);
		leftArm.addOrReplaceChild("bracer_l", cube("bracer", -2.5F, 6.0F, -2.5F, 5, 4, 5), PartPose.ZERO);
		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", cube("leg", -2.0F, 0.0F, -2.0F, 4, 12, 4, new CubeDeformation(-0.4F)),
			PartPose.offset(-LEG_X, HIP_LOW, 0.0F));
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg", cube("leg", -2.0F, 0.0F, -2.0F, 4, 12, 4, new CubeDeformation(-0.4F)),
			PartPose.offset(LEG_X, HIP_LOW, 0.0F));
		rightLeg.addOrReplaceChild("greave_r", cube("greave", -2.5F, 5.0F, -2.5F, 5, 4, 5), PartPose.ZERO);
		leftLeg.addOrReplaceChild("greave_l", cube("greave", -2.5F, 5.0F, -2.5F, 5, 4, 5), PartPose.ZERO);
		// the puddle: a wide slab on the ground, a smaller mound on it, lobes round the rim, a wake behind (all pivoted on the ground, so they swell upward)
		root.addOrReplaceChild("pool", cube("pool", -7.0F, -2.0F, -8.0F, 14, 2, 16), PartPose.offset(0.0F, 24.0F, 0.0F));
		root.addOrReplaceChild("mound", cube("mound", -5.0F, -3.0F, -5.0F, 10, 3, 10), PartPose.offset(0.0F, 22.2F, 0.0F));
		for (int i = 0; i < LOBES.length; i++) {
			root.addOrReplaceChild("lobe" + i, cube("lobe", -2.0F, -3.0F, -2.0F, 4, 3, 4), PartPose.offset(LOBES[i][0], 24.0F, LOBES[i][1]));
			root.addOrReplaceChild("plate" + i, cube("plate", -2.5F, -1.0F, -2.5F, 5, 1, 5), PartPose.offset(PLATES[i][0], PLATE_Y, PLATES[i][1]));
		}
		root.addOrReplaceChild("wake", cube("wake", -3.0F, -2.0F, 0.0F, 6, 2, 8), PartPose.offset(0.0F, 24.0F, 4.5F));
		for (int i = 0; i < FINS.length; i++) {
			root.addOrReplaceChild("fin" + i, cube("fin", -1.0F, -4.0F, -0.5F, 2, 4, 1), PartPose.offsetAndRotation(0.0F, 24.0F, 4.5F + FINS[i], -0.35F, 0.0F, 0.0F));
		}
		return mesh;
	}

	/** {rise, pour, tall} smoothed per entity over its own age (so a second draw in the same frame changes nothing). */
	private static float[] track(int id, float age, float riseTo, float pourTo, float tallTo) {
		if (TRACKS.size() > 64) {
			TRACKS.clear();
		}
		float[] t = TRACKS.get(id);
		if (t == null) {
			t = new float[] {age, riseTo, pourTo, tallTo};
			TRACKS.put(id, t);
			return t;
		}
		float dt = age - t[0];
		if (dt < 0.0F || dt > 40.0F) {
			t[0] = age;
			t[1] = riseTo;
			t[2] = pourTo;
			t[3] = tallTo;
		} else if (dt > 0.0F) {
			t[0] = age;
			t[1] += Mth.clamp(riseTo - t[1], -(pourTo > 0.5F ? POUR_MELT : MELT_DOWN) * dt, RISE_UP * dt);
			t[2] += Mth.clamp(pourTo - t[2], -POUR_OUT * dt, POUR_IN * dt);
			t[3] += Mth.clamp(tallTo - t[3], -TALL_OUT * dt, TALL_IN * dt);
		}
		return t;
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		// keep what the swing and the held item did to the arms and body, then start the parts afresh
		float twist = this.body.yRot;
		float[] ra = {this.rightArm.xRot, this.rightArm.yRot, this.rightArm.zRot};
		float[] la = {this.leftArm.xRot, this.leftArm.yRot, this.leftArm.zRot};
		for (ModelPart part : this.all) {
			part.resetPose();
		}
		XenoMotion.Read m = XenoMotion.read(state);
		float age = state.ageInTicks;
		String act = state.getData(XenoformClient.ACT);
		float swingT = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
		float swing = Mth.sin(swingT * (float)Math.PI);
		boolean slam = "slam".equals(act);
		boolean pouring = "pour".equals(act);
		// it stands to fight, and melts when the fight's over, when it sneaks, or when it rides; pouring it is flat; the slam draws it up into a column
		boolean stand = !pouring && !state.isCrouching && !state.isPassenger && (slam || "fight".equals(act) || swingT > 0.0F);
		float[] t = track(state.id, age, stand ? 1.0F : 0.0F, pouring ? 1.0F : 0.0F, slam ? 1.0F : 0.0F);
		float e = smooth(t[1]);
		float p = smooth(t[2]);
		float tall = smooth(t[3]);
		float armsUp = tall * (1.0F - Mth.clamp(m.fall() * 3.0F, 0.0F, 1.0F));
		float armsDown = tall * Mth.clamp(m.fall() * 3.0F, 0.0F, 1.0F);
		this.rightArm.xRot = Mth.lerp(armsUp, Mth.lerp(armsDown, ra[0], -0.2F), ARMS_UP);
		this.rightArm.yRot = ra[1];
		this.rightArm.zRot = ra[2];
		this.leftArm.xRot = Mth.lerp(armsUp, Mth.lerp(armsDown, la[0], -0.2F), ARMS_UP);
		this.leftArm.yRot = la[1];
		this.leftArm.zRot = la[2];
		float speed = Mth.clamp(state.walkAnimationSpeed * 1.3F, 0.0F, 1.0F);
		float flow = state.walkAnimationPos * 0.9F;
		float breath = m.breath();

		// ---- the puddle: the slab ripples and stretches with its flow; the mound leans into the way it goes ----
		float surge = Mth.sin(flow) * 0.08F * speed;
		float shrink = Mth.lerp(e, 1.0F, POOL_RISEN);
		this.pool.xScale = shrink * (1.0F - 0.5F * surge) * (1.0F - 0.15F * p);
		this.pool.zScale = shrink * (1.0F + surge + 0.12F * speed) * (1.0F + 0.9F * p);
		this.pool.yScale = (1.0F + 0.1F * breath) * (1.0F - 0.35F * p);
		float moundWide = Mth.lerp(e, 1.0F, MOUND_RISEN) * (1.0F - 0.2F * p);
		this.mound.xScale = moundWide;
		this.mound.zScale = moundWide * (1.0F + 0.5F * p);
		this.mound.yScale = (1.0F + 0.08F * breath) * Mth.lerp(e, 1.0F, 0.6F) * (1.0F - 0.5F * p);
		this.mound.xRot = speed * 0.12F * (1.0F - e);
		this.mound.yRot = m.turn() * 0.2F;
		for (int i = 0; i < this.lobes.length; i++) {
			ModelPart lobe = this.lobes[i];
			float ph = age * 0.11F + i * 1.9F;
			float h = 0.85F + 0.25F * Mth.sin(ph) + 0.45F * speed * Mth.sin(flow + i * 1.57F);
			h = h * Mth.lerp(e, 1.0F, 0.6F) + swing * 0.5F * e;
			lobe.yScale = Mth.clamp(h * (1.0F - 0.5F * p), 0.3F, 1.5F);
			float in = Mth.lerp(e, 1.0F, LOBE_IN) * (1.0F + 0.05F * Mth.sin(ph));
			lobe.x = LOBES[i][0] * in;
			lobe.z = LOBES[i][1] * in + p * (LOBES[i][1] > 0.0F ? 3.0F : 4.0F);
			lobe.zScale = 1.0F + 1.2F * p;
			// the plates float on the metal, bobbing and drifting; they melt away into the torso's own as it stands
			ModelPart plate = this.plates[i];
			float away = 1.0F - smooth(e * 1.6F);
			plate.x = PLATES[i][0] * in;
			plate.z = PLATES[i][1] * in + p * (PLATES[i][1] > 0.0F ? 3.0F : 4.0F);
			plate.y = PLATE_Y - PLATE_BOB * (1.0F + Mth.sin(ph + 0.8F)) * 0.5F;
			plate.yRot = age * PLATE_DRIFT * (i % 2 == 0 ? 1.0F : -1.0F) + i;
			plate.xScale = plate.zScale = away * (1.0F + 0.5F * p);
			plate.yScale = away;
		}
		// the wake drags behind, swinging with its turns, and streams out when it pours; the gear fins rise out of it
		this.wake.yScale = (0.8F + 0.2F * breath) * (1.0F - 0.3F * p) * Mth.lerp(e, 1.0F, 0.5F);
		this.wake.zScale = (1.0F + 0.5F * speed) * (1.0F + 2.0F * p) * Mth.lerp(e, 1.0F, 0.6F);
		this.wake.xScale = 1.0F - 0.2F * p;
		this.wake.yRot = m.trail() * 0.5F;
		for (int i = 0; i < this.fins.length; i++) {
			ModelPart fin = this.fins[i];
			float up = (1.0F - smooth(e * 1.4F)) * (1.0F - 0.5F * p) * (1.0F + 0.15F * breath);
			fin.yScale = up;
			fin.xScale = fin.zScale = Math.max(0.01F, up);
			float along = FINS[i] * this.wake.zScale;
			fin.z = 4.5F + along * Mth.cos(this.wake.yRot);
			fin.x = along * Mth.sin(this.wake.yRot);
			fin.yRot = this.wake.yRot;
		}

		// ---- the legs: stubby columns that rise out of the slab; each foot stays on the floor, lifting only as it swings forward; the slam fuses them into a column ----
		float hip = Mth.lerp(e, HIP_LOW, HIP_RISEN) - TALL_HIP * tall;
		float reach = FOOT_Y - hip;
		float amp = e * LEG_SWING * Mth.clamp(state.walkAnimationSpeed * 1.4F, 0.0F, 1.0F) * (1.0F - tall);
		float ph = state.walkAnimationPos * LEG_RATE;
		float stepR = Mth.cos(ph) * amp;
		float stepL = -stepR;
		float lift = FOOT_LIFT * e * Mth.clamp(state.walkAnimationSpeed * 1.4F, 0.0F, 1.0F);
		float bob = "fight".equals(act) ? Mth.sin(age * 0.3F) * 0.4F * e : 0.0F;
		// (a swung leg's toe or heel corner dips by its half depth times the swing's sine: the hip rises that much so it never sinks into the floor)
		float yR = hip + reach * (1.0F - Mth.cos(stepR)) - FOOT_HALF * Math.abs(Mth.sin(stepR)) - Math.max(0.0F, Mth.sin(ph)) * lift + bob;
		float yL = hip + reach * (1.0F - Mth.cos(stepL)) - FOOT_HALF * Math.abs(Mth.sin(stepL)) - Math.max(0.0F, -Mth.sin(ph)) * lift + bob;
		float legScale = reach / LEG_LEN;
		float legX = LEG_X * (1.0F - TALL_FUSE * tall);
		this.rightLeg.setPos(-legX, yR, 0.0F);
		this.leftLeg.setPos(legX, yL, 0.0F);
		this.rightLeg.xRot = stepR;
		this.leftLeg.xRot = stepL;
		this.rightLeg.yScale = this.leftLeg.yScale = legScale;
		this.rightLeg.xScale = this.leftLeg.xScale = this.rightLeg.zScale = this.leftLeg.zScale = 1.0F + TALL_THICK * tall;

		// ---- the torso: lying face-up on the mound, it swings up to a hunch (straight in the column); its base point follows the hips ----
		float lean = (LEAN + 0.04F * breath + swing * 0.12F) * (1.0F - 0.8F * tall);
		float a = Mth.lerp(e, -(float)Math.PI * 0.5F, lean);
		float cs = Mth.cos(a);
		float sn = Mth.sin(a);
		float baseY = Mth.lerp(e, LIE_Y, (yR + yL) * 0.5F) + p * 1.0F;
		float baseZ = Mth.lerp(e, LIE_Z, 0.0F);
		this.body.setPos(0.0F, baseY - TORSO_REACH * cs, baseZ - TORSO_REACH * sn);
		this.body.xRot = a;
		this.body.yRot = twist * e;
		this.body.zRot = (Mth.sin(age * 0.09F) * 0.03F + m.turn() * 0.08F) * e;

		// ---- the head: a squashed dome at the front of the puddle; on the torso's top once it stands ----
		float along = Mth.lerp(e, TORSO_LEN, NECK_SINK);
		this.head.setPos(0.0F, this.body.y + along * cs + (1.0F - e) * (HEAD_Y - LIE_Y), this.body.z + along * sn + (1.0F - e) * (HEAD_Z - (LIE_Z + TORSO_REACH - TORSO_LEN)));
		this.head.yScale = Mth.lerp(e, DOME, 1.0F) * (1.0F - 0.3F * p);
		this.head.xScale = Mth.lerp(e, 1.2F, 1.0F);
		this.head.zScale = Mth.lerp(e, 1.15F, 1.0F) * (1.0F + 0.8F * p);
		this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.2F, 1.2F) * Mth.lerp(e, 0.7F, 1.0F);
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD * Mth.lerp(e, 0.3F, 0.9F) + e * 0.1F;
		this.head.zRot = -m.turn() * 0.1F;

		// ---- the arms: nubs inside the torso until it stands, then they grow out of its sides; both whip on a swing, the swinging one furthest ----
		float armS = ARM_MIN + (1.0F - ARM_MIN) * e;
		float wob = Mth.sin(age * 0.15F) * 0.05F * e;
		float sx = Mth.lerp(e, 2.0F, ARM_X);
		float sy = this.body.y + ARM_DROP * cs;
		float sz = this.body.z + ARM_DROP * sn;
		float tw = this.body.yRot;
		boolean offSwing = state.currentSwing != null && state.currentSwing.hand() == net.minecraft.world.InteractionHand.OFF_HAND;
		boolean rightLash = (state.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT) != offSwing;
		float whip = swing * LASH_STRETCH * e + armsDown * SLAM_STRETCH;
		this.arm(this.rightArm, -1, sx, sy, sz, tw, armS, rightLash ? whip : whip * TWIN_SHARE, wob);
		this.arm(this.leftArm, 1, sx, sy, sz, tw, armS, rightLash ? whip * TWIN_SHARE : whip, -wob);
		// a sneaking player is drawn CROUCH_SINK lower: everything raised that much, so the puddle stays on the floor
		if (state.isCrouching) {
			for (ModelPart part : this.all) {
				part.y -= CROUCH_SINK;
			}
		}
	}

	/** One arm at its shoulder, scaled out of the mass, flared a little, stretched by {@code stretch} but never past the floor. */
	private void arm(ModelPart arm, int side, float sx, float sy, float sz, float twist, float scale, float stretch, float wobble) {
		arm.setPos(side * Mth.cos(twist) * sx, sy, -side * Mth.sin(twist) * sx + sz);
		arm.zRot -= side * (ARM_FLARE * (scale - ARM_MIN) / (1.0F - ARM_MIN) + wobble);
		float s = scale * (1.0F + stretch);
		float down = ARM_LEN * Math.max(0.0F, Mth.cos(arm.xRot) * Mth.cos(arm.zRot));
		if (down > 0.01F) {
			s = Math.min(s, Math.max(scale, (FOOT_Y - sy) / down));
		}
		arm.xScale = arm.zScale = scale;
		arm.yScale = s;
	}

	/** The seams flare white-hot on a blow and through the slam, drawn over the skin's own glow. */
	@Override
	public void submitExtras(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, SubmitNodeCollector collector, int light) {
		float flare = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI) * BLOW_FLARE;
		if ("slam".equals(state.getData(XenoformClient.ACT))) {
			flare = 1.0F;
		}
		if (flare < 0.04F) {
			return;
		}
		collector.order(1).submitModel(this, state, poseStack, RenderTypes.entityTranslucentEmissive(this.ultimateGlow), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
			(int)(flare * 255.0F) << 24 | 0xFFFFFF, null, 0);
	}
}
