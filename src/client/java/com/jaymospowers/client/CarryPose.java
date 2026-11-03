package com.jaymospowers.client;

import com.jaymospowers.client.power.web.WebRagdoll;
import com.jaymospowers.client.power.wind.WindPose;
import com.jaymospowers.core.Carry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * How a carry looks, on both bodies. The carried body stands at its carrier's feet (see {@code CarryClient}); here it's
 * turned to the carrier's facing and moved into the hold (and tipped with the carrier when they cruise in Wind
 * flight), its limbs hanging loose and swaying; the carrier's arms take the hold. A thrown body tumbles and flops for
 * a moment. A Gravity user holds a big mob up overhead, arms raised, the body floating and bobbing there. Distances are
 * blocks (world-sized, whatever the body's scale) in the body's frame before the model is drawn (+X its right, +Y up,
 * -Z its front).
 * <p>
 * (0.15.42) The carrier's side moves with the weight ({@link PoseBlend}): they bend at the hips and knees to pick it up,
 * lift with the legs, and carry it with a heavier, rolling walk, the body swaying and the arms giving a little on each
 * step; setting it down bends them the same way, and the hold eases in and out.
 * <p>
 * (0.19.55) Dragged by the shoulder ({@link Carry#DRAG}): lying on the back along the ground behind the carrier, head
 * toward them, the shoulders held up off the ground in their hand and the body sloping down to the hips; the legs lie
 * flat and ragdoll as they drag ({@code CarryClient.Drag}: kicked up by the bumps, swung aside on turns, bouncing back
 * down), the arms trail limp beside the body and the head lolls back, jolting. The carrier bends forward into the pull,
 * the right arm reaching back and down to the shoulder.
 */
public final class CarryPose {
	/** On a carried body: how it's held. */
	public static final RenderStateDataKey<Held> HELD_KEY = RenderStateDataKey.create(() -> "jaymospowers:carry_held");
	/** On a carrier: the hold their arms take. */
	public static final RenderStateDataKey<Holding> CARRIER_KEY = RenderStateDataKey.create(() -> "jaymospowers:carry_holding");
	/** On a thrown body: ticks since it was thrown. */
	public static final RenderStateDataKey<Float> THROWN_KEY = RenderStateDataKey.create(() -> "jaymospowers:carry_thrown");

	/** The swing ({@code back}, {@code left}: radians-ish, from CarryClient) and wind {@code trail} (0-1) of a held body. */
	public record Held(int hold, boolean animal, int carrierId, float partialTicks, float back, float left, float trail,
		float time, float width, float height, float @Nullable [] drag) {
		Held(int hold, boolean animal, int carrierId, float partialTicks, float back, float left, float trail, float time, float width,
			float height) {
			this(hold, animal, carrierId, partialTicks, back, left, trail, time, width, height, null);
		}
	}

	/** {@code width}: of what's held (blocks), so the arms spread round a wide one overhead. */
	public record Holding(int hold, boolean animal, float time, float width) {
	}

	/** Where a player's body joins sit, in its own frame (the model is drawn 0.9375 of its size): hip, chest, eyes. */
	static final float HIP = 0.70F, CHEST = 1.06F, EYE = 1.64F, BELLY = 0.13F;
	/** Fireman's carry: hips on the carrier's right shoulder, folded over it this far (degrees). */
	static final float FIRE_X = 0.28F, FIRE_Y = 1.45F, FIRE_Z = 0.02F, FIRE_TIP = 105.0F;
	/** Bridal carry: across the arms this far ahead and this high, the head end raised a little (90 = flat). */
	static final float BRIDAL_AHEAD = 0.48F, BRIDAL_Y = 1.0F, BRIDAL_TIP = 76.0F;
	/** Shoulder grab: hung at the carrier's right side, gripped by the left shoulder, leaning in. */
	static final float SHOULDER_X = 0.66F, SHOULDER_Y = -0.5F, SHOULDER_Z = 0.05F, SHOULDER_LEAN = 10.0F;
	/** Over the back: back to back, a little higher, leaning onto the carrier (and lying on them in a cruise). */
	static final float BACK_Y = 0.12F, BACK_Z = 0.3F, BACK_TIP = 14.0F;
	/**
	 * (0.19.55) Dragged: the shoulders held this high (blocks; the hips' back then just touches the ground), the body
	 * sloping down from them this steeply (degrees), turned about the collar this far up the body. The arms lie this far
	 * out from the sides, the head lolled back this far (radians).
	 */
	static final float DRAG_LIFT = 0.53F, DRAG_INCLINE = 40.0F, DRAG_COLLAR = 1.35F, DRAG_ARMS_OUT = 0.32F, DRAG_HEAD = -0.55F;
	/** (0.19.55) The carrier dragging: the right arm back and down (radians), out a little; leaning into the pull, sunk this much (pixels). */
	static final float DRAG_ARM = 0.8F, DRAG_ARM_OUT = 0.18F, DRAG_LEAN = 0.32F, DRAG_SINK = 1.8F;
	/** How far (degrees) the whole held body swings per unit of sway, and how wide the limbs swing (radians). */
	static final float BODY_SWAY = 7.0F, LIMB_SWAY = 0.55F;
	/** A thrown body: how far it turns over in the air (degrees), and how hard its limbs flop. */
	static final float THROWN_SPIN = 160.0F, THROWN_FLOP = 0.9F;
	/** Overhead (Gravity): the underside this high over the carrier's feet, bobbing this far (blocks) at this pace. */
	static final float OVERHEAD_Y = 2.15F, OVERHEAD_BOB = 0.07F, OVERHEAD_BOB_SPEED = 0.09F;
	/** Overhead: it slowly drifts this far (degrees) round and over, and its limbs float this loosely. */
	static final float OVERHEAD_DRIFT_YAW = 9.0F, OVERHEAD_DRIFT_TILT = 4.0F, OVERHEAD_FLOAT = 0.22F;
	/** Overhead: something this much taller than it's wide (a zombie, an enderman) is held up lying across. */
	static final float OVERHEAD_LIE_RATIO = 1.4F;
	/** Overhead: the carrier's arms straight up (radians from hanging), spread this much per block of its width. */
	static final float OVERHEAD_ARMS = -2.95F, OVERHEAD_SPREAD = 0.14F, OVERHEAD_SPREAD_MAX = 0.5F;
	/** Holding a block: both arms raised this far forward (radians). */
	static final float BLOCK_ARMS = -1.1F;
	/** Holding a severed part: the right arm a little forward (radians), out from the side. */
	static final float PART_ARM = -0.5F, PART_ARM_OUT = 0.12F;
	/** (0.15.42) Picking up: reaching down (seconds), then lifting until; setting down takes this long before the hold eases out. */
	static final float PICKUP_REACH = 0.25F, PICKUP_TIME = 0.6F, SET_DOWN_TIME = 0.4F;
	/** The bend to pick up or set down: at the hips (radians), lowered (model pixels), knees, arms reaching down, head. */
	static final float BEND_PITCH = 0.75F, BEND_DROP = 2.6F, BEND_KNEES = -0.35F, BEND_ARMS = -1.05F, BEND_ARMS_OUT = 0.12F,
		BEND_HEAD = 0.3F;
	/** The weighted walk: the body's sway (radians), its roll through the hips, the bob (pixels), the arms' give a step. */
	static final float WALK_SWAY = 0.06F, WALK_ROLL = 0.05F, WALK_BOB = 0.6F, WALK_GIVE = 0.08F;
	/** Leaning against the weight: back for a load in front, forward for one on the shoulder or back (radians). */
	static final float LEAN_FRONT = -0.08F, LEAN_BACK = 0.1F;
	private static final PoseBlend.Channel CARRIER_BLEND = PoseBlend.channel("carry_carrier");

	/** First person: how the carrier's arms sit for each hold. */
	public static final float FIRST_PERSON_EQUIP = 0.12F;

	/** The flopping parts, wherever a model has them (on its root, or on its body). */
	private static final String[] LIMBS = {
		"head", "right_arm", "left_arm", "right_leg", "left_leg",
		"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg",
		"right_wing", "left_wing", "tail", "right_ear", "left_ear"
	};

	private CarryPose() {
	}

	// ---- render state

	/** From the living and avatar renderers' extractRenderState (last, so the carry wins). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Carry.Carrying carrying = entity.getAttached(Carry.CARRYING);
		Entity target = carrying == null ? null : entity.level().getEntity(carrying.target());
		state.setData(CARRIER_KEY, carrying == null ? null
			: new Holding(carrying.hold(), !Carry.person(target), entity.tickCount + partialTicks, target == null ? 0.6F : target.getBbWidth()));
		Long thrown = entity.getAttached(Carry.THROWN);
		float since = thrown == null ? -1.0F : entity.level().getGameTime() - thrown + partialTicks;
		state.setData(THROWN_KEY, since >= 0.0F && since < Carry.THROWN_TICKS ? since : null);
		Player carrier = Carry.carrierOf(entity);
		Carry.Carrying hold = carrier == null ? null : carrier.getAttached(Carry.CARRYING);
		if (hold == null || hold.target() != entity.getId()) {
			state.setData(HELD_KEY, null);
			return;
		}
		float[] sway = CarryClient.swayOf(entity, partialTicks);
		float[] drag = hold.hold() == Carry.DRAG ? CarryClient.dragOf(entity, partialTicks) : null;
		state.setData(HELD_KEY, new Held(hold.hold(), !Carry.person(entity), carrier.getId(), partialTicks, sway[0], sway[1], sway[2],
			entity.tickCount + partialTicks, entity.getBbWidth(), entity.getBbHeight(), drag));
		// turned to the carrier's facing (or (0.19.55) dragged, lying the way it's dragged), limp: no walking, crouching,
		// swimming or anything else's pose
		state.bodyRot = drag != null ? drag[0] : Mth.rotLerp(partialTicks, carrier.yBodyRotO, carrier.yBodyRot);
		state.yRot = 0.0F;
		state.xRot = 0.0F;
		state.walkAnimationSpeed = 0.0F;
		state.pose = Pose.STANDING;
		state.isAutoSpinAttack = false;
		state.shadowRadius = 0.0F;
		if (state instanceof HumanoidRenderState humanoid) {
			humanoid.isCrouching = false;
			humanoid.swimAmount = 0.0F;
			humanoid.isFallFlying = false;
			humanoid.isVisuallySwimming = false;
			humanoid.isPassenger = false;
		}
		state.setData(FreefallPose.KEY, null);
		state.setData(WebRagdoll.KEY, null);
	}

	// ---- the whole body

	/** After the body faces its way (the carrier's): into the hold, or tumbling from a throw. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Held held = state.getData(HELD_KEY);
		if (held != null) {
			// the hold is laid out in world blocks: undo the body's own scale (a baby's, say) around it
			float scale = Math.max(entityScale, 0.01F);
			poseStack.scale(1.0F / scale, 1.0F / scale, 1.0F / scale);
			Minecraft minecraft = Minecraft.getInstance();
			Entity carrier = minecraft.level == null ? null : minecraft.level.getEntity(held.carrierId());
			if (carrier != null) {
				// cruising, the carrier lies along the flight: so does whatever they hold
				WindPose.tiltLike(carrier, held.partialTicks(), poseStack);
			}
			hold(poseStack, held);
			poseStack.scale(scale, scale, scale);
			return;
		}
		Float thrown = state.getData(THROWN_KEY);
		if (thrown != null && state.deathTime <= 0.0F) {
			float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
			float t = thrown / Carry.THROWN_TICKS;
			// turning over through the air, settling back upright as it comes down
			float spin = Mth.sin(t * Mth.PI) * THROWN_SPIN * 0.6F;
			poseStack.translate(0.0F, middle, 0.0F);
			poseStack.rotateDegrees(Axis.XP, -spin);
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(thrown * 0.7F) * 12.0F * (1.0F - t));
			poseStack.translate(0.0F, -middle, 0.0F);
		}
	}

	/** Moves a body from its carrier's feet into the hold. */
	private static void hold(PoseStack poseStack, Held held) {
		float back = held.back(), left = held.left();
		if (held.hold() == Carry.DRAG) {
			dragHold(poseStack, held);
			return;
		}
		if (held.hold() == Carry.OVERHEAD) {
			overheadHold(poseStack, held);
			return;
		}
		if (held.animal()) {
			animalHold(poseStack, held);
			return;
		}
		switch (held.hold()) {
			case Carry.FIREMAN -> {
				// hips on the right shoulder, facing behind, folded over it: head and arms down the back, legs in front
				poseStack.translate(FIRE_X, FIRE_Y, FIRE_Z);
				poseStack.rotateDegrees(Axis.YP, 180.0F);
				poseStack.rotateDegrees(Axis.XP, -FIRE_TIP + back * BODY_SWAY);
				poseStack.rotateDegrees(Axis.ZP, -left * BODY_SWAY);
				poseStack.translate(0.0F, -HIP, BELLY);
			}
			case Carry.BRIDAL -> {
				// lying across both arms in front, face up, head to the left
				poseStack.translate(0.0F, BRIDAL_Y, -BRIDAL_AHEAD);
				poseStack.rotateDegrees(Axis.YP, -90.0F);
				poseStack.rotateDegrees(Axis.XP, BRIDAL_TIP + left * BODY_SWAY * 0.5F);
				poseStack.rotateDegrees(Axis.YP, back * BODY_SWAY * 0.5F);
				poseStack.translate(0.0F, -CHEST * 0.8F, 0.0F);
			}
			case Carry.SHOULDER -> {
				// hung by the left shoulder at the carrier's right side, swinging from the grip
				float gripX = -0.3F, gripY = 1.35F;
				poseStack.translate(SHOULDER_X, SHOULDER_Y, SHOULDER_Z);
				poseStack.translate(gripX, gripY, 0.0F);
				poseStack.rotateDegrees(Axis.ZP, -SHOULDER_LEAN - left * BODY_SWAY * 1.5F);
				poseStack.rotateDegrees(Axis.XP, back * BODY_SWAY * 1.5F + held.trail() * 20.0F);
				poseStack.translate(-gripX, -gripY, 0.0F);
			}
			default -> {
				// slung over the back, facing up (out behind), leaning on the carrier
				poseStack.translate(0.0F, BACK_Y, BACK_Z);
				poseStack.rotateDegrees(Axis.YP, 180.0F);
				poseStack.rotateDegrees(Axis.XP, BACK_TIP + back * BODY_SWAY * 0.5F);
				poseStack.rotateDegrees(Axis.ZP, left * BODY_SWAY * 0.5F);
			}
		}
	}

	/**
	 * (0.19.55) Dragged: already lying where it's dragged (CarryClient), facing away from the carrier; laid on its back with
	 * the head toward them, turned about the collar so the shoulders are held up and the body slopes down to the hips,
	 * rocking a little as the legs kick.
	 */
	private static void dragHold(PoseStack poseStack, Held held) {
		float[] drag = held.drag();
		float rock = drag == null ? 0.0F : (drag[2] + drag[4]) * 4.0F;
		float h = held.height() > 0.0F ? Math.min(1.0F, held.height() / 1.8F) : 1.0F;
		poseStack.translate(0.0F, DRAG_LIFT * h, 0.0F);
		poseStack.rotateDegrees(Axis.XP, 90.0F - DRAG_INCLINE);
		poseStack.rotateDegrees(Axis.YP, rock);
		poseStack.translate(0.0F, -DRAG_COLLAR * h, 0.0F);
	}

	/**
	 * Gravity's overhead lift: up above the carrier's head on their raised hands, weightless, bobbing and slowly
	 * drifting round. A tall, thin body (a zombie) lies across the hands face up, head to the left.
	 */
	private static void overheadHold(PoseStack poseStack, Held held) {
		float w = held.width(), h = held.height(), t = held.time();
		boolean lying = h > w * OVERHEAD_LIE_RATIO;
		float thick = lying ? w : h;
		float bob = Mth.sin(t * OVERHEAD_BOB_SPEED) * OVERHEAD_BOB;
		// to its middle, up over the head
		poseStack.translate(0.0F, OVERHEAD_Y + bob + thick * 0.5F, 0.0F);
		poseStack.rotateDegrees(Axis.YP, Mth.sin(t * 0.021F) * OVERHEAD_DRIFT_YAW);
		poseStack.rotateDegrees(Axis.XP, held.back() * BODY_SWAY * 0.6F + Mth.sin(t * 0.05F) * OVERHEAD_DRIFT_TILT);
		poseStack.rotateDegrees(Axis.ZP, -held.left() * BODY_SWAY * 0.6F + Mth.sin(t * 0.037F + 1.0F) * OVERHEAD_DRIFT_TILT);
		if (lying) {
			poseStack.rotateDegrees(Axis.ZP, 90.0F);
			poseStack.rotateDegrees(Axis.YP, -90.0F);
		}
		poseStack.translate(0.0F, -h * 0.5F, 0.0F);
	}

	/** A small animal: a simpler cradle for each hold, set by its size. */
	private static void animalHold(PoseStack poseStack, Held held) {
		float w = held.width(), h = held.height();
		float back = held.back(), left = held.left();
		switch (held.hold()) {
			case Carry.FIREMAN -> {
				// draped over the right shoulder, across it
				poseStack.translate(FIRE_X + 0.04F, FIRE_Y - h * 0.3F, FIRE_Z);
				poseStack.rotateDegrees(Axis.YP, 90.0F);
				poseStack.rotateDegrees(Axis.ZP, back * BODY_SWAY);
			}
			case Carry.BRIDAL -> {
				// cradled in both arms, rolled half onto its back
				poseStack.translate(0.0F, BRIDAL_Y + 0.05F - h * 0.5F, -(0.3F + w * 0.5F));
				poseStack.rotateDegrees(Axis.YP, 90.0F);
				poseStack.translate(0.0F, h * 0.5F, 0.0F);
				poseStack.rotateDegrees(Axis.ZP, -55.0F + left * BODY_SWAY);
				poseStack.translate(0.0F, -h * 0.5F, 0.0F);
			}
			case Carry.SHOULDER -> {
				// dangling from the right hand by the scruff
				poseStack.translate(0.45F, 0.95F - h, -0.1F);
				poseStack.translate(0.0F, h, 0.0F);
				poseStack.rotateDegrees(Axis.XP, 20.0F + back * BODY_SWAY * 2.0F + held.trail() * 25.0F);
				poseStack.rotateDegrees(Axis.ZP, -left * BODY_SWAY * 2.0F);
				poseStack.translate(0.0F, -h, 0.0F);
			}
			default -> {
				// riding on the back, facing behind
				poseStack.translate(0.0F, 1.2F - h * 0.4F, 0.2F + w * 0.5F);
				poseStack.rotateDegrees(Axis.YP, 180.0F);
				poseStack.rotateDegrees(Axis.XP, back * BODY_SWAY);
			}
		}
	}

	// ---- limbs

	/** After the model animates: the carried body's loose limbs, the carrier's arms, a thrown body's flop. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living)) {
			return;
		}
		Held held = living.getData(HELD_KEY);
		if (held != null) {
			if (held.hold() == Carry.DRAG) {
				if (model instanceof HumanoidModel<?> h) {
					draggedLimbs(h, held);
				} else {
					flop(model.root(), held.time() * 0.3F, 0.12F);
				}
				return;
			}
			if (held.hold() == Carry.OVERHEAD) {
				// weightless: everything drifts slowly, as if underwater
				flop(model.root(), held.time() * 0.45F, OVERHEAD_FLOAT + Math.min(0.3F, Math.abs(held.back()) + Math.abs(held.left())) * 0.5F);
			} else if (model instanceof HumanoidModel<?> h && !held.animal()) {
				carriedLimbs(h, held);
			} else {
				flop(model.root(), held.time(), 0.25F + Math.min(0.6F, Math.abs(held.back()) + Math.abs(held.left()) + held.trail()));
			}
			return;
		}
		if (model instanceof HumanoidModel<?> h) {
			carrier(h, living, living.getData(CARRIER_KEY));
		}
		Float thrown = living.getData(THROWN_KEY);
		if (thrown != null) {
			flop(model.root(), thrown, THROWN_FLOP * (1.0F - thrown / Carry.THROWN_TICKS));
		}
	}

	/** The carried body's arms and legs: hanging loose, drifting with the sway, trailing in the wind in flight. */
	private static void carriedLimbs(HumanoidModel<?> h, Held held) {
		float t = held.time();
		float back = held.back() * LIMB_SWAY, left = held.left() * LIMB_SWAY;
		float trail = held.trail();
		// a slow float, each limb on its own beat (stronger in the wind)
		float drift = 0.06F + 0.14F * trail;
		float d0 = Mth.sin(t * 0.13F) * drift, d1 = Mth.sin(t * 0.11F + 1.7F) * drift;
		float d2 = Mth.sin(t * 0.09F + 3.1F) * drift, d3 = Mth.sin(t * 0.12F + 4.4F) * drift;
		float flutter = Mth.sin(t * 1.9F) * 0.08F * trail;
		switch (held.hold()) {
			case Carry.FIREMAN -> {
				float tip = FIRE_TIP * Mth.DEG_TO_RAD;
				// everything hangs straight down: the legs in front of the carrier, the arms down their back
				set(h.rightLeg, -tip - back + d0, left + flutter, 0.05F);
				set(h.leftLeg, -tip - back + d1, left - flutter, -0.05F);
				set(h.rightArm, -tip - back * 1.2F + d2, left, 0.15F + d3);
				set(h.leftArm, -tip - back * 1.2F + d3, left, -0.15F - d2);
				set(h.head, 0.45F + d0 * 0.5F, 0.0F, left * 0.3F);
			}
			case Carry.BRIDAL -> {
				// legs over the carrier's arm, the inner arm round their neck, the outer one dangling
				set(h.rightLeg, 0.55F + d0 - left * 0.4F, 0.0F, back * 0.5F + 0.05F);
				set(h.leftLeg, 0.6F + d1 - left * 0.4F, 0.0F, back * 0.5F - 0.05F);
				set(h.rightArm, -2.5F + d2 * 0.4F, 0.0F, -0.3F);
				set(h.leftArm, 1.15F + d3 - left * 0.5F, 0.0F, -0.2F + back * 0.6F);
				set(h.head, -0.15F + d0 * 0.4F, 0.0F, -0.2F);
			}
			case Carry.SHOULDER -> {
				// the held arm up to the grip, the rest dangling; trailing back in the wind
				set(h.leftArm, -0.2F, 0.0F, -0.55F + d0 * 0.3F);
				set(h.rightArm, back + trail * 0.6F + d1, 0.0F, 0.12F + left * 0.4F + flutter);
				set(h.rightLeg, back + trail * 0.5F + d2, 0.0F, 0.06F + left * 0.4F + flutter);
				set(h.leftLeg, back + trail * 0.45F + d3, 0.0F, -0.04F + left * 0.4F - flutter);
				set(h.head, 0.25F + d1 * 0.4F, 0.0F, 0.15F);
			}
			default -> {
				// lying back over the carrier, arms flung loose, legs dangling
				set(h.rightArm, -0.4F - back + d0, 0.0F, 0.75F + d2 + flutter);
				set(h.leftArm, -0.3F - back + d1, 0.0F, -0.7F - d3 - flutter);
				set(h.rightLeg, 0.25F - back * 0.7F + d2 + trail * 0.3F, 0.0F, 0.12F + left * 0.3F);
				set(h.leftLeg, 0.15F - back * 0.7F + d3 + trail * 0.3F, 0.0F, -0.1F + left * 0.3F);
				set(h.head, -0.5F + d0 * 0.4F, 0.0F, 0.0F);
			}
		}
		h.hat.xRot = h.head.xRot;
		h.hat.yRot = h.head.yRot;
		h.hat.zRot = h.head.zRot;
	}

	/**
	 * (0.19.55) A dragged body's limbs: the legs laid flat along the ground behind (bent at the hips against the body's
	 * slope) and ragdolling as CarryClient kicks and swings them; the arms trailing limp out to the sides; the head lolled
	 * back and jolting.
	 */
	private static void draggedLimbs(HumanoidModel<?> h, Held held) {
		float[] drag = held.drag();
		float flat = -DRAG_INCLINE * Mth.DEG_TO_RAD;
		float rightLift = 0.0F, rightAside = 0.12F, leftLift = 0.0F, leftAside = -0.12F, head = 0.0F;
		if (drag != null) {
			rightLift = drag[1];
			rightAside = drag[2];
			leftLift = drag[3];
			leftAside = drag[4];
			head = drag[5];
		}
		set(h.rightLeg, flat + rightLift, 0.0F, rightAside);
		set(h.leftLeg, flat + leftLift, 0.0F, leftAside);
		// the arms trail along the ground beside the body, dragged loose by the legs' jolts
		set(h.rightArm, flat * 0.8F + rightLift * 0.3F, 0.0F, DRAG_ARMS_OUT + rightAside * 0.3F);
		set(h.leftArm, flat * 0.8F + leftLift * 0.3F, 0.0F, -DRAG_ARMS_OUT + leftAside * 0.3F);
		set(h.head, DRAG_HEAD + head, 0.0F, (rightAside + leftAside) * 0.4F);
		h.hat.xRot = h.head.xRot;
		h.hat.yRot = h.head.yRot;
		h.hat.zRot = h.head.zRot;
	}

	/**
	 * (0.15.42) The carrier, eased: the bend to pick it up and the lift, the hold's arms over a weighted walk, the bend to set
	 * it down (the last hold is kept on the track for that), all laid on by the hold's weight.
	 */
	private static void carrier(HumanoidModel<?> h, LivingEntityRenderState living, @Nullable Holding holding) {
		if (!(living instanceof AvatarRenderState avatar)) {
			if (holding != null) {
				carrierArms(h, holding);
			}
			return;
		}
		boolean on = holding != null && living.deathTime <= 0.0F;
		PoseBlend.Track before = CARRIER_BLEND.peek(avatar.id);
		Holding last = holding != null ? holding : before != null && before.memo instanceof Holding kept ? kept : null;
		boolean bends = last != null && bends(last.hold());
		PoseBlend.Track track = CARRIER_BLEND.track(avatar.id, on, bends ? SET_DOWN_TIME : 0.0F, 1.0F);
		if (track == null) {
			return;
		}
		if (on) {
			track.memo = holding;
		}
		if (last == null || living.deathTime > 0.0F) {
			track.apply(h, null);
			return;
		}
		PoseBlend.Frame f = new PoseBlend.Frame(h);
		// the hold's own arms, set as before and taken as the target
		carrierArms(h, last);
		f.take(h);
		float t = track.age();
		float bend = !bends ? 0.0F : on ? 1.0F - PoseBlend.seg(t, PICKUP_REACH, PICKUP_TIME) : PoseBlend.seg(t, 0.0F, SET_DOWN_TIME * 0.6F);
		float carry = 1.0F - bend;
		// the weighted walk: the body rolling over each step, the arms giving under the load
		float speed = Mth.clamp(avatar.walkAnimationSpeed, 0.0F, 1.0F);
		float cycle = avatar.walkAnimationPos * 0.6662F;
		float step = Math.abs(Mth.sin(cycle)) * speed * carry;
		f.add(PoseBlend.HIP_ROLL, Mth.sin(cycle) * WALK_SWAY * speed * carry);
		f.add(PoseBlend.HIP_YAW, Mth.cos(cycle) * WALK_ROLL * speed * carry);
		f.add(PoseBlend.DROP, step * WALK_BOB);
		f.add(PoseBlend.RIGHT_ARM, step * WALK_GIVE);
		f.add(PoseBlend.LEFT_ARM, step * WALK_GIVE);
		if (last.hold() == Carry.DRAG) {
			// (0.19.55) sunk a little at the knees, hauling
			f.add(PoseBlend.DROP, DRAG_SINK * carry);
		}
		float lean = switch (last.hold()) {
			case Carry.DRAG -> DRAG_LEAN;
			case Carry.BRIDAL, Carry.BLOCK -> LEAN_FRONT;
			case Carry.FIREMAN, Carry.SHOULDER, Carry.PART, Carry.OVERHEAD -> 0.0F;
			default -> LEAN_BACK;
		};
		f.add(PoseBlend.HIP_PITCH, lean * carry);
		f.add(PoseBlend.HEAD, -lean * carry);
		if (bend > 0.0F) {
			// bent at the hips and knees, the arms down to it, eyes on it
			f.add(PoseBlend.HIP_PITCH, BEND_PITCH * bend);
			f.add(PoseBlend.DROP, BEND_DROP * bend);
			f.add(PoseBlend.HEAD, (BEND_HEAD - BEND_PITCH) * bend);
			f.mix(PoseBlend.RIGHT_LEG, BEND_KNEES, 0.0F, 0.05F, bend);
			f.mix(PoseBlend.LEFT_LEG, BEND_KNEES, 0.0F, -0.05F, bend);
			f.mix(PoseBlend.RIGHT_ARM, BEND_ARMS, 0.0F, BEND_ARMS_OUT, bend);
			f.mix(PoseBlend.LEFT_ARM, BEND_ARMS, 0.0F, -BEND_ARMS_OUT, bend);
		}
		track.apply(h, f);
	}

	/** (0.15.42) Whether a hold is picked up off the ground (and set down there): not a part in the hand, nor Gravity's lift. */
	private static boolean bends(int hold) {
		return hold != Carry.PART && hold != Carry.OVERHEAD;
	}

	/** The carrier's arms in the hold (players), or round the little animal. */
	private static void carrierArms(HumanoidModel<?> h, Holding holding) {
		if (holding.hold() == Carry.PART) {
			// a head or a limb dangling from the right hand (the left is free)
			set(h.rightArm, PART_ARM, 0.0F, PART_ARM_OUT);
			return;
		}
		if (holding.hold() == Carry.DRAG) {
			// (0.19.55) the right hand back and down at the dragged one's shoulder (the left swings free)
			set(h.rightArm, DRAG_ARM, 0.0F, DRAG_ARM_OUT);
			return;
		}
		if (holding.hold() == Carry.BLOCK) {
			// both arms out in front, hands on either side of the block
			set(h.rightArm, BLOCK_ARMS, -0.1F, 0.0F);
			set(h.leftArm, BLOCK_ARMS, 0.1F, 0.0F);
			return;
		}
		if (holding.hold() == Carry.OVERHEAD) {
			// both arms straight up under it, spread round a wide one, giving a little with its bob
			float spread = Math.min(OVERHEAD_SPREAD_MAX, 0.08F + holding.width() * OVERHEAD_SPREAD);
			float give = Mth.sin(holding.time() * OVERHEAD_BOB_SPEED) * 0.05F;
			set(h.rightArm, OVERHEAD_ARMS + give, 0.0F, -spread);
			set(h.leftArm, OVERHEAD_ARMS + give, 0.0F, spread);
			return;
		}
		if (holding.animal()) {
			switch (holding.hold()) {
				case Carry.FIREMAN -> set(h.rightArm, -2.3F, -0.25F, -0.2F);
				case Carry.BRIDAL -> {
					set(h.rightArm, -0.95F, -0.45F, 0.0F);
					set(h.leftArm, -0.95F, 0.45F, 0.0F);
				}
				case Carry.SHOULDER -> set(h.rightArm, -0.3F, 0.0F, 0.25F);
				default -> set(h.rightArm, -2.8F, 0.0F, -0.2F);
			}
			return;
		}
		switch (holding.hold()) {
			case Carry.FIREMAN -> set(h.rightArm, -1.35F, -0.25F, 0.05F);
			case Carry.BRIDAL -> {
				set(h.rightArm, -1.0F, -0.4F, 0.0F);
				set(h.leftArm, -1.0F, 0.4F, 0.0F);
			}
			case Carry.SHOULDER -> set(h.rightArm, -0.15F, 0.0F, 0.4F);
			default -> {
				set(h.rightArm, 0.55F, 0.0F, 0.3F);
				set(h.leftArm, 0.55F, 0.0F, -0.3F);
			}
		}
	}

	/** Every limb flops on its own beat (as a webbed creature's do). */
	private static void flop(ModelPart root, float time, float strength) {
		flopParts(root, time, strength, 0);
		if (root.hasChild("body")) {
			flopParts(root.getChild("body"), time, strength, LIMBS.length);
		}
	}

	private static void flopParts(ModelPart parent, float time, float strength, int offset) {
		for (int i = 0; i < LIMBS.length; i++) {
			if (!parent.hasChild(LIMBS[i])) {
				continue;
			}
			ModelPart limb = parent.getChild(LIMBS[i]);
			float phase = time * 0.8F + (i + offset) * 1.9F;
			float give = strength * (i == 0 ? 0.4F : 1.0F);
			limb.xRot += Mth.sin(phase) * give;
			limb.zRot += Mth.cos(phase * 1.2F) * give * 0.6F;
		}
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}

	// ---- first person

	/**
	 * Where the carried local player's eyes are, held as they are (their camera goes there in first person), or null
	 * if they aren't carried.
	 */
	public static @Nullable Vec3 carriedEyes(Minecraft minecraft, float partialTicks) {
		Player carrier = CarryClient.localCarrier(minecraft);
		if (carrier == null || minecraft.player == null) {
			return null;
		}
		Carry.Carrying hold = carrier.getAttached(Carry.CARRYING);
		if (hold == null || hold.hold() == Carry.DRAG) {
			// ((0.19.55) dragged: the view stays your own, lying where you're dragged)
			return null;
		}
		float[] sway = CarryClient.swayOf(minecraft.player, partialTicks);
		Held held = new Held(hold.hold(), false, carrier.getId(), partialTicks, sway[0], sway[1], sway[2], 0.0F, 0.6F, 1.8F);
		PoseStack poseStack = new PoseStack();
		poseStack.translate((float)Mth.lerp(partialTicks, carrier.xo, carrier.getX()), (float)Mth.lerp(partialTicks, carrier.yo, carrier.getY()),
			(float)Mth.lerp(partialTicks, carrier.zo, carrier.getZ()));
		poseStack.rotateDegrees(Axis.YP, 180.0F - Mth.rotLerp(partialTicks, carrier.yBodyRotO, carrier.yBodyRot));
		WindPose.tiltLike(carrier, partialTicks, poseStack);
		hold(poseStack, held);
		Vector3f eyes = poseStack.last().pose().transformPosition(new Vector3f(0.0F, EYE, -0.1F));
		return new Vec3(eyes.x(), eyes.y(), eyes.z());
	}

	/** First person, carrying: how one arm sits for the hold ({@code right}: the right arm), or false to leave it out. */
	public static boolean firstPersonArm(PoseStack poseStack, int hold, boolean animal, boolean right) {
		float side = right ? 1.0F : -1.0F;
		if (hold == Carry.OVERHEAD) {
			// both arms up out of sight overhead: only the wrists at the top edge, wide apart
			poseStack.translate(side * 0.16F, 0.5F, 0.1F);
			poseStack.rotateDegrees(Axis.XP, -60.0F);
			poseStack.rotateDegrees(Axis.ZP, side * 8.0F);
			return true;
		}
		if (hold == Carry.DRAG) {
			// (0.19.55) the hand dragging is behind you, out of sight
			return false;
		}
		if (hold == Carry.PART) {
			if (!right) {
				return false;
			}
			// the right hand low at your side, holding it
			poseStack.translate(0.05F, -0.25F, 0.0F);
			poseStack.rotateDegrees(Axis.XP, 20.0F);
			return true;
		}
		if (hold == Carry.BLOCK) {
			// both forearms out low at the block's sides, holding it up
			poseStack.translate(side * 0.08F, -0.2F, -0.05F);
			poseStack.rotateDegrees(Axis.YP, side * 6.0F);
			poseStack.rotateDegrees(Axis.XP, 8.0F);
			return true;
		}
		switch (animal ? -1 - hold : hold) {
			case Carry.FIREMAN, -1 - Carry.FIREMAN, -1 - Carry.SHOULDER, Carry.SHOULDER -> {
				if (!right) {
					return false;
				}
				// up at the shoulder, steadying what's on it (or low at your side, holding on)
				boolean low = hold == Carry.SHOULDER;
				poseStack.translate(0.05F, low ? -0.25F : 0.08F, 0.0F);
				poseStack.rotateDegrees(Axis.XP, low ? 20.0F : -18.0F);
			}
			case Carry.BRIDAL, -1 - Carry.BRIDAL -> {
				// both forearms out under the body, turned in
				poseStack.translate(-side * 0.12F, -0.12F, -0.05F);
				poseStack.rotateDegrees(Axis.YP, side * 12.0F);
				poseStack.rotateDegrees(Axis.XP, 12.0F);
			}
			default -> {
				// reaching back over the shoulders: only the fists at the top corners
				if (animal && !right) {
					return false;
				}
				poseStack.translate(side * 0.1F, 0.35F, 0.25F);
				poseStack.rotateDegrees(Axis.XP, -45.0F);
			}
		}
		return true;
	}
}
