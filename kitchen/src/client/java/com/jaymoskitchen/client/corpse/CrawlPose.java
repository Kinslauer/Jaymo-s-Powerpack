package com.jaymoskitchen.client.corpse;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * A creature hit in the legs is drawn down on the ground crawling for a while (see LegShots): humanoids lie flat,
 * haul themselves along on their arms and look up at what they're after; four-legged animals sink their hindquarters and drag their back legs.
 * The amount (0 standing to 1 down) is set on the render state by {@code LivingEntityRendererMixin}.
 * <p>
 * (0.5.9) The humanoid's crawl is an army crawl, as Jaymo's Powers draws a downed player's: each arm sweeps ahead along
 * the ground, lifted coming forward, and pulls back out to the side planted while the other comes forward; the opposite
 * knee drives up the side; the body rocks onto the driving side; the head lifts to look ahead on each pull.
 */
public final class CrawlPose {
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:crawl");

	/** The stroke: radians a tick (a struggle in place) and per unit of the walk cycle (for ground covered). */
	static final float STROKE = 0.08F, STROKE_STRIDE = 0.35F;
	/** How much of the stroke shows: at the least, and per unit of walk speed. */
	static final float EFFORT_MIN = 0.35F, EFFORT_PER_SPEED = 4.0F;
	/**
	 * The arms sweep flat along the ground (face down, a roll of PI points an arm ahead past the head, PI/2 straight out
	 * to its side): reached, pulled; dipped to the ground (- is down) and lifted coming forward.
	 */
	static final float REACH_SWEEP = 2.6F, PULL_SWEEP = 1.1F, ARM_DIP = -0.25F, REACH_LIFT = 0.35F;
	/** The knees driven up the side (radians out), at rest and at the drive, and the foot lifted with it. */
	static final float LEG_OUT = 0.1F, KNEE_DRIVE = 0.55F, KNEE_LIFT = 0.15F;
	/** The body's rock onto the driving side (degrees), and the head's lift on each pull (radians). */
	static final float ROCK_DEGREES = 6.0F, HEAD_LIFT = 0.3F;

	private CrawlPose() {
	}

	public static void extract(LivingEntityRenderState state, float amount) {
		state.setData(KEY, amount > 0.0F ? amount : null);
	}

	/** After the body's been turned to face its way: lay it down. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Float amount = state.getData(KEY);
		if (amount == null) {
			return;
		}
		float height = state.boundingBoxHeight / Math.max(entityScale, 0.01F);
		if (state instanceof HumanoidRenderState humanoid) {
			if (humanoid.isVisuallySwimming) {
				// already lying down (a player crawling or swimming anyway)
				return;
			}
			// face down along the ground, as a crawling player lies, rocking with the stroke (about its length)
			poseStack.rotate(Axis.XP.rotationDegrees(-90.0F * amount));
			poseStack.translate(0.0F, -height * 0.555F * amount, 0.3F * amount);
			poseStack.rotate(Axis.YP.rotationDegrees(ROCK_DEGREES * Mth.sin(phase(state)) * effort(state) * amount));
		} else {
			// hindquarters on the ground, front legs still holding it up
			poseStack.rotate(Axis.XP.rotationDegrees(14.0F * amount));
			poseStack.translate(0.0F, -height * 0.12F * amount, 0.0F);
		}
	}

	/**
	 * After the model animates. A humanoid hauls itself along on its forearms, one arm reaching ahead and pulling back
	 * while the other does the opposite, its legs dragging, its head raised to look where it's facing (at whatever it's
	 * after). A four-legged creature's back legs drag out behind it.
	 */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living)) {
			return;
		}
		Float amount = living.getData(KEY);
		if (amount == null) {
			return;
		}
		if (model instanceof HumanoidModel<?> humanoid && state instanceof HumanoidRenderState body) {
			if (!body.isVisuallySwimming) {
				crawl(humanoid, living, amount);
			}
			return;
		}
		ModelPart root = model.root();
		for (String leg : new String[] {"right_hind_leg", "left_hind_leg"}) {
			if (root.hasChild(leg)) {
				ModelPart part = root.getChild(leg);
				part.xRot = part.xRot * (1.0F - amount) + 1.3F * amount;
			}
		}
	}

	/**
	 * The body lies face down, so in the model's own terms "forward along the ground" is up the body (toward the top
	 * of the head) and "up" is out of its back. A part's rotations apply x, then y, then z.
	 */
	private static void crawl(HumanoidModel<?> model, LivingEntityRenderState state, float amount) {
		// hauling: quick when it's actually moving, a weak struggle when it's barely going anywhere
		float effort = effort(state);
		float phase = phase(state);
		float sin = Mth.sin(phase);
		// each arm's reach (0 pulled back to the side, 1 ahead); the right comes forward, lifted, while sin is rising
		float rightReach = 0.5F - 0.5F * Mth.cos(phase), leftReach = 1.0F - rightReach;
		float rightSweep = Mth.lerp(rightReach, PULL_SWEEP, REACH_SWEEP), leftSweep = Mth.lerp(leftReach, PULL_SWEEP, REACH_SWEEP);
		pose(model.rightArm, ARM_DIP + REACH_LIFT * Math.max(0.0F, sin) * effort, rightSweep, amount);
		pose(model.leftArm, ARM_DIP + REACH_LIFT * Math.max(0.0F, -sin) * effort, -leftSweep, amount);
		// the opposite knee drives up the side as an arm reaches; otherwise the legs drag
		float rightDrive = Math.max(0.0F, -sin) * effort, leftDrive = Math.max(0.0F, sin) * effort;
		pose(model.rightLeg, 0.05F + KNEE_LIFT * rightDrive, LEG_OUT + KNEE_DRIVE * rightDrive, amount);
		pose(model.leftLeg, 0.05F + KNEE_LIFT * leftDrive, -LEG_OUT - KNEE_DRIVE * leftDrive, amount);
		// head: tipped back to look along the ground, lifting on each pull, then turned and tilted toward where it's
		// looking; turning a head left or right is now about the body's front-to-back axis
		ModelPart head = model.head;
		float yaw = state.yRot * Mth.DEG_TO_RAD;
		float pitch = state.xRot * Mth.DEG_TO_RAD;
		float lift = Math.abs(sin) * effort * HEAD_LIFT;
		head.xRot = Mth.lerp(amount, head.xRot, -Mth.HALF_PI + Mth.clamp(pitch, -0.6F, 0.8F) - lift + 0.3F);
		head.yRot = Mth.lerp(amount, head.yRot, 0.0F);
		head.zRot = Mth.lerp(amount, head.zRot, -Mth.clamp(yaw, -1.2F, 1.2F));
	}

	/** Where in the stroke: a slow struggle, quicker for the ground it covers. */
	private static float phase(LivingEntityRenderState state) {
		return state.ageInTicks * STROKE + state.walkAnimationPos * STROKE_STRIDE;
	}

	/** How much of the stroke shows: a weak struggle when it's barely moving. */
	private static float effort(LivingEntityRenderState state) {
		return Math.min(1.0F, EFFORT_MIN + state.walkAnimationSpeed * EFFORT_PER_SPEED);
	}

	private static void pose(ModelPart part, float xRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, 0.0F);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
