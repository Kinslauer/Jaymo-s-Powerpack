package com.jaymospowers.client.power.xenoform.ultimate;

import com.jaymospowers.client.power.wind.WindPose;
import com.jaymospowers.client.power.xenoform.XenoFormModels;
import com.jaymospowers.client.power.xenoform.XenoMotion;
import com.jaymospowers.client.power.xenoform.XenoformFlightPose;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.21.37, Ultimates patch 2) Wyrm's Ultimate, Elder Wyrm: Wyrm's own build and gait (a long low scaly body on four clawed legs
 * that trot in a lizard's diagonal gait, each leg two bones and a foot solved so the planted feet never slide, a neck rising to a
 * horned head with a hinged jaw, a tail in three joints to a spade), the same shape and size in model pixels (the 2.0x of the
 * Ultimate comes from the player's scale, as Wyrm's 1.2x does) but heavier: black scale plates run down its back and along its
 * flanks, guards sit on the thighs and a plate over the brow, the spikes grow long, the spade widens into a barbed blade and the two
 * horns sweep back into a crown. It walks slower and flies on slow, deep wingbeats ({@link ElderWings}, drawn in
 * {@link #submitExtras} with the dragon's own texture and its veins lit void purple). Seams glow along the plates (the skin's glow
 * map, drawn fullbright by the layer). The skin is {@code tools/ultimates/wyrm.py}, 128x128; {@link #UV} here is the same table
 * that script prints ({@code wyrm.py layout}).
 * <p>
 * The change-in film zooms on, in order, {@code tail3+spade}, {@code torso}, {@code neck1+neck2} and {@code right_horn+left_horn}:
 * those names are parts of this mesh.
 */
public final class UltimateElderWyrm extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** The torso's height (model y of its middle) at rest; how far it sinks when sneaking and on a landing. */
	static final float BODY_Y = 14.0F, CROUCH_DROP = 2.0F, LAND_DIP = 2.0F;
	/** Where Wind's cruise tilt pivots: the renderer's player scale and how far over the feet it draws the model (as Wyrm's model reads them). */
	static final float PLAYER_SCALE = 0.9375F, MODEL_ORIGIN = 1.501F;
	/** A sneaking player is drawn 2 pixels of the world lower: this many model pixels. */
	static final float CROUCH_SINK = 2.0F / PLAYER_SCALE;
	/** The gait (slower and heavier than Wyrm's 1.8): radians of step cycle per unit of walkAnimationPos, half a stride (pixels), the lift of a swinging foot, the fraction of the cycle a foot is down. */
	static final float GAIT = 1.4F, STRIDE = 3.8F, LIFT = 2.0F, DUTY = 0.65F;
	/** How far the walk bobs the torso (pixels), rolls it (radians) and wriggles it side to side (radians). */
	static final float BOB = 0.9F, ROLL = 0.05F, WRIGGLE = 0.05F;
	/** A foot lifting off curls its toes up this much (radians); walkAnimationSpeed at which the gait is at full stride. */
	static final float TOE_LIFT = 0.3F, WALK_FULL = 0.3F;
	/** The legs: a hip's offset from the torso's middle (x either side, y, z fore and hind), the two bones' and the foot's lengths. */
	static final float HIP_X = 3.7F, HIP_Y = 1.5F, FRONT_Z = -4.2F, HIND_Z = 4.0F, THIGH = 4.0F, SHIN = 4.5F, FOOT_H = 1.5F;
	/** The legs splay out a little (radians). */
	static final float SPLAY = 0.1F;
	/** Where an ankle rests ahead of its hip (pixels): the forelegs a touch forward, the hind straight under. */
	static final float FRONT_REACH = -0.5F, HIND_REACH = 0.0F;
	/** Flying, the legs tuck up under the body: the ankle's offset from its hip (z: fore, hind; y), and the toes' angle. */
	static final float TUCK_FRONT_Z = -0.5F, TUCK_HIND_Z = 2.5F, TUCK_Y = 3.6F, TUCK_TOE = 0.5F;
	/** Falling, the legs reach down and out: how far they reach (0-1), the ankle's offset (front z, hind z, y). */
	static final float REACH = 0.7F, REACH_FRONT_Z = -2.0F, REACH_HIND_Z = 1.5F, REACH_Y = 7.6F;
	/** The tail's resting pitch at each joint (+ is up): arching a little from the back, then drooping. */
	static final float[] TAIL_REST = {0.22F, -0.16F, -0.10F, 0.0F};
	/** How far the tail's lift may drop it and raise it (radians), and how much of it each joint takes. */
	static final float TAIL_DROOP = 0.35F, TAIL_RISE = 0.5F;
	private static final float[] TAIL_SHARE = {0.3F, 0.3F, 0.2F, 0.1F};
	/** The neck's resting pitch at its two joints and the head (+ is down): up from the chest, then level. */
	static final float NECK1 = -0.7F, NECK2 = 0.45F, HEAD = 0.3F;
	/** A bite: how far the neck straightens (joint 1, joint 2), how far the head thrusts forward (pixels), how wide the jaw opens (radians). */
	static final float BITE_NECK1 = 0.6F, BITE_NECK2 = -0.2F, BITE_THRUST = 0.9F, JAW_OPEN = 1.0F, JAW_REST = 0.06F;
	/** Flying, the neck stretches out ahead. */
	static final float FLY_NECK1 = 0.35F, FLY_NECK2 = -0.1F;
	/** How far the head follows where the player looks (degrees clamp: yaw, pitch). */
	static final float LOOK_YAW = 80.0F, LOOK_PITCH = 60.0F;
	/** Cruising (Wind's pose), the body dives and climbs after the look by this much of it (degrees clamp, share). */
	static final float DIVE_MAX = 60.0F, DIVE_FOLLOW = 0.7F;
	/** WindPose's air brake leans the body back this much (degrees). */
	static final float CRUISE_BRAKE_LEAN = 25.0F;
	/** The spine's ridge tilts back this much (radians). */
	static final float SPIKE_TILT = -0.4F;
	/** Its flying wing beat bobs the body (pixels). */
	static final float BEAT_BOB = 0.9F;
	/** The insignia's size on the chest. */
	static final float PLATE_SIZE = 0.9F;
	private static final float TWO_PI = (float)Math.PI * 2.0F;

	/** Spikes along the back: where each sits (parent: 0 torso, 1 first neck joint, 2 second, 3 first tail joint, 4 second; z along it). */
	private static final float[][] SPIKES = {
		{0, -3.5F}, {0, -0.5F}, {0, 2.5F}, {0, 5.0F}, {1, -2.0F}, {2, -1.0F}, {3, 2.5F}, {3, 5.0F}, {4, 2.5F}};
	/** The plates on its back (z along the torso) and the flank plates (z, either side). */
	private static final float[] BACK_PLATE_Z = {-3.5F, -0.5F, 2.5F};
	private static final float[] FLANK_Z = {-2.5F, 2.5F};

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/wyrm.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("torso", 0, 0), uv("chest", 36, 0), uv("neck1", 58, 0), uv("neck2", 76, 0), uv("skull", 92, 0), uv("snout", 112, 0),
		uv("jaw", 0, 17), uv("horn", 18, 17), uv("crown", 36, 17), uv("brow", 48, 17), uv("tail1", 68, 17), uv("tail2", 90, 17),
		uv("tail3", 110, 17), uv("blade", 0, 28), uv("cross", 14, 28), uv("barb", 38, 28), uv("thigh", 44, 28), uv("shin", 56, 28),
		uv("foot", 64, 28), uv("spike", 78, 28), uv("plate", 84, 28), uv("flank", 100, 28), uv("guard", 110, 28), uv("arm", 0, 41));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart torso, chest, neck1, neck2, skull, jaw, tail1, tail2, tail3, spade;
	/** Fore right, fore left, hind right, hind left. */
	private final ModelPart[] thighs = new ModelPart[4], shins = new ModelPart[4], feet = new ModelPart[4];
	private final ModelPart[] spikes = new ModelPart[SPIKES.length];
	/** Every part that is animated, to put back to its resting pose each frame. */
	private final ModelPart[] moving;
	private ElderWings wings;

	public UltimateElderWyrm() {
		super(XenoFormModels.bake(mesh(), 128, 128), "wyrm");
		// on the chest's front, under the neck (the man's body copies the torso's pose, so this is in the torso's space)
		this.insigniaY = 1.0F;
		this.insigniaZ = -8.0F;
		this.insigniaSize = PLATE_SIZE;
		ModelPart root = this.root();
		this.torso = root.getChild("torso");
		this.chest = this.torso.getChild("chest");
		this.neck1 = this.torso.getChild("neck1");
		this.neck2 = this.neck1.getChild("neck2");
		this.skull = this.neck2.getChild("skull");
		this.jaw = this.skull.getChild("jaw");
		this.tail1 = this.torso.getChild("tail1");
		this.tail2 = this.tail1.getChild("tail2");
		this.tail3 = this.tail2.getChild("tail3");
		this.spade = this.tail3.getChild("spade");
		List<ModelPart> all = new ArrayList<>(List.of(this.torso, this.chest, this.neck1, this.neck2, this.skull, this.jaw, this.tail1,
			this.tail2, this.tail3, this.spade));
		for (int i = 0; i < 4; i++) {
			this.thighs[i] = this.torso.getChild("thigh" + i);
			this.shins[i] = this.thighs[i].getChild("shin" + i);
			this.feet[i] = this.shins[i].getChild("foot" + i);
			all.add(this.thighs[i]);
			all.add(this.shins[i]);
			all.add(this.feet[i]);
		}
		for (int i = 0; i < SPIKES.length; i++) {
			this.spikes[i] = this.spikeParent(i).getChild("spike" + i);
			all.add(this.spikes[i]);
		}
		this.moving = all.toArray(new ModelPart[0]);
	}

	private ModelPart spikeParent(int i) {
		return switch ((int)SPIKES[i][0]) {
			case 0 -> this.torso;
			case 1 -> this.neck1;
			case 2 -> this.neck2;
			case 3 -> this.tail1;
			default -> this.tail2;
		};
	}

	/** A box of {@code key}'s patch of the skin, its size taken from the skin painter's table. */
	private static CubeListBuilder cube(String key, boolean mirror, float x, float y, float z, int w, int h, int d) {
		int[] at = UV.get(key);
		CubeListBuilder builder = CubeListBuilder.create().texOffs(at[0], at[1]);
		if (mirror) {
			builder = builder.mirror();
		}
		return builder.addBox(x, y, z, w, h, d);
	}

	private static MeshDefinition mesh() {
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
		PartDefinition root = mesh.getRoot();
		// the man's head, body and legs are never drawn; his body is the insignia's anchor, posed as the torso. His arms are only
		// for the first-person hand: a scaled forelimb
		root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, BODY_Y, -6.0F)).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, BODY_Y, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-HIP_X, BODY_Y + HIP_Y, HIND_Z));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(HIP_X, BODY_Y + HIP_Y, HIND_Z));
		root.addOrReplaceChild("right_arm", cube("arm", false, -3.0F, -2.0F, -2.0F, 4, 12, 4), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", cube("arm", true, -1.0F, -2.0F, -2.0F, 4, 12, 4), PartPose.offset(5.0F, 2.0F, 0.0F));
		PartDefinition torso = root.addOrReplaceChild("torso", cube("torso", false, -3.5F, -3.0F, -5.5F, 7, 6, 11), PartPose.offset(0.0F, BODY_Y, 0.0F));
		torso.addOrReplaceChild("chest", cube("chest", false, -3.5F, -3.5F, -2.0F, 7, 7, 4), PartPose.offset(0.0F, 1.0F, -6.0F));
		// the black plates down the back, and one or two along each flank
		for (int i = 0; i < BACK_PLATE_Z.length; i++) {
			torso.addOrReplaceChild("back_plate" + i, cube("plate", false, -2.5F, -1.0F, -1.5F, 5, 1, 3), PartPose.offset(0.0F, -3.0F, BACK_PLATE_Z[i]));
		}
		for (int i = 0; i < FLANK_Z.length; i++) {
			torso.addOrReplaceChild("right_flank" + i, cube("flank", false, -1.0F, -2.0F, -2.0F, 1, 4, 4), PartPose.offset(-3.5F, -0.5F, FLANK_Z[i]));
			torso.addOrReplaceChild("left_flank" + i, cube("flank", true, 0.0F, -2.0F, -2.0F, 1, 4, 4), PartPose.offset(3.5F, -0.5F, FLANK_Z[i]));
		}
		// the neck: two joints rising from the chest, then the head with its snout, jaw, brow plate and crown
		PartDefinition neck1 = torso.addOrReplaceChild("neck1", cube("neck1", false, -1.5F, -1.5F, -5.0F, 3, 3, 6),
			PartPose.offsetAndRotation(0.0F, -2.0F, -6.0F, NECK1, 0.0F, 0.0F));
		PartDefinition neck2 = neck1.addOrReplaceChild("neck2", cube("neck2", false, -1.5F, -1.5F, -4.0F, 3, 3, 5),
			PartPose.offsetAndRotation(0.0F, 0.0F, -4.5F, NECK2, 0.0F, 0.0F));
		PartDefinition skull = neck2.addOrReplaceChild("skull", cube("skull", false, -2.5F, -2.0F, -4.0F, 5, 4, 5),
			PartPose.offsetAndRotation(0.0F, 0.0F, -3.5F, HEAD, 0.0F, 0.0F));
		skull.addOrReplaceChild("snout", cube("snout", false, -1.5F, -0.7F, -4.0F, 3, 2, 4), PartPose.offset(0.0F, 0.0F, -4.0F));
		skull.addOrReplaceChild("jaw", cube("jaw", false, -1.5F, 0.0F, -6.0F, 3, 1, 6), PartPose.offset(0.0F, 1.4F, -0.5F));
		skull.addOrReplaceChild("brow", cube("brow", false, -3.0F, -0.5F, -2.0F, 6, 1, 4), PartPose.offset(0.0F, -2.3F, -1.5F));
		// the horns sweep back and out, up a little, and a crown of smaller ones stands inside each
		skull.addOrReplaceChild("right_horn", cube("horn", false, -1.0F, -1.0F, 0.0F, 2, 2, 7),
			PartPose.offsetAndRotation(-2.0F, -1.3F, 0.2F, 0.45F, -0.4F, 0.0F));
		skull.addOrReplaceChild("left_horn", cube("horn", true, -1.0F, -1.0F, 0.0F, 2, 2, 7),
			PartPose.offsetAndRotation(2.0F, -1.3F, 0.2F, 0.45F, 0.4F, 0.0F));
		skull.addOrReplaceChild("right_crown", cube("crown", false, -0.5F, -0.5F, 0.0F, 1, 1, 5),
			PartPose.offsetAndRotation(-0.9F, -2.6F, -0.2F, 0.85F, -0.15F, 0.0F));
		skull.addOrReplaceChild("left_crown", cube("crown", true, -0.5F, -0.5F, 0.0F, 1, 1, 5),
			PartPose.offsetAndRotation(0.9F, -2.6F, -0.2F, 0.85F, 0.15F, 0.0F));
		skull.addOrReplaceChild("crest", cube("crown", false, -0.5F, -0.5F, 0.0F, 1, 1, 5), PartPose.offsetAndRotation(0.0F, -2.8F, -0.6F, 1.0F, 0.0F, 0.0F));
		// the tail: three joints and a barbed blade
		PartDefinition tail1 = torso.addOrReplaceChild("tail1", cube("tail1", false, -2.0F, -2.0F, -1.0F, 4, 4, 7),
			PartPose.offsetAndRotation(0.0F, -0.5F, 5.2F, TAIL_REST[0], 0.0F, 0.0F));
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", cube("tail2", false, -1.5F, -1.5F, -1.0F, 3, 3, 7),
			PartPose.offsetAndRotation(0.0F, 0.0F, 5.5F, TAIL_REST[1], 0.0F, 0.0F));
		PartDefinition tail3 = tail2.addOrReplaceChild("tail3", cube("tail3", false, -1.0F, -1.0F, -1.0F, 2, 2, 6),
			PartPose.offsetAndRotation(0.0F, 0.0F, 5.5F, TAIL_REST[2], 0.0F, 0.0F));
		PartDefinition spade = tail3.addOrReplaceChild("spade", cube("blade", false, -0.5F, -3.5F, -1.0F, 1, 7, 6),
			PartPose.offsetAndRotation(0.0F, 0.0F, 4.5F, TAIL_REST[3], 0.0F, 0.0F));
		spade.addOrReplaceChild("cross", cube("cross", false, -3.5F, -0.5F, -0.5F, 7, 1, 5), PartPose.ZERO);
		spade.addOrReplaceChild("right_barb", cube("barb", false, -0.5F, -1.5F, 0.0F, 1, 3, 2), PartPose.offsetAndRotation(-3.4F, 0.0F, 4.2F, 0.0F, 0.5F, 0.0F));
		spade.addOrReplaceChild("left_barb", cube("barb", true, -0.5F, -1.5F, 0.0F, 1, 3, 2), PartPose.offsetAndRotation(3.4F, 0.0F, 4.2F, 0.0F, -0.5F, 0.0F));
		spade.addOrReplaceChild("top_barb", cube("barb", false, -0.5F, -1.0F, 0.0F, 1, 3, 2), PartPose.offsetAndRotation(0.0F, -3.5F, 4.2F, -0.6F, 0.0F, 0.0F));
		spade.addOrReplaceChild("bottom_barb", cube("barb", false, -0.5F, -2.0F, 0.0F, 1, 3, 2), PartPose.offsetAndRotation(0.0F, 3.5F, 4.2F, 0.6F, 0.0F, 0.0F));
		// the long spikes along the back, neck and tail
		for (int i = 0; i < SPIKES.length; i++) {
			PartDefinition parent = switch ((int)SPIKES[i][0]) {
				case 0 -> torso;
				case 1 -> neck1;
				case 2 -> neck2;
				case 3 -> tail1;
				default -> tail2;
			};
			float y = switch ((int)SPIKES[i][0]) {
				case 0 -> -3.8F;
				case 1, 2 -> -1.5F;
				case 3 -> -2.0F;
				default -> -1.5F;
			};
			parent.addOrReplaceChild("spike" + i, cube("spike", false, -0.5F, -4.0F, -1.0F, 1, 4, 2), PartPose.offsetAndRotation(0.0F, y, SPIKES[i][1], SPIKE_TILT, 0.0F, 0.0F));
		}
		// the legs: thigh with its guard, shin, foot; fore right, fore left, hind right, hind left
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = (i & 1) == 1;
			PartDefinition thigh = torso.addOrReplaceChild("thigh" + i, cube("thigh", left, -1.5F, -0.5F, -1.5F, 3, 5, 3),
				PartPose.offset(left ? HIP_X : -HIP_X, HIP_Y, front ? FRONT_Z : HIND_Z));
			thigh.addOrReplaceChild("guard" + i, cube("guard", left, -2.0F, -1.2F, -2.0F, 4, 3, 4), PartPose.ZERO);
			PartDefinition shin = thigh.addOrReplaceChild("shin" + i, cube("shin", left, -1.0F, -0.5F, -1.0F, 2, 5, 2), PartPose.offset(0.0F, THIGH, 0.0F));
			shin.addOrReplaceChild("foot" + i, cube("foot", left, -1.5F, -0.5F, -3.5F, 3, 2, 4), PartPose.offset(0.0F, SHIN, 0.0F));
		}
		return mesh;
	}

	/** Where a foot is along its stride (-1 forward .. +1 back, relative to the body) a fraction u of the way through its cycle. */
	private static float stride(float u) {
		if (u < DUTY) {
			return -1.0F + 2.0F * u / DUTY;
		}
		float s = (u - DUTY) / (1.0F - DUTY);
		return 1.0F - 2.0F * (0.5F - 0.5F * Mth.cos(s * (float)Math.PI));
	}

	/** How high a foot is lifted (0-1) a fraction u of the way through its cycle. */
	private static float lifted(float u) {
		return u < DUTY ? 0.0F : Mth.sin((u - DUTY) / (1.0F - DUTY) * (float)Math.PI);
	}

	/** How far a tail rises: up as it falls, whipping down on landing, low when crouched (radians, + is up). */
	private static float tailLift(XenoMotion.Read m) {
		return Math.max(0.0F, m.fall()) * 0.5F * m.air() - m.land() * 0.7F - (m.crouching() ? 0.35F : 0.0F);
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/**
	 * Two bones from the hip to an ankle {@code (tz, ty)} away (z back, y down, from the hip), the middle joint forward (hind legs) or
	 * back (forelegs); the foot is kept at {@code toe} to the ground.
	 */
	private static void solve(ModelPart thigh, ModelPart shin, ModelPart foot, float tz, float ty, boolean kneeForward, float toe) {
		float reach = Mth.clamp((float)Math.sqrt(tz * tz + ty * ty), Math.abs(THIGH - SHIN) + 0.3F, THIGH + SHIN - 0.05F);
		float dir = (float)Math.atan2(tz, ty);
		float bend = (float)Math.acos(Mth.clamp((THIGH * THIGH + reach * reach - SHIN * SHIN) / (2.0F * THIGH * reach), -1.0F, 1.0F));
		float a = kneeForward ? dir - bend : dir + bend;
		float ankleZ = reach * Mth.sin(dir), ankleY = reach * Mth.cos(dir);
		float b = (float)Math.atan2(ankleZ - THIGH * Mth.sin(a), ankleY - THIGH * Mth.cos(a)) - a;
		thigh.xRot = a;
		shin.xRot = b;
		foot.xRot = -(a + b) + toe;
	}

	/** The body posed for this frame (the wings pose it too, to copy its torso). */
	void repose(AvatarRenderState state) {
		pose(state);
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		// the man is never drawn: his body is only the insignia's anchor (posed as the torso below)
		this.head.visible = this.hat.visible = this.body.visible = false;
		this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
		this.body.resetPose();
		for (ModelPart part : this.moving) {
			part.resetPose();
		}
		float t = state.ageInTicks;
		XenoMotion.Read m = XenoMotion.read(state);
		XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
		float fly = flight == null ? 0.0F : smooth(flight.wings());
		float fold = flight == null ? 0.0F : smooth(flight.fold());
		Float cruiseData = state.getData(WindPose.KEY);
		float cruise = cruiseData == null || state.deathTime > 0.0F ? 0.0F : cruiseData;

		// ---- the walk: diagonal pairs (fore right with hind left) in step, the torso bobbing twice a cycle ----
		float walk = Mth.clamp(state.walkAnimationSpeed / WALK_FULL, 0.0F, 1.0F) * (1.0F - fly);
		float cycle = state.walkAnimationPos * GAIT / TWO_PI;
		cycle -= (float)Math.floor(cycle);
		float rise = 0.5F + 0.5F * Mth.cos(2.0F * TWO_PI * (cycle - 0.3F));
		float by = BODY_Y - BOB * rise * walk + m.land() * LAND_DIP;
		float ground = 24.0F;
		if (state.isCrouching) {
			by += CROUCH_DROP - CROUCH_SINK;
			ground -= CROUCH_SINK;
		}
		// flying, the slow wing beat lifts it
		by += Mth.sin(t * ElderWings.FLAP_RATE) * BEAT_BOB * fly * (1.0F - fold);
		float roll = Mth.sin(cycle * TWO_PI) * ROLL * walk;
		float wriggle = Mth.sin(cycle * TWO_PI + 0.4F) * WRIGGLE * walk;

		// ---- the torso. Wind's cruise tilt lays an upright body flat; a dragon already lies level, so it tips back by the same
		// angle about the same point, and follows the look's pitch instead ----
		float tilt = 0.0F;
		if (cruise > 0.0F) {
			Float brakeData = state.getData(WindPose.BRAKE_KEY);
			float brake = brakeData == null ? 0.0F : brakeData;
			float laid = cruise * (1.0F - 0.75F * brake) * (-90.0F - state.xRot) + brake * CRUISE_BRAKE_LEAN;
			float dive = Mth.clamp(state.xRot, -DIVE_MAX, DIVE_MAX) * cruise * DIVE_FOLLOW;
			tilt = (laid + dive) * Mth.DEG_TO_RAD;
		}
		float middle = (MODEL_ORIGIN - state.boundingBoxHeight / Math.max(state.scale, 0.01F) * 0.5F / PLAYER_SCALE) * 16.0F;
		float toMiddle = by - middle;
		this.torso.y = middle + toMiddle * Mth.cos(tilt);
		this.torso.z = toMiddle * Mth.sin(tilt);
		this.torso.xRot = tilt;
		this.torso.yRot = wriggle;
		this.torso.zRot = roll;
		this.chest.yScale = 1.0F + m.breath() * 0.03F;
		// the man's body is the insignia's anchor: on the torso
		this.body.x = this.torso.x;
		this.body.y = this.torso.y;
		this.body.z = this.torso.z;
		this.body.xRot = this.torso.xRot;
		this.body.yRot = this.torso.yRot;
		this.body.zRot = this.torso.zRot;

		// ---- the legs ----
		float air = m.air() * (1.0F - fly) * REACH;
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = (i & 1) == 1;
			float u = cycle + (front == left ? 0.5F : 0.0F);
			u -= (float)Math.floor(u);
			float swing = lifted(u) * walk;
			float tz = (front ? FRONT_REACH : HIND_REACH) + STRIDE * walk * stride(u);
			// the ankle's height over the ground, in the torso's frame: the planted foot stays on the ground as the torso bobs
			float ty = ground - FOOT_H - by - HIP_Y - LIFT * swing;
			float toe = -TOE_LIFT * swing;
			// falling: reaching down; flying: tucked up under the body
			tz = Mth.lerp(air, tz, front ? REACH_FRONT_Z : REACH_HIND_Z);
			ty = Mth.lerp(air, ty, REACH_Y);
			tz = Mth.lerp(fly, tz, front ? TUCK_FRONT_Z : TUCK_HIND_Z);
			ty = Mth.lerp(fly, ty, TUCK_Y);
			toe = Mth.lerp(fly, toe, TUCK_TOE);
			solve(this.thighs[i], this.shins[i], this.feet[i], tz, ty, !front, toe);
			this.thighs[i].zRot = left ? -SPLAY : SPLAY;
			this.shins[i].zRot = left ? SPLAY * 0.8F : -SPLAY * 0.8F;
		}

		// ---- the neck and head: they follow where the player looks, and a bite throws the head forward ----
		float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
		float yaw = Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD * (1.0F - cruise);
		float pitch = Mth.clamp(state.xRot, -LOOK_PITCH, LOOK_PITCH) * Mth.DEG_TO_RAD * (1.0F - DIVE_FOLLOW * cruise);
		float nod = Mth.cos(2.0F * TWO_PI * (cycle - 0.3F)) * 0.06F * walk + m.breath() * 0.03F;
		float sway = Mth.sin(t * 0.05F) * 0.05F;
		this.neck1.xRot = NECK1 + pitch * 0.3F + BITE_NECK1 * bite + FLY_NECK1 * fly;
		this.neck2.xRot = NECK2 + pitch * 0.3F + BITE_NECK2 * bite + FLY_NECK2 * fly + nod;
		this.skull.xRot = HEAD + pitch * 0.4F - nod;
		this.neck1.yRot = yaw * 0.3F - wriggle * 1.5F + sway;
		this.neck2.yRot = yaw * 0.3F - sway * 0.5F;
		this.skull.yRot = yaw * 0.4F;
		this.skull.zRot = Mth.sin(t * 0.07F + 0.5F) * 0.03F;
		this.neck2.z -= BITE_THRUST * bite;
		this.skull.z -= BITE_THRUST * bite;
		this.jaw.xRot = JAW_REST + (1.0F + Mth.sin(t * 0.09F)) * 0.04F + JAW_OPEN * bite;

		// ---- the tail swings behind each turn, each joint a beat after the last; it lifts as it drops, whips down on landing and droops when crouched ----
		float lag = m.trail() - m.turn();
		float tailUp = Mth.clamp(tailLift(m), -TAIL_DROOP, TAIL_RISE);
		float wag = Mth.cos(cycle * TWO_PI) * 0.25F * walk + Mth.sin(t * 0.08F) * 0.08F;
		this.tail1.yRot = wag - m.trail() * 0.5F - wriggle * 1.2F;
		this.tail2.yRot = Mth.sin(t * 0.08F - 0.8F) * 0.1F + wag * 0.6F - lag * 0.6F - m.trail() * 0.2F;
		this.tail3.yRot = Mth.sin(t * 0.08F - 1.6F) * 0.14F + wag * 0.5F - lag * 0.8F;
		this.spade.yRot = Mth.sin(t * 0.08F - 2.4F) * 0.2F + wag * 0.4F - lag * 0.8F;
		this.tail1.xRot = TAIL_REST[0] + walk * 0.05F + tailUp * TAIL_SHARE[0] + fly * 0.1F;
		this.tail2.xRot = TAIL_REST[1] + tailUp * TAIL_SHARE[1] - fly * 0.05F;
		this.tail3.xRot = TAIL_REST[2] + tailUp * TAIL_SHARE[2];
		this.spade.xRot = TAIL_REST[3] + tailUp * TAIL_SHARE[3];

		// ---- the ridge ripples ----
		for (int i = 0; i < this.spikes.length; i++) {
			this.spikes[i].xRot = SPIKE_TILT + Mth.sin(t * 0.1F - i * 0.7F) * 0.04F + m.breath() * 0.02F;
		}
	}

	/**
	 * Its wings, drawn over the body from the dragon's own texture, then once more fullbright in void purple so the veins of the
	 * membranes glow. The layer draws the seams of the skin itself.
	 */
	@Override
	public void submitExtras(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		if (this.wings == null) {
			this.wings = new ElderWings(this);
		}
		collector.submitModel(this.wings, state, poseStack, RenderTypes.entityCutout(ElderWings.TEXTURE), light, OverlayTexture.NO_OVERLAY, -1, null, 0);
		int alpha = (int)(255.0F * Mth.clamp(0.45F + 0.2F * Mth.sin(state.ageInTicks * 0.1F), 0.0F, 1.0F));
		collector.order(1).submitModel(this.wings, state, poseStack, RenderTypes.eyes(ElderWings.TEXTURE), light, OverlayTexture.NO_OVERLAY,
			ARGB.color(alpha, 0xC0, 0x60, 0xE0), null, 0);
	}

	/**
	 * Wyrm's wings (the ender dragon's own, from the game's texture, never copied), larger and slower: each is the dragon model's
	 * wing (a bone with its membrane, and the tip with its own membrane) hung from the shoulder blades of the Elder Wyrm's torso,
	 * copied from {@link UltimateElderWyrm#repose}. Open in the air they beat, fold back at speed, flare when falling; on the ground
	 * they fold along its back.
	 */
	private static final class ElderWings extends HumanoidModel<AvatarRenderState> {
		static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
		/** How much of the dragon's wing (56 + 56 long) is drawn, and where each roots on the shoulders (from the torso's middle). */
		static final float SCALE = 0.27F, ROOT_X = 3.0F, ROOT_Y = -4.0F, ROOT_Z = -3.4F;
		/** The beat's speed (slow) and depth; folded, the wings sweep back to this yaw and lift this little. */
		static final float FLAP_RATE = 0.35F, FLAP_DEPTH = 1.0F, FOLDED_YAW = 1.35F, FOLDED_LIFT = 0.15F;
		/** In flight, how far the tips bend behind the beat. */
		static final float TIP_FLEX = 0.6F;
		/** Falling without flying, how far the wings flare open; how hard a landing slams them down. */
		static final float FALL_FLARE = 0.8F, LAND_SLAM = 0.6F;
		/** Folded on the ground: the size, the roll on edge, the sweep back along the back, the tip folded forward, its tilt off the flank. */
		static final float FOLD_SCALE = 0.55F, FOLD_ROLL = 1.25F, FOLD_SWEEP = 1.45F, FOLD_TIP = 0.9F, FOLD_TIP_TILT = 0.9F;

		private final UltimateElderWyrm owner;
		private final ModelPart torso, rightWing, leftWing, rightTip, leftTip;

		ElderWings(UltimateElderWyrm owner) {
			super(mesh().bakeRoot());
			this.owner = owner;
			ModelPart root = this.root();
			this.torso = root.getChild("torso");
			this.rightWing = this.torso.getChild("right_wing");
			this.leftWing = this.torso.getChild("left_wing");
			this.rightTip = this.rightWing.getChild("right_wing_tip");
			this.leftTip = this.leftWing.getChild("left_wing_tip");
		}

		private static LayerDefinition mesh() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
			PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create(), PartPose.offset(0.0F, BODY_Y, 0.0F));
			PartDefinition left = torso.addOrReplaceChild("left_wing",
				CubeListBuilder.create().mirror().addBox("bone", 0.0F, -4.0F, -4.0F, 56, 8, 8, 112, 88).addBox("skin", 0.0F, 0.0F, 2.0F, 56, 0, 56, -56, 88),
				PartPose.offset(ROOT_X, ROOT_Y, ROOT_Z).withScale(SCALE));
			left.addOrReplaceChild("left_wing_tip",
				CubeListBuilder.create().mirror().addBox("bone", 0.0F, -2.0F, -2.0F, 56, 4, 4, 112, 136).addBox("skin", 0.0F, 0.0F, 2.0F, 56, 0, 56, -56, 144),
				PartPose.offset(56.0F, 0.0F, 0.0F));
			PartDefinition right = torso.addOrReplaceChild("right_wing",
				CubeListBuilder.create().addBox("bone", -56.0F, -4.0F, -4.0F, 56, 8, 8, 112, 88).addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56, -56, 88),
				PartPose.offset(-ROOT_X, ROOT_Y, ROOT_Z).withScale(SCALE));
			right.addOrReplaceChild("right_wing_tip",
				CubeListBuilder.create().addBox("bone", -56.0F, -2.0F, -2.0F, 56, 4, 4, 112, 136).addBox("skin", -56.0F, 0.0F, 2.0F, 56, 0, 56, -56, 144),
				PartPose.offset(-56.0F, 0.0F, 0.0F));
			return LayerDefinition.create(mesh, 256, 256);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the Elder Wyrm's torso as it is posed this frame (re-posed here: each model is posed right before it is drawn)
			this.torso.resetPose();
			this.owner.repose(state);
			ModelPart body = this.owner.torso;
			this.torso.x = body.x;
			this.torso.y = body.y;
			this.torso.z = body.z;
			this.torso.xRot = body.xRot;
			this.torso.yRot = body.yRot;
			this.torso.zRot = body.zRot;
			float t = state.ageInTicks;
			XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
			float fly = flight == null ? 0.0F : smooth(flight.wings());
			float fold = flight == null ? 0.0F : smooth(flight.fold());
			XenoMotion.Read m = XenoMotion.read(state);
			float walk = state.walkAnimationSpeed;
			// on the ground and not flying, the wings are folded; in the air they open
			float wrap = smooth(Mth.clamp((1.0F - m.air()) * (1.0F - fly), 0.0F, 1.0F));

			// open: a slow stir at rest, deep slow strokes flying, dying away as the wings fold at speed
			float idle = m.breath() * 0.08F + Mth.sin(t * 0.2F) * 0.05F;
			float flap = Mth.sin(t * FLAP_RATE) * FLAP_DEPTH * (1.0F - fold);
			float beat = Mth.lerp(fly, idle, flap);
			float lift = fold * FOLDED_LIFT * fly;
			float flare = Math.max(0.0F, m.fall()) * m.air() * (1.0F - fly) * FALL_FLARE;
			float slam = m.land() * LAND_SLAM;
			float bank = m.turn() * 0.3F;
			float openZ = -0.3F + beat + lift - flare + slam + bank;
			float openY = Mth.lerp(fold * fly, 0.6F + Mth.cos(t * 0.2F) * 0.1F - flare * 0.5F, FOLDED_YAW);
			float openTip = -(Mth.sin(t * FLAP_RATE - 2.0F) + 0.5F) * TIP_FLEX * fly * (1.0F - fold) + flare * 0.3F;

			// folded (on the ground): the wings breathe, shiver with each step and swing out behind a turn
			float step = Mth.sin(state.walkAnimationPos * 0.6662F) * walk;
			float foldRoll = FOLD_ROLL + m.breath() * 0.03F + walk * 0.1F + m.land() * 0.1F;
			float foldSweep = FOLD_SWEEP + m.breath() * 0.02F + Mth.abs(step) * 0.05F - m.trail() * 0.08F;
			float foldTip = FOLD_TIP + m.breath() * 0.04F - walk * 0.15F + step * 0.05F;

			float size = SCALE * Mth.lerp(wrap, 1.0F, FOLD_SCALE);
			this.rightWing.xScale = this.rightWing.yScale = this.rightWing.zScale = size;
			this.leftWing.xScale = this.leftWing.yScale = this.leftWing.zScale = size;
			this.rightWing.xRot = Mth.lerp(wrap, 0.0F, foldRoll);
			this.leftWing.xRot = Mth.lerp(wrap, 0.0F, foldRoll);
			this.rightWing.yRot = Mth.lerp(wrap, openY, foldSweep);
			this.leftWing.yRot = Mth.lerp(wrap, -openY, -foldSweep);
			this.rightWing.zRot = Mth.lerp(wrap, openZ, 0.0F);
			this.leftWing.zRot = Mth.lerp(wrap, -openZ + 2.0F * bank, 0.0F);
			this.rightTip.zRot = Mth.lerp(wrap, openTip, FOLD_TIP_TILT);
			this.leftTip.zRot = Mth.lerp(wrap, -openTip, -FOLD_TIP_TILT);
			this.rightTip.yRot = Mth.lerp(wrap, 0.0F, -foldTip);
			this.leftTip.yRot = Mth.lerp(wrap, 0.0F, foldTip);
		}
	}
}
