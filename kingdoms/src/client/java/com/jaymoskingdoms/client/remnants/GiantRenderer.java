package com.jaymoskingdoms.client.remnants;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.remnants.AetherGiant;
import com.jaymoskingdoms.remnants.Element;
import com.jaymoskingdoms.remnants.Monoliths;
import java.util.Optional;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the Aether's Giants ({@link AetherGiant}) with GeckoLib: one model
 * ({@code geckolib/models/entity/aether_giant.geo.json}, the body the old GiantModel built in code), drawn {@link #SCALE}
 * times over, in its element's skin ({@code tools/gen_giants.py}). Andy: "is it possible to animate our giants with
 * blockbench and geckolib?" Its breathing, raised fists, blows, stamp, grab and fall are keyframes now
 * ({@code kingdoms/art/aether_giant.bbmodel}; {@link AetherGiant#registerControllers} plays them).
 * <p>
 * What follows the game frame by frame is still worked out here, on top of the animation, with the old model's numbers:
 * <ul>
 * <li>its stride: the legs keep to the server's steps (one every {@code AetherGiant#STEP_LENGTH} blocks), it sinks onto
 * each footfall, its weight rolls onto that foot and its shoulders turn with the arm swinging forward (Andy: "a slow
 * sway ... removing that fast swing");</li>
 * <li>its head turning where it looks, and its flinch when hit;</li>
 * <li>its break-up: once the fall has it on its face, every piece is thrown out from the body on its own line, pops up,
 * comes down, bounces once and lies still, spinning (Andy: "keep our old models, and the new animations and the
 * shatter"). The server throws up its stone as it breaks ({@code AetherGiant#tickDeath}).</li>
 * </ul>
 * The sums are done as the old model did them, on each bone's place and angles the way a {@code ModelPart} has them
 * (pixels, y down; GeckoLib's x and y turn the other way), read from the animated bone and put back ({@link Part}).
 */
public class GiantRenderer extends GeoEntityRenderer<AetherGiant, LivingEntityRenderState> {
	/** How much bigger than its model it's drawn: to its full height (the model stands about 2.64 blocks). */
	static final float SCALE = AetherGiant.HEIGHT / 2.64F;
	static final float SHADOW = AetherGiant.WIDTH;
	/** Its hunch (radians): the body's resting pitch, and the head's against it. */
	private static final float HUNCH = 0.22F;
	/**
	 * How far its legs swing, and its arms' swing, twist and flare at each stride's peak; how far its weight rolls and its
	 * shoulders turn; its legs' length (pixels), for how far it sinks onto a footfall.
	 */
	private static final float LEG_SWING = 0.6F, ARM_SWING = 0.55F, ARM_TWIST = 0.18F, ARM_FLARE = 0.2F, ARM_FLARE_PEAK = 0.18F, ARM_FORWARD = 0.2F,
		ROLL = 0.07F, SHOULDER_TWIST = 0.09F, LEG_LENGTH = 14.0F;
	/** Hauling (sprinting): its arms swing and twist this share of a walk's, and it hunches this much more. */
	private static final float HAUL_ARMS = 0.3F, HAUL_HUNCH = 0.15F;
	/** How it flinches when hit (body, head, arms, and the arms thrown out). */
	private static final float FLINCH_BODY = 0.42F, FLINCH_HEAD = 0.6F, FLINCH_ARM = 0.6F, FLINCH_FLARE = 0.5F;
	/** How far its head turns to look (degrees), and how much of the look's pitch it takes. */
	private static final float LOOK_YAW = 50.0F, LOOK_PITCH = 0.5F;
	/** Its death: how long the fall takes (the {@code death_*} animations' length, ticks) and when vanilla takes the body away. */
	private static final float DEATH_TICKS = 20.0F, FALL_TICKS = 9.0F;
	/** Its break-up: the ground (pixels below the root), how fast pieces fly out (pixels a tick), their pop up, gravity, bounce, spin, and how widely they scatter. */
	private static final float GROUND = 24.0F, SCATTER_SPEED = 2.2F, POP = 3.0F, GRAVITY = 1.6F, BOUNCE = 0.35F, SPIN = 0.5F,
		SCATTER_SPREAD = 1.4F;
	/** The thud of its chest hitting the ground (pixels up, over this many ticks). */
	private static final float THUD = 2.0F, THUD_TICKS = 4.0F;

	static final DataTicket<Float> STRIDE = DataTicket.create("jaymoskingdoms_giant_stride", Float.class);
	static final DataTicket<Float> FLINCH = DataTicket.create("jaymoskingdoms_giant_flinch", Float.class);
	static final DataTicket<Integer> SEED = DataTicket.create("jaymoskingdoms_giant_seed", Integer.class);
	/** Hauling; and each arm busy with a blow or the grab (it doesn't swing with the stride then). */
	static final DataTicket<Boolean> HAULING = DataTicket.create("jaymoskingdoms_giant_hauling", Boolean.class);
	static final DataTicket<Boolean> RIGHT_BUSY = DataTicket.create("jaymoskingdoms_giant_right_busy", Boolean.class);
	static final DataTicket<Boolean> LEFT_BUSY = DataTicket.create("jaymoskingdoms_giant_left_busy", Boolean.class);

	GiantRenderer(EntityRendererProvider.Context context, Element element) {
		super(context, new Model(element));
		this.withScale(SCALE);
		this.shadowRadius = SHADOW;
	}

	@Override
	public void addRenderData(AetherGiant giant, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(giant, related, state, partialTick);
		boolean grabbing = giant.grabTick() >= 0 && giant.grabTick() < AetherGiant.GRAB_TOTAL;
		state.addGeckolibData(STRIDE, giant.stride(partialTick));
		state.addGeckolibData(FLINCH, giant.hurtTime > 0 && giant.hurtDuration > 0 ? (giant.hurtTime - partialTick) / giant.hurtDuration : 0.0F);
		state.addGeckolibData(SEED, giant.getId());
		state.addGeckolibData(HAULING, giant.isSprinting());
		state.addGeckolibData(RIGHT_BUSY, grabbing || giant.rightCounter() > 0);
		state.addGeckolibData(LEFT_BUSY, grabbing || giant.leftCounter() > 0);
	}

	/** It doesn't topple sideways when it dies: it falls on its face and breaks up instead. */
	@Override
	protected float getDeathMaxRotation(GeoRenderState state) {
		return 0.0F;
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(info, snapshots);
		Part body = Part.of(snapshots, "body", 0, 10, 0, HUNCH), shards = Part.of(snapshots, "shards", 0, 0, 0, 0),
			head = Part.of(snapshots, "head", 0, -19, -4, -HUNCH), crown = Part.of(snapshots, "crown", 0, 0, 0, 0),
			rightArm = Part.of(snapshots, "right_arm", -10, -17, 0, 0), leftArm = Part.of(snapshots, "left_arm", 10, -17, 0, 0),
			rightLeg = Part.of(snapshots, "right_leg", -5, 10, 0, 0), leftLeg = Part.of(snapshots, "left_leg", 5, 10, 0, 0);
		Part[] all = {body, shards, head, crown, rightArm, leftArm, rightLeg, leftLeg};
		for (Part part : all) {
			if (part == null) {
				return;
			}
		}
		LivingEntityRenderState state = info.renderState();
		float death = Math.min(state.deathTime, DEATH_TICKS);
		// it stops looking about as it falls
		float looking = death > 0.0F ? 1.0F - Mth.square(Math.min(death / FALL_TICKS, 1.0F)) : 1.0F;
		head.yRot += Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD * looking;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * LOOK_PITCH * looking;
		if (death > 0.0F) {
			if (death > FALL_TICKS) {
				shatter(info.getOrDefaultGeckolibData(SEED, 0), death - FALL_TICKS, body, shards, head, crown, rightArm, leftArm, rightLeg, leftLeg);
			}
		} else {
			this.stride(info, body, head, rightArm, leftArm, rightLeg, leftLeg);
			float flinch = info.getOrDefaultGeckolibData(FLINCH, 0.0F);
			if (flinch > 0.0F) {
				// rocked back by a hit
				body.xRot -= flinch * FLINCH_BODY;
				head.xRot -= flinch * FLINCH_HEAD;
				rightArm.xRot -= flinch * FLINCH_ARM;
				rightArm.zRot += flinch * FLINCH_FLARE;
				leftArm.xRot -= flinch * FLINCH_ARM;
				leftArm.zRot -= flinch * FLINCH_FLARE;
			}
		}
		for (Part part : all) {
			part.store();
		}
	}

	/** Its stride, added to whatever the animation has it doing. */
	private void stride(RenderPassInfo<LivingEntityRenderState> info, Part body, Part head, Part rightArm, Part leftArm, Part rightLeg, Part leftLeg) {
		// 1 as its right foot comes down ahead (even steps), -1 as its left does
		float walk = Mth.cos(info.getOrDefaultGeckolibData(STRIDE, 0.0F) * Mth.PI), amount = Math.min(info.renderState().walkAnimationSpeed, 1.0F);
		boolean hauling = info.getOrDefaultGeckolibData(HAULING, false);
		float swing = walk * LEG_SWING * amount;
		rightLeg.xRot -= swing;
		leftLeg.xRot += swing;
		// it sinks onto each footfall (a swung leg reaches less far down), its weight rolling onto that foot, its shoulders turning
		float sink = LEG_LENGTH * (1.0F - Mth.cos(swing));
		body.y += sink;
		rightLeg.y += sink;
		leftLeg.y += sink;
		body.zRot -= walk * ROLL * amount;
		body.yRot += walk * SHOULDER_TWIST * amount;
		if (hauling) {
			body.xRot += HAUL_HUNCH * amount;
		}
		// its head keeps level as it rolls
		head.zRot += walk * ROLL * 0.6F * amount;
		// arms swing against the legs, twist, and flare out wide of the chest, more at the height of each swing (hauling,
		// hardly at all: it doesn't flail); an arm in a blow or the grab is left to that
		float arms = hauling ? HAUL_ARMS : 1.0F;
		if (!info.getOrDefaultGeckolibData(RIGHT_BUSY, false)) {
			rightArm.xRot += (walk * ARM_SWING * arms - ARM_FORWARD) * amount;
			rightArm.yRot -= walk * ARM_TWIST * arms * amount;
			rightArm.zRot += Math.abs(walk) * ARM_FLARE_PEAK * amount;
		}
		if (!info.getOrDefaultGeckolibData(LEFT_BUSY, false)) {
			leftArm.xRot += (-walk * ARM_SWING * arms - ARM_FORWARD) * amount;
			leftArm.yRot += walk * ARM_TWIST * arms * amount;
			leftArm.zRot -= Math.abs(walk) * ARM_FLARE_PEAK * amount;
		}
	}

	/**
	 * It breaks up where it fell, {@code s} ticks after it hit the ground: the chest thuds, and every other piece (head,
	 * crown, arms, back shards, legs) flies out from the body on its own line, pops up a little, falls back to the ground,
	 * bounces once and lies still, spinning as it goes. Which way each goes comes from {@code seed}, the same every frame.
	 */
	private static void shatter(int seed, float s, Part body, Part shards, Part head, Part crown, Part rightArm, Part leftArm, Part rightLeg,
		Part leftLeg) {
		Matrix4f bodyFrom = pose(new Matrix4f(), body);
		body.y -= THUD * Mth.sin(Math.min(s / THUD_TICKS, 1.0F) * Mth.PI);
		Matrix4f bodyTo = pose(new Matrix4f(), body);
		Vector3f centre = bodyFrom.transformPosition(0.0F, -13.0F, 0.0F, new Vector3f());
		Matrix4f none = new Matrix4f();
		Matrix4f[] headFrames = fly(head, bodyFrom, bodyTo, centre, seed, 1, 4.0F, s);
		fly(crown, headFrames[0], headFrames[1], centre, seed, 2, 1.5F, s);
		fly(rightArm, bodyFrom, bodyTo, centre, seed, 3, 4.0F, s);
		fly(leftArm, bodyFrom, bodyTo, centre, seed, 4, 4.0F, s);
		fly(shards, bodyFrom, bodyTo, centre, seed, 5, 2.0F, s);
		fly(rightLeg, none, none, centre, seed, 6, 3.5F, s);
		fly(leftLeg, none, none, centre, seed, 7, 3.5F, s);
	}

	/**
	 * One piece of it thrown loose {@code s} ticks into its break-up, worked out where it lies on the ground (model space,
	 * y down) and put back into its parent's frame as it now stands, so a piece flies free of whatever it hung from.
	 * {@code rest} is how far above the ground its pivot settles. Returns its frame before and after (for its own pieces).
	 */
	private static Matrix4f[] fly(Part part, Matrix4f parentFrom, Matrix4f parentTo, Vector3f centre, int seed, int key, float rest, float s) {
		Vector3f from = parentFrom.transformPosition(part.x, part.y, part.z, new Vector3f());
		Matrix4f partFrom = pose(new Matrix4f(parentFrom), part);
		// outward from the body's middle, give or take, at its own speed and pop
		float away = (float) Mth.atan2(from.z - centre.z, from.x - centre.x) + (noise(seed, key * 8 + 1) - 0.5F) * SCATTER_SPREAD;
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

	/** {@code frame} carried into {@code part}'s own frame, as a {@code ModelPart} would (in pixels). */
	private static Matrix4f pose(Matrix4f frame, Part part) {
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

	/**
	 * One bone as the old model's {@code ModelPart} had it: its pivot from its parent's (pixels, y down) and its angles
	 * (radians), with whatever the animation has added. GeckoLib keeps a bone's move and turn as amounts added to its
	 * rest, its x move and its x and y turns the other way round and y up; {@link #store} puts the sums back that way.
	 */
	private static final class Part {
		private final BoneSnapshot bone;
		private final float pivotX, pivotY, pivotZ, restX;
		float x, y, z, xRot, yRot, zRot;

		private Part(BoneSnapshot bone, float pivotX, float pivotY, float pivotZ, float restX) {
			this.bone = bone;
			this.pivotX = pivotX;
			this.pivotY = pivotY;
			this.pivotZ = pivotZ;
			this.restX = restX;
			this.x = pivotX + bone.getTranslateX();
			this.y = pivotY - bone.getTranslateY();
			this.z = pivotZ + bone.getTranslateZ();
			this.xRot = restX - bone.getRotX();
			this.yRot = -bone.getRotY();
			this.zRot = bone.getRotZ();
		}

		/** The bone called {@code name}, or null if the model has none. */
		static Part of(BoneSnapshots snapshots, String name, float pivotX, float pivotY, float pivotZ, float restX) {
			Optional<BoneSnapshot> bone = snapshots.get(name);
			return bone.isPresent() ? new Part(bone.get(), pivotX, pivotY, pivotZ, restX) : null;
		}

		void store() {
			this.bone.setTranslation(this.x - this.pivotX, this.pivotY - this.y, this.z - this.pivotZ);
			this.bone.setRotation(this.restX - this.xRot, -this.yRot, this.zRot);
		}
	}

	/** The Giants' one model and animations, in this element's skin. */
	private static final class Model extends DefaultedEntityGeoModel<AetherGiant> {
		private final Identifier texture;

		Model(Element element) {
			super(JaymosKingdoms.id("aether_giant"));
			this.texture = JaymosKingdoms.id("textures/entity/giant/" + element.key() + ".png");
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return this.texture;
		}
	}

	public static void register() {
		for (Element element : Element.values()) {
			EntityRenderers.register(Monoliths.GIANTS.get(element), context -> new GiantRenderer(context, element));
		}
	}
}
