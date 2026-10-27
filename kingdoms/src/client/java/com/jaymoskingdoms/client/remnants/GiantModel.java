package com.jaymoskingdoms.client.remnants;

import com.jaymoskingdoms.remnants.AetherGiant;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * (0.7.97) An Aether Giant (our own): a hunched, broad-chested giant of the Aether's stone, its small head sunk between
 * heavy shoulders under a crown of shards, long arms ending in great fists that hang to its knees, short thick legs,
 * and shards of its element jutting from its back. A blow brings one fist up and down; a stamp lifts both arms and
 * drops its weight onto them. Texture 128x128 ({@code tools/gen_giants.py} paints its UV layout).
 * <p>
 * (0.7.184) Andy (2026-10-24): "keep our old models, and the new animations and the shatter". The titan's (0.7.182)
 * walk on this body: a longer stride, arms that swing, twist and flare out wide of the chest, a breath and a flinch when
 * hit. Its death plays in two parts: it topples forward onto its face, limbs flailing, then breaks apart where it lands,
 * every piece thrown out, bouncing once and spinning ({@link #fall}, {@link #shatter}); the server throws up its stone
 * as it breaks ({@code AetherGiant#tickDeath}).
 */
final class GiantModel extends EntityModel<GiantState> {
	/** How far an arm lifts for a blow, how long a blow takes (ticks), and how far the body pitches into a stamp. */
	private static final float BLOW_LIFT = 2.4F, BLOW_TICKS = 12.0F, STAMP_PITCH = 0.35F;
	/** Its hunch (radians). */
	private static final float HUNCH = 0.22F;
	/**
	 * How far its legs swing, and its arms' swing, twist and flare out from the chest (at rest, and more at each stride's
	 * peak). (0.7.261) Andy: "a slow sway ... removing that fast swing": the legs keep to the server's steps (one every
	 * {@code AetherGiant#STEP_LENGTH} blocks, {@link GiantState#stride}) instead of vanilla's walk, which a 30-block body
	 * ran through four times too fast; the swings are smaller, and it sways with them: its weight rolls onto the foot coming
	 * down, its shoulders turn with the arm swinging forward, and it sinks onto each footfall, so its feet keep to the
	 * ground (its legs are {@link #LEG_LENGTH} long).
	 */
	private static final float LEG_SWING = 0.6F, ARM_SWING = 0.55F, ARM_TWIST = 0.18F, ARM_FLARE = 0.2F, ARM_FLARE_PEAK = 0.18F,
		ROLL = 0.07F, SHOULDER_TWIST = 0.09F, LEG_LENGTH = 14.0F;
	/** How it flinches when hit (body, head, arms). */
	private static final float FLINCH_BODY = 0.42F, FLINCH_HEAD = 0.6F, FLINCH_ARM = 0.6F;
	/** Its death: when it hits the ground and breaks up (vanilla takes the body away at 20 ticks). */
	private static final float DEATH_TICKS = 20.0F, FALL_TICKS = 9.0F;
	/** Its fall: how far it pitches over, rolls to one side, and how far its hips and legs drop (pixels), its head's twist, its reaching arm and flail. */
	private static final float FALL_PITCH = 1.3F, FALL_ROLL = 0.12F, FALL_DROP = 8.0F, LEG_DROP = 10.0F, HEAD_TWIST = 0.8F,
		REACH = 2.9F, FLAIL_RATE = 1.3F;
	/** Its break-up: the ground (pixels below the root), how fast pieces fly out (pixels a tick), their pop up, gravity, bounce, spin, and how widely they scatter. */
	private static final float GROUND = 24.0F, SCATTER_SPEED = 2.2F, POP = 3.0F, GRAVITY = 1.6F, BOUNCE = 0.35F, SPIN = 0.5F,
		SCATTER_SPREAD = 1.4F;
	/** The thud of its chest hitting the ground (pixels up, over this many ticks). */
	private static final float THUD = 2.0F, THUD_TICKS = 4.0F;
	/**
	 * (0.7.268) Its grab ({@link #grab}): the arm drawn up behind it in the wind-up, down to the ground for the snatch, held
	 * out ahead at its chest, then hurled down; how far its body leans back winding up and forward for the snatch and slam.
	 */
	private static final float GRAB_RAISED = -2.8F, GRAB_FLARE = 0.5F, GRAB_DOWN = -0.35F, GRAB_HELD = -1.35F, GRAB_HURLED = 0.3F,
		GRAB_LEAN_BACK = -0.3F, GRAB_LEAN_FORWARD = 0.45F, GRAB_TREMBLE = 0.04F;

	private final ModelPart body, shards, head, crown, rightArm, leftArm, rightLeg, leftLeg;

	GiantModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.shards = this.body.getChild("shards");
		this.head = this.body.getChild("head");
		this.crown = this.head.getChild("crown");
		this.rightArm = this.body.getChild("right_arm");
		this.leftArm = this.body.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// chest (20x14x12) over hips (16x6x10), and the shards out of its back (their own piece, to break loose)
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-10, -20, -6, 20, 14, 12)
			.texOffs(0, 26).addBox(-8, -6, -5, 16, 6, 10), PartPose.offsetAndRotation(0, 10, 0, HUNCH, 0, 0));
		body.addOrReplaceChild("shards", CubeListBuilder.create()
			.texOffs(96, 16).addBox(-6, -27, 2, 4, 10, 4)
			.texOffs(96, 16).addBox(3, -25, 3, 4, 10, 4)
			.texOffs(96, 16).addBox(-1, -23, 5, 3, 7, 3), PartPose.ZERO);
		// the head (8x8x8) with a heavy brow, sunk forward between the shoulders, and its crown of shards
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(64, 0).addBox(-4, -7, -6, 8, 8, 8)
			.texOffs(96, 0).addBox(-4.5F, -6, -6.5F, 9, 2, 2), PartPose.offsetAndRotation(0, -19, -4, -HUNCH, 0, 0));
		head.addOrReplaceChild("crown", CubeListBuilder.create()
			.texOffs(112, 16).addBox(-3.5F, -11, -3, 2, 5, 2)
			.texOffs(112, 16).addBox(-1, -12.5F, -2, 2, 6, 2)
			.texOffs(112, 16).addBox(1.5F, -11, -3, 2, 5, 2), PartPose.ZERO);
		// the arms (6x26x8) with great fists (7x6x9) and shoulder stones (8x5x9)
		body.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(0, 42).addBox(-6, -2, -4, 6, 26, 8)
			.texOffs(28, 42).addBox(-6.5F, 22, -4.5F, 7, 6, 9)
			.texOffs(0, 76).addBox(-7, -4, -4.5F, 8, 5, 9), PartPose.offset(-10, -17, 0));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
			.texOffs(0, 42).addBox(0, -2, -4, 6, 26, 8)
			.texOffs(28, 42).addBox(-0.5F, 22, -4.5F, 7, 6, 9)
			.texOffs(0, 76).addBox(-1, -4, -4.5F, 8, 5, 9), PartPose.offset(10, -17, 0));
		// short, thick legs (7x14x7)
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(60, 42).addBox(-3.5F, 0, -3.5F, 7, 14, 7), PartPose.offset(-5, 10, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(60, 42).mirror().addBox(-3.5F, 0, -3.5F, 7, 14, 7),
			PartPose.offset(5, 10, 0));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(GiantState state) {
		super.setupAnim(state);
		this.head.yRot = Mth.clamp(state.yRot, -50.0F, 50.0F) * Mth.DEG_TO_RAD;
		this.head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F;
		if (state.deathTime > 0.0F) {
			this.fall(state.seed, Math.min(state.deathTime, DEATH_TICKS));
			return;
		}
		// 1 as its right foot comes down ahead (even steps), -1 as its left does
		float walk = Mth.cos(state.stride * Mth.PI), amount = Math.min(state.walkAnimationSpeed, 1.0F);
		this.rightLeg.xRot = -walk * LEG_SWING * amount;
		this.leftLeg.xRot = walk * LEG_SWING * amount;
		// it sinks onto each footfall (a swung leg reaches less far down), its weight rolling onto that foot, its shoulders turning
		float sink = LEG_LENGTH * (1.0F - Mth.cos(this.rightLeg.xRot));
		this.body.y += sink;
		this.rightLeg.y += sink;
		this.leftLeg.y += sink;
		this.body.zRot = -walk * ROLL * amount;
		this.body.yRot = walk * SHOULDER_TWIST * amount;
		// its head keeps level as it rolls
		this.head.zRot = walk * ROLL * 0.6F * amount;
		// arms swing against the legs, twist, and flare out wide of the chest, more at the height of each swing
		this.rightArm.xRot = (walk * ARM_SWING - 0.2F) * amount;
		this.rightArm.yRot = -walk * ARM_TWIST * amount;
		this.rightArm.zRot = ARM_FLARE + Math.abs(walk) * ARM_FLARE_PEAK * amount;
		this.leftArm.xRot = (-walk * ARM_SWING - 0.2F) * amount;
		this.leftArm.yRot = walk * ARM_TWIST * amount;
		this.leftArm.zRot = -ARM_FLARE - Math.abs(walk) * ARM_FLARE_PEAK * amount;
		// a slow breath
		float breath = Mth.sin(state.ageInTicks * 0.09F) * 0.025F;
		this.body.xRot = HUNCH + breath;
		this.rightArm.zRot += breath;
		this.leftArm.zRot -= breath;
		if (state.aggressive && state.leftAttack <= 0.0F && state.rightAttack <= 0.0F) {
			// fists half raised when it's after someone
			this.rightArm.xRot -= 0.35F;
			this.leftArm.xRot -= 0.35F;
		}
		if (state.grab >= 0.0F) {
			this.grab(state.grab, state.ageInTicks);
		}
		if (state.rightAttack > 0.0F) {
			this.rightArm.xRot = -blow(state.rightAttack);
		}
		if (state.leftAttack > 0.0F) {
			this.leftArm.xRot = -blow(state.leftAttack);
		}
		if (state.stamp) {
			// both arms up, then its whole weight thrown down onto them
			float t = Math.min(Math.max(state.leftAttack, state.rightAttack) / BLOW_TICKS, 1.0F);
			this.body.xRot += STAMP_PITCH * Mth.sin(t * Mth.PI);
		}
		if (state.flinch > 0.0F) {
			// rocked back by a hit
			this.body.xRot -= state.flinch * FLINCH_BODY;
			this.head.xRot -= state.flinch * FLINCH_HEAD;
			this.rightArm.xRot -= state.flinch * FLINCH_ARM;
			this.rightArm.zRot += state.flinch * 0.5F;
			this.leftArm.xRot -= state.flinch * FLINCH_ARM;
			this.leftArm.zRot -= state.flinch * 0.5F;
		}
	}

	/**
	 * (0.7.268) Its grab, {@code t} ticks in ({@code AetherGiant}'s phases): it rears back, its right hand drawn up behind
	 * it and flared wide, then the hand sweeps down to the ground ahead, lifts its catch to its chest (trembling as it
	 * squeezes), hurls it down, and straightens up. The head follows the hand.
	 */
	private void grab(float t, float age) {
		float windup = AetherGiant.GRAB_WINDUP, snatch = AetherGiant.GRAB_SNATCH, hold = AetherGiant.GRAB_HOLD, slam = AetherGiant.GRAB_SLAM,
			recover = AetherGiant.GRAB_RECOVER;
		float x, z, lean, head;
		if (t < windup) {
			float u = ease(t / windup);
			x = Mth.lerp(u, -0.2F, GRAB_RAISED);
			z = ARM_FLARE + u * GRAB_FLARE;
			lean = u * GRAB_LEAN_BACK;
			head = -u * 0.4F;
		} else if (t < windup + snatch) {
			float u = ease((t - windup) / snatch);
			x = Mth.lerp(u, GRAB_RAISED, GRAB_DOWN);
			z = ARM_FLARE + (1.0F - u) * GRAB_FLARE;
			lean = Mth.lerp(u, GRAB_LEAN_BACK, GRAB_LEAN_FORWARD);
			head = Mth.lerp(u, -0.4F, 0.5F);
		} else if (t < windup + snatch + hold) {
			float u = ease(Math.min(1.0F, (t - windup - snatch) / AetherGiant.LIFT_TICKS));
			x = Mth.lerp(u, GRAB_DOWN, GRAB_HELD) + u * GRAB_TREMBLE * Mth.sin(age * 2.1F);
			z = ARM_FLARE + u * GRAB_TREMBLE * Mth.sin(age * 1.7F);
			lean = Mth.lerp(u, GRAB_LEAN_FORWARD, 0.0F);
			head = Mth.lerp(u, 0.5F, 0.15F);
		} else if (t < windup + snatch + hold + slam) {
			float u = (t - windup - snatch - hold) / slam;
			u *= u;
			x = Mth.lerp(u, GRAB_HELD, GRAB_HURLED);
			z = ARM_FLARE;
			lean = u * GRAB_LEAN_FORWARD;
			head = Mth.lerp(u, 0.15F, 0.6F);
		} else {
			float u = ease(Math.min(1.0F, (t - windup - snatch - hold - slam) / recover));
			x = Mth.lerp(u, GRAB_HURLED, this.rightArm.xRot);
			z = Mth.lerp(u, ARM_FLARE, this.rightArm.zRot);
			lean = (1.0F - u) * GRAB_LEAN_FORWARD;
			head = (1.0F - u) * 0.6F;
		}
		this.rightArm.xRot = x;
		this.rightArm.yRot = 0.0F;
		this.rightArm.zRot = z;
		this.body.xRot += lean;
		this.head.xRot += head;
		// the other arm braces out a little
		this.leftArm.zRot -= Math.abs(lean) * 0.5F;
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/**
	 * (0.7.184) Its death, {@code t} ticks in (0 to 20). Until {@link #FALL_TICKS} it topples forward onto its face, dropping
	 * to the ground with a slight roll to one side, head twisting, one arm thrown out ahead and the other flung wide, its
	 * legs going out from under it. Then it breaks up where it lies ({@link #shatter}). Which way it rolls, which arm
	 * reaches and how it flails come from {@code seed}, the same every frame.
	 */
	private void fall(int seed, float t) {
		float e = Mth.square(Math.min(t / FALL_TICKS, 1.0F));
		float roll = noise(seed, 0) < 0.5F ? -1.0F : 1.0F;
		// the flailing dies away as it lands
		float flail = Mth.sin(t * FLAIL_RATE + noise(seed, 2) * Mth.TWO_PI) * (1.0F - e);
		this.body.xRot = HUNCH + e * FALL_PITCH;
		this.body.zRot = roll * e * FALL_ROLL;
		this.body.y += e * FALL_DROP;
		this.head.xRot = Mth.lerp(e, this.head.xRot, -0.6F);
		this.head.yRot = Mth.lerp(e, this.head.yRot, (noise(seed, 3) * 2.0F - 1.0F) * HEAD_TWIST);
		this.head.zRot = roll * e * 0.3F;
		// one arm thrown out ahead of it, the other flung wide (each flare's sign is outward for its side)
		boolean rightReaches = noise(seed, 1) < 0.5F;
		ModelPart reach = rightReaches ? this.rightArm : this.leftArm, fling = rightReaches ? this.leftArm : this.rightArm;
		float out = rightReaches ? 1.0F : -1.0F;
		reach.xRot = -e * REACH + flail * 0.45F;
		reach.zRot = out * (ARM_FLARE + e * 0.25F);
		fling.xRot = -e * 0.4F - flail * 0.5F;
		fling.zRot = -out * (ARM_FLARE + e * 1.25F);
		// its legs go out from under it, trailing behind
		this.rightLeg.y += e * LEG_DROP;
		this.leftLeg.y += e * LEG_DROP;
		this.rightLeg.xRot = e * 1.5F + flail * 0.35F;
		this.leftLeg.xRot = e * 1.45F - flail * 0.45F;
		this.rightLeg.yRot = -e * 0.25F;
		this.leftLeg.yRot = e * 0.2F;
		if (t > FALL_TICKS) {
			this.shatter(seed, t - FALL_TICKS);
		}
	}

	/**
	 * (0.7.184) It breaks up where it fell, {@code s} ticks after it hit the ground: the chest thuds, and every other piece
	 * (head, crown, arms, back shards, legs) flies out from the body on its own line, pops up a little, falls back to the
	 * ground, bounces once and lies still, spinning as it goes.
	 */
	private void shatter(int seed, float s) {
		Matrix4f bodyFrom = pose(new Matrix4f(), this.body);
		this.body.y -= THUD * Mth.sin(Math.min(s / THUD_TICKS, 1.0F) * Mth.PI);
		Matrix4f bodyTo = pose(new Matrix4f(), this.body);
		Vector3f centre = bodyFrom.transformPosition(0.0F, -13.0F, 0.0F, new Vector3f());
		Matrix4f none = new Matrix4f();
		Matrix4f[] head = this.fly(this.head, bodyFrom, bodyTo, centre, seed, 1, 4.0F, s);
		this.fly(this.crown, head[0], head[1], centre, seed, 2, 1.5F, s);
		this.fly(this.rightArm, bodyFrom, bodyTo, centre, seed, 3, 4.0F, s);
		this.fly(this.leftArm, bodyFrom, bodyTo, centre, seed, 4, 4.0F, s);
		this.fly(this.shards, bodyFrom, bodyTo, centre, seed, 5, 2.0F, s);
		this.fly(this.rightLeg, none, none, centre, seed, 6, 3.5F, s);
		this.fly(this.leftLeg, none, none, centre, seed, 7, 3.5F, s);
	}

	/**
	 * One piece of it thrown loose {@code s} ticks into its break-up, worked out where it lies on the ground (model space,
	 * y down) and put back into its parent's frame as it now stands, so a piece flies free of whatever it hung from.
	 * {@code rest} is how far above the ground its pivot settles. Returns its frame before and after (for its own pieces).
	 */
	private Matrix4f[] fly(ModelPart part, Matrix4f parentFrom, Matrix4f parentTo, Vector3f centre, int seed, int key, float rest, float s) {
		Vector3f from = parentFrom.transformPosition(part.x, part.y, part.z, new Vector3f());
		Matrix4f partFrom = pose(new Matrix4f(parentFrom), part);
		// outward from the body's middle, give or take, at its own speed and pop
		float away = (float)Mth.atan2(from.z - centre.z, from.x - centre.x) + (noise(seed, key * 8 + 1) - 0.5F) * SCATTER_SPREAD;
		float speed = SCATTER_SPEED * (0.6F + 0.8F * noise(seed, key * 8 + 2));
		float pop = POP * (0.5F + noise(seed, key * 8 + 3));
		// up, down to the ground, then one small bounce and still
		float floor = Math.max(GROUND - rest, from.y);
		float land = (pop + Mth.sqrt(pop * pop + 2.0F * GRAVITY * (floor - from.y))) / GRAVITY;
		float bounce = (GRAVITY * land - pop) * BOUNCE, hop = 2.0F * bounce / GRAVITY;
		float drift, y, turn;
		if (s < land) {
			drift = speed * s;
			y = from.y - pop * s + 0.5F * GRAVITY * s * s;
			turn = s;
		} else {
			float u = Math.min(s - land, hop);
			drift = speed * (land + 0.4F * u);
			y = floor - (bounce * u - 0.5F * GRAVITY * u * u);
			turn = land + 0.3F * u;
		}
		Vector3f to = new Vector3f(from.x + Mth.cos(away) * drift, y, from.z + Mth.sin(away) * drift);
		parentTo.invert(new Matrix4f()).transformPosition(to);
		part.x = to.x;
		part.y = to.y;
		part.z = to.z;
		part.xRot += (noise(seed, key * 8 + 4) - 0.5F) * 2.0F * SPIN * turn;
		part.yRot += (noise(seed, key * 8 + 5) - 0.5F) * SPIN * turn;
		part.zRot += (noise(seed, key * 8 + 6) - 0.5F) * 2.0F * SPIN * turn;
		return new Matrix4f[] {partFrom, pose(new Matrix4f(parentTo), part)};
	}

	/** {@code frame} carried into {@code part}'s own frame, as {@link ModelPart#translateAndRotate} does (in pixels). */
	private static Matrix4f pose(Matrix4f frame, ModelPart part) {
		return frame.translate(part.x, part.y, part.z).rotateZYX(part.zRot, part.yRot, part.xRot);
	}

	/** A steady "random" number, 0 to 1, for this Giant ({@code seed}) and this {@code key}: the same every frame. */
	private static float noise(int seed, int key) {
		int h = seed * 0x9E3779B1 + key * 0x85EBCA77;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		h *= 0x297A2D39;
		h ^= h >>> 15;
		return (h >>> 8) / 16777216.0F;
	}

	/** How high an arm is raised {@code ticks} into a blow: up over the head, then down hard. */
	private static float blow(float ticks) {
		return BLOW_LIFT * Mth.sin(Math.min(ticks / BLOW_TICKS, 1.0F) * Mth.PI);
	}
}
