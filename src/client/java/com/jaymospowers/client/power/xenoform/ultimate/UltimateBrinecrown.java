package com.jaymospowers.client.power.xenoform.ultimate;

import com.jaymospowers.client.power.xenoform.XenoFormModels;
import com.jaymospowers.client.power.xenoform.XenoMotion;
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
 * (0.21.37, Ultimates patch 2) Slipfin's Ultimate, Brinecrown: Slipfin's own axolotl (the same build and gait: a belly crawl on land
 * with the legs paddling in diagonal pairs and the trunk and tail bending into a travelling S, an undulating swim with the legs
 * tucked back, a darting bite) grown longer and plated. Pearly scale plates run down the back of the rear trunk, a spiked fin sail
 * stands along the spine and the tail, the head is wider with spikes at the brow and cheeks, and the six gill stalks are long frilled
 * crowns with glowing tips. The seams, the sail's edge, the gill tips and the spike points glow coral pink (the skin's glow map,
 * drawn fullbright by the layer). The sail rides back as it swims and the crowns flare wider.
 * <p>
 * The skin is {@code tools/ultimates/slipfin.py}, 128x128; {@link #UV} here is the table that script prints ({@code slipfin.py layout}).
 * The change-in film zooms on, in order, {@code tail_0..tail_3} (the sail grows on them), {@code rear}, {@code jaw} and
 * {@code gill_0..gill_5}: those names are parts of this mesh.
 */
public final class UltimateBrinecrown extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** Model y of the ground, of the trunk's centre belly-crawling, how far sneaking sinks it, and swimming. */
	static final float GROUND = 24.0F, CRAWL_Y = 20.0F, CROUCH_DROP = 0.4F, SWIM_Y = 13.5F;
	/** The renderer draws a sneaking player this many pixels lower: the animal is raised that much to stay on the ground. */
	static final float CROUCH_SINK = 2.0F;
	/** Where the front trunk's centre sits (model z), so the whole, longer length is centred on the player. */
	static final float HUB_Z = -9.5F;
	/** A leg's length (its box height), and the lowest its pivot may sit on the trunk. */
	static final float LEG_LEN = 3.0F, LEG_TOP_MAX = 1.8F;
	/** The paddling gait: how fast against the walk, how far a leg swings (radians), how high a stepping foot lifts (px). */
	static final float GAIT = 0.6662F * 1.25F, LEG_SWING = 0.7F, LEG_LIFT = 1.1F;
	/** Splay: a leg's outward angle at rest and sneaking, and how much more it opens mid-step (radians). */
	static final float SPLAY = 0.35F, SPLAY_CROUCH = 0.7F, SPLAY_STEP = 0.2F;
	/** How far the walk reads as full (walk speed times this, capped at 1), and the idle sway at a standstill. */
	static final float WALK_FULL = 1.6F, IDLE_SWAY = 0.35F;
	/** The land S-wave: each joint's yaw amplitude and phase (front trunk, rear trunk, then the four tail segments). */
	private static final float[] WAVE_YAW = {0.12F, 0.14F, 0.2F, 0.28F, 0.36F, 0.44F};
	private static final float[] WAVE_PHASE = {0.0F, -3.1416F, -3.9F, -4.7F, -5.5F, -6.3F};
	/** The swim wave: each joint's pitch amplitude (radians), the lag between joints, and its speed. */
	private static final float[] SWIM_PITCH = {0.04F, 0.07F, 0.11F, 0.16F, 0.21F, 0.26F};
	static final float SWIM_LAG = 0.75F, SWIM_RATE = 0.33F, SWIM_STRIDE = 1.0F, SWIM_LOOK = 0.35F;
	/** Swimming: the legs' tuck (back along the body), their outward angle, and their flutter. */
	static final float TUCK = 1.25F, TUCK_SPLAY = 0.2F, TUCK_FLUTTER = 0.12F;
	/** How far the head may turn on its neck, how far it nods up and down (radians); lunge: reach (px) and jaw (radians). */
	static final float HEAD_TURN = 0.75F, NOD_UP = 0.45F, NOD_DOWN = 0.04F, LUNGE_REACH = 2.2F, LUNGE_JAW = 0.6F;
	/** The skull's pivot height on the front trunk, and how far it rises as the nose tips up. */
	static final float SKULL_Y = 1.0F, SKULL_LIFT = 3.0F;
	/** The gill crowns' heights on the skull's sides and their fan (radians, + is up): top, middle, bottom. */
	private static final float[] GILL_Y = {-1.5F, -0.5F, 0.5F};
	private static final float[] GILL_FAN = {0.8F, 0.4F, 0.0F};
	/** The sail: how far it leans back at rest, how far more at full speed and swimming (radians), and its ripple. */
	static final float SAIL_REST = -0.3F, SAIL_SPEED = -0.25F, SAIL_SWIM = -0.15F, SAIL_RIPPLE = 0.06F;
	private static final int JOINTS = 6;
	/** Where the rear trunk hangs off the front one (hub z), the hind legs' z on it, and the front legs' z on the hub. */
	private static final float REAR_Z = 3.0F, HIND_LEG_Z = 4.0F, FORE_LEG_Z = 0.5F;
	/** How far out the legs' pivots sit: the front pair on the front trunk, the hind pair on the (slightly shrunk) rear one. */
	private static final float FORE_LEG_X = 3.5F, HIND_LEG_X = 3.2F;
	/** Tail segments: depth (their next one hangs one pixel inside the end). */
	private static final float[] TAIL_DEPTH = {6.0F, 6.0F, 6.0F, 5.0F};
	/** The sail's spikes: on the front trunk, the rear trunk (each with its z), then one on each tail segment (its z). */
	private static final float[] HUB_SAIL_Z = {-3.0F, 3.0F};
	private static final float[] REAR_SAIL_Z = {1.5F, 6.5F};
	private static final float[] TAIL_SAIL_Z = {3.0F, 3.0F, 3.0F, 2.5F};

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/slipfin.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("hub", 0, 0), uv("rear", 28, 0), uv("skull", 56, 0), uv("jaw", 92, 0), uv("gill", 0, 12), uv("gfan", 16, 12),
		uv("gtip", 22, 12), uv("leg", 30, 12), uv("t0", 40, 12), uv("t1", 60, 12), uv("t2", 78, 12), uv("t3", 94, 12),
		uv("scale", 108, 12), uv("sail", 0, 23), uv("sails", 8, 23), uv("spike", 14, 23));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart hub, skull, jaw, rear;
	private final ModelPart[] gills = new ModelPart[6];
	private final ModelPart[] tail = new ModelPart[4];
	/** Front right, front left, hind right, hind left. */
	private final ModelPart[] legs = new ModelPart[4];
	/** The sail's spikes: hub 2, rear 2, tail 4. */
	private final ModelPart[] sail = new ModelPart[8];
	/** Each joint's yaw and pitch, from the front trunk to the tail tip (relative to the world, not the joint before). */
	private final float[] yaw = new float[JOINTS], pitch = new float[JOINTS];

	public UltimateBrinecrown() {
		super(XenoFormModels.bake(mesh(), 128, 128), "slipfin");
		ModelPart root = this.root();
		this.hub = root.getChild("hub");
		this.skull = this.hub.getChild("skull");
		this.jaw = this.skull.getChild("jaw");
		this.rear = this.hub.getChild("rear");
		for (int i = 0; i < 6; i++) {
			this.gills[i] = this.skull.getChild("gill_" + i);
		}
		ModelPart parent = this.rear;
		for (int i = 0; i < 4; i++) {
			this.tail[i] = parent.getChild("tail_" + i);
			parent = this.tail[i];
		}
		this.legs[0] = this.hub.getChild("leg_0");
		this.legs[1] = this.hub.getChild("leg_1");
		this.legs[2] = this.rear.getChild("leg_2");
		this.legs[3] = this.rear.getChild("leg_3");
		this.sail[0] = this.hub.getChild("sail_h0");
		this.sail[1] = this.hub.getChild("sail_h1");
		this.sail[2] = this.rear.getChild("sail_r0");
		this.sail[3] = this.rear.getChild("sail_r1");
		for (int i = 0; i < 4; i++) {
			this.sail[4 + i] = this.tail[i].getChild("sail_t" + i);
		}
		// the plate lies on the back of the front trunk (the hidden body is laid face-up there each frame)
		this.insigniaY = 0.0F;
		this.insigniaZ = 0.0F;
		this.insigniaSize = 0.9F;
	}

	/** A box of {@code key}'s patch of the skin, its size {@code w x h x d} (the same as {@code DIMS} in the painter). */
	private static CubeListBuilder cube(String key, boolean mirror, float x, float y, float z, int w, int h, int d, CubeDeformation grow) {
		int[] at = UV.get(key);
		CubeListBuilder builder = CubeListBuilder.create().texOffs(at[0], at[1]);
		if (mirror) {
			builder.mirror();
		}
		return builder.addBox(x, y, z, w, h, d, grow);
	}

	private static MeshDefinition mesh() {
		// the man's parts stay (hidden) for the first-person hand and the insignia plate
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
		PartDefinition root = mesh.getRoot();
		CubeDeformation none = CubeDeformation.NONE;
		CubeDeformation shrink = new CubeDeformation(-0.3F);
		CubeDeformation proud = new CubeDeformation(0.2F);
		// the front trunk (6 wide, 4 tall, 8 long), centred on its pivot
		PartDefinition hub = root.addOrReplaceChild("hub", cube("hub", false, -3.0F, -2.0F, -4.0F, 6, 4, 8, none), PartPose.offset(0.0F, CRAWL_Y, HUB_Z));
		// the head: a wide flat skull reaching back inside the trunk, and the jaw under it
		PartDefinition skull = hub.addOrReplaceChild("skull", cube("skull", false, -4.5F, -2.0F, -6.0F, 9, 3, 9, none), PartPose.offset(0.0F, SKULL_Y, -3.5F));
		skull.addOrReplaceChild("jaw", cube("jaw", false, -3.5F, 0.0F, -5.0F, 7, 1, 5, none), PartPose.offset(0.0F, 1.0F, -1.0F));
		// spikes at the brow (leaning forward and out) and the cheeks (out and back)
		skull.addOrReplaceChild("brow_r", cube("spike", false, -0.5F, -3.0F, -0.5F, 1, 3, 1, none), PartPose.offsetAndRotation(-3.0F, -2.0F, -4.5F, -0.6F, 0.0F, -0.25F));
		skull.addOrReplaceChild("brow_l", cube("spike", false, -0.5F, -3.0F, -0.5F, 1, 3, 1, none), PartPose.offsetAndRotation(3.0F, -2.0F, -4.5F, -0.6F, 0.0F, 0.25F));
		skull.addOrReplaceChild("cheek_r", cube("spike", false, -0.5F, -3.0F, -0.5F, 1, 3, 1, none), PartPose.offsetAndRotation(-4.5F, -0.5F, -1.5F, 0.0F, 0.0F, -1.35F));
		skull.addOrReplaceChild("cheek_l", cube("spike", false, -0.5F, -3.0F, -0.5F, 1, 3, 1, none), PartPose.offsetAndRotation(4.5F, -0.5F, -1.5F, 0.0F, 0.0F, 1.35F));
		// three gill crowns a side: a 6 long stalk, a frill standing up off it and a bright tip; fanned from steeply up to level; the left ones mirror
		for (int i = 0; i < 6; i++) {
			boolean left = i >= 3;
			int row = i % 3;
			float s = left ? 1.0F : -1.0F;
			CubeListBuilder gill = CubeListBuilder.create();
			int[] g = UV.get("gill"), f = UV.get("gfan"), t = UV.get("gtip");
			gill.texOffs(g[0], g[1]);
			if (left) {
				gill.mirror();
			}
			gill.addBox(left ? 0.0F : -6.0F, -0.5F, -1.0F, 6.0F, 1.0F, 2.0F);
			gill.texOffs(f[0], f[1]).addBox(left ? 2.5F : -3.5F, -2.0F, -1.0F, 1.0F, 4.0F, 2.0F);
			gill.texOffs(t[0], t[1]).addBox(left ? 6.0F : -8.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F);
			skull.addOrReplaceChild("gill_" + i, gill, PartPose.offsetAndRotation(s * 4.5F, GILL_Y[row], 0.0F, 0.0F, 0.0F, -s * GILL_FAN[row]));
		}
		// the rear trunk hangs one pixel inside the front one's end (a hair smaller, so their faces never share a plane)
		PartDefinition rear = hub.addOrReplaceChild("rear", cube("rear", false, -3.0F, -2.0F, 0.0F, 6, 4, 8, shrink), PartPose.offset(0.0F, 0.0F, REAR_Z));
		// pearly scale plates down the back of the rear trunk, standing a little proud of it
		rear.addOrReplaceChild("scale_r0", cube("scale", false, -2.5F, -2.7F, 0.5F, 5, 1, 3, proud), PartPose.ZERO);
		rear.addOrReplaceChild("scale_r1", cube("scale", false, -2.5F, -2.7F, 4.5F, 5, 1, 3, proud), PartPose.ZERO);
		// the tail: four segments, each hung one pixel inside the end of the one before, deep and flat like a fin
		PartDefinition t0 = rear.addOrReplaceChild("tail_0", cube("t0", false, -2.0F, -2.5F, 0.0F, 4, 5, 6, none), PartPose.offset(0.0F, 0.0F, 7.0F));
		PartDefinition t1 = t0.addOrReplaceChild("tail_1", cube("t1", false, -1.5F, -2.5F, 0.0F, 3, 5, 6, shrink), PartPose.offset(0.0F, 0.0F, TAIL_DEPTH[0] - 1.0F));
		PartDefinition t2 = t1.addOrReplaceChild("tail_2", cube("t2", false, -1.0F, -2.0F, 0.0F, 2, 4, 6, none), PartPose.offset(0.0F, 0.0F, TAIL_DEPTH[1] - 1.0F));
		PartDefinition t3 = t2.addOrReplaceChild("tail_3", cube("t3", false, -1.0F, -1.5F, 0.0F, 2, 3, 5, shrink), PartPose.offset(0.0F, 0.0F, TAIL_DEPTH[2] - 1.0F));
		// the fin sail: a spike on the front trunk (either side of the insignia), two on the rear, one on each tail segment
		for (int i = 0; i < 2; i++) {
			hub.addOrReplaceChild("sail_h" + i, cube("sail", false, -0.5F, -4.0F, -1.5F, 1, 4, 3, none), PartPose.offsetAndRotation(0.0F, -2.0F, HUB_SAIL_Z[i], SAIL_REST, 0.0F, 0.0F));
			rear.addOrReplaceChild("sail_r" + i, cube("sail", false, -0.5F, -4.0F, -1.5F, 1, 4, 3, none), PartPose.offsetAndRotation(0.0F, -2.7F, REAR_SAIL_Z[i], SAIL_REST, 0.0F, 0.0F));
		}
		float[] tailTop = {-2.5F, -2.5F, -2.0F, -1.5F};
		PartDefinition[] tails = {t0, t1, t2, t3};
		for (int i = 0; i < 4; i++) {
			tails[i].addOrReplaceChild("sail_t" + i, cube("sails", false, -0.5F, -3.0F, -1.0F, 1, 3, 2, none), PartPose.offsetAndRotation(0.0F, tailTop[i], TAIL_SAIL_Z[i], SAIL_REST, 0.0F, 0.0F));
		}
		// the legs: stubby 2x3x3 paddles, their inner face half a pixel inside the trunk; right then left, front pair on the front trunk
		for (int i = 0; i < 4; i++) {
			boolean left = (i & 1) == 1;
			PartDefinition parent = i < 2 ? hub : rear;
			parent.addOrReplaceChild("leg_" + i, cube("leg", left, -1.0F, 0.0F, -1.5F, 2, 3, 3, none),
				PartPose.offset((left ? 1.0F : -1.0F) * (i < 2 ? FORE_LEG_X : HIND_LEG_X), 1.2F, i < 2 ? FORE_LEG_Z : HIND_LEG_Z));
		}
		return mesh;
	}

	/** How far a tail rises: up as it falls, whipping down on landing, low when crouched (radians, + is up). */
	private static float tailLift(XenoMotion.Read m) {
		return Math.max(0.0F, m.fall()) * 0.5F * m.air() - m.land() * 0.7F - (m.crouching() ? 0.35F : 0.0F);
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		// the man's parts are all hidden; the hidden body only carries the insignia
		this.head.visible = this.hat.visible = this.body.visible = false;
		this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
		this.body.resetPose();
		XenoMotion.Read m = XenoMotion.read(state);
		float age = state.ageInTicks;
		boolean swim = state.isInWater;
		float speed = Math.min(1.0F, state.walkAnimationSpeed * WALK_FULL);
		float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
		// a sneaking player is drawn CROUCH_SINK lower: the ground, in model y, is that much higher
		float sink = state.isCrouching ? CROUCH_SINK : 0.0F;
		float ground = GROUND - sink;
		float hubY;
		if (swim) {
			hubY = SWIM_Y - sink + Mth.sin(age * 0.09F) * 0.6F;
			// the nose pitched where it looks (more as it moves), a wave running down the length of it
			float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.7F, -SWIM_LOOK, SWIM_LOOK) * (0.4F + 0.6F * speed);
			float t = age * SWIM_RATE * 0.5F + state.walkAnimationPos * SWIM_STRIDE;
			float amp = 0.45F + 0.55F * speed;
			for (int j = 0; j < JOINTS; j++) {
				this.pitch[j] = look + SWIM_PITCH[j] * amp * Mth.sin(t * 2.0F - j * SWIM_LAG);
				this.yaw[j] = 0.05F * Mth.sin(age * 0.07F - j * 0.5F);
			}
		} else {
			hubY = CRAWL_Y + (state.isCrouching ? CROUCH_DROP : 0.0F) - sink;
			float phi = state.walkAnimationPos * GAIT;
			float idle = (1.0F - speed) * IDLE_SWAY;
			// how far the tail is lifted (it flares as it falls, whips down on landing), spread along its length
			float lift = Mth.clamp(tailLift(m), -0.15F, 0.5F);
			for (int j = 0; j < JOINTS; j++) {
				this.yaw[j] = WAVE_YAW[j] * (speed * Mth.sin(phi + WAVE_PHASE[j]) + idle * Mth.sin(age * 0.05F - j * 0.8F));
				this.pitch[j] = 0.0F;
				if (j >= 2) {
					this.yaw[j] -= m.trail() * 0.1F * (j - 1);
					this.pitch[j] = lift * (j - 1) * 0.25F;
				}
			}
		}
		// the spine: the front trunk is placed in the world, each joint after it bends by the difference in angle
		// (swimming, the whole animal pitches about its middle, not about the front trunk)
		float tilt = swim ? this.pitch[0] : 0.0F;
		this.hub.x = 0.0F;
		this.hub.y = hubY - HUB_Z * Mth.sin(tilt);
		this.hub.z = HUB_Z * Mth.cos(tilt);
		this.hub.xRot = this.pitch[0];
		this.hub.yRot = this.yaw[0];
		this.hub.zRot = 0.0F;
		this.rear.xRot = this.pitch[1] - this.pitch[0];
		this.rear.yRot = this.yaw[1] - this.yaw[0];
		for (int i = 0; i < 4; i++) {
			this.tail[i].xRot = this.pitch[i + 2] - this.pitch[i + 1];
			this.tail[i].yRot = this.yaw[i + 2] - this.yaw[i + 1];
		}
		// the insignia: the hidden body laid face-up on the middle of the front trunk's back, tilted and turned with it
		float sp = Mth.sin(this.pitch[0]);
		this.body.x = -2.0F * sp * Mth.sin(this.yaw[0]);
		this.body.y = this.hub.y - 2.0F * Mth.cos(this.pitch[0]);
		this.body.z = this.hub.z - 2.0F * sp * Mth.cos(this.yaw[0]);
		this.body.xRot = this.pitch[0] - Mth.HALF_PI;
		this.body.yRot = this.yaw[0];
		this.legs(state, m, swim, ground, hubY, speed, age);
		this.face(state, m, swim, speed, age, bite);
		this.fins(swim, speed, age);
	}

	/** The sail: leaning back more as it moves and swims, rippling down the spine, and the tail's spikes following its bend. */
	private void fins(boolean swim, float speed, float age) {
		float lean = SAIL_REST + SAIL_SPEED * speed + (swim ? SAIL_SWIM : 0.0F);
		for (int i = 0; i < this.sail.length; i++) {
			this.sail[i].xRot = lean + Mth.sin(age * 0.17F - i * 0.7F) * SAIL_RIPPLE * (0.5F + speed);
		}
	}

	/** The legs: paddling in diagonal pairs on land (feet planted through the stance), tucked back and fluttering swimming. */
	private void legs(AvatarRenderState state, XenoMotion.Read m, boolean swim, float ground, float hubY, float speed, float age) {
		float phi = state.walkAnimationPos * GAIT;
		for (int k = 0; k < 4; k++) {
			boolean left = (k & 1) == 1;
			ModelPart leg = this.legs[k];
			if (swim) {
				leg.xRot = TUCK + TUCK_FLUTTER * Mth.sin(age * 0.3F + k * 1.3F);
				leg.zRot = (left ? -1.0F : 1.0F) * TUCK_SPLAY;
				leg.y = 1.0F;
				continue;
			}
			// front right with hind left, front left with hind right
			float ph = phi + ((k == 0 || k == 3) ? 0.0F : (float)Math.PI);
			// + swings the foot back (the stance, on the ground), - brings it forward (the swing, lifted)
			float a = LEG_SWING * speed * Mth.sin(ph);
			float step = Math.max(0.0F, -Mth.cos(ph)) * speed;
			// in the air the legs dangle and kick
			a += m.air() * (0.5F + 0.3F * Mth.sin(age * 0.5F + k * 1.7F));
			float splay = (state.isCrouching ? SPLAY_CROUCH : SPLAY) + SPLAY_STEP * step;
			leg.xRot = a;
			leg.zRot = (left ? -1.0F : 1.0F) * splay;
			// the pivot rides so the lowest corner of the paw stays on the ground (up by the lift on the swing), and
			// never so low that the leg's top leaves the trunk's side
			leg.y = Math.min(LEG_TOP_MAX, ground - hubY - drop(a, (left ? -1.0F : 1.0F) * splay) - LEG_LIFT * step);
		}
	}

	/** How far below its pivot a leg's lowest corner hangs once turned by {@code a} (forward-back) and {@code zr} (out). */
	private static float drop(float a, float zr) {
		float lowest = -1.0E9F;
		for (int x = -1; x <= 1; x += 2) {
			for (int y = 0; y <= 1; y++) {
				for (int z = -1; z <= 1; z += 2) {
					float y1 = y * LEG_LEN * Mth.cos(a) - z * 1.5F * Mth.sin(a);
					lowest = Math.max(lowest, x * Mth.sin(zr) + y1 * Mth.cos(zr));
				}
			}
		}
		return lowest;
	}

	/** The head on its neck, the jaw, and the gill crowns. */
	private void face(AvatarRenderState state, XenoMotion.Read m, boolean swim, float speed, float age, float bite) {
		float turn = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -HEAD_TURN, HEAD_TURN) - this.yaw[0] * 0.5F;
		float nod;
		if (swim) {
			nod = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.3F, -0.3F, 0.3F) - this.pitch[0] * 0.4F;
		} else {
			nod = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.5F, -NOD_UP, NOD_DOWN);
		}
		// a bite: the head darts out, tips up, the jaw flung open; tipped up, it rises so its back stays off the ground
		float tip = nod - bite * 0.4F;
		this.skull.x = 0.0F;
		this.skull.y = SKULL_Y - bite * 0.8F + m.breath() * 0.08F - SKULL_LIFT * Mth.sin(Math.max(0.0F, -tip));
		this.skull.z = -3.5F - bite * LUNGE_REACH;
		this.skull.yRot = turn;
		this.skull.xRot = tip;
		this.jaw.xRot = 0.05F + m.breath() * 0.03F + bite * LUNGE_JAW + (swim ? 0.04F + 0.04F * Mth.sin(age * 0.2F) : 0.0F);
		for (int i = 0; i < 6; i++) {
			boolean left = i >= 3;
			int row = i % 3;
			float s = left ? -1.0F : 1.0F;
			// the crowns sweep back as it moves or swims, flutter all the time, and flare wider than Slipfin's
			float sweep = 0.4F + (swim ? 0.45F : 0.0F) + speed * 0.3F + bite * 0.2F;
			float flutter = Mth.sin(age * 0.22F + i * 1.1F) * 0.2F * (1.0F + row * 0.3F) + m.breath() * 0.05F;
			this.gills[i].yRot = s * (sweep + flutter);
			this.gills[i].zRot = s * (GILL_FAN[row] + Mth.sin(age * 0.17F + i * 0.7F) * 0.12F);
		}
	}
}
