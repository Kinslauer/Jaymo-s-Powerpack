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
 */
public final class CrawlPose {
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:crawl");

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
			// face down along the ground, as a crawling player lies
			poseStack.rotate(Axis.XP.rotationDegrees(-90.0F * amount));
			poseStack.translate(0.0F, -height * 0.555F * amount, 0.3F * amount);
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
		float effort = Math.min(1.0F, 0.35F + state.walkAnimationSpeed * 4.0F);
		float phase = state.ageInTicks * (0.12F + 0.18F * effort);
		float reach = Mth.sin(phase);
		// arms: pointing up the body (pi), one reaching out ahead while the other pulls back under the chest
		pose(model.rightArm, Mth.PI - 0.35F + reach * 0.7F * effort, -0.25F - Math.max(0.0F, -reach) * 0.5F, amount);
		pose(model.leftArm, Mth.PI - 0.35F - reach * 0.7F * effort, 0.25F + Math.max(0.0F, reach) * 0.5F, amount);
		// legs: dragged straight out behind, twitching
		pose(model.rightLeg, 0.08F + Mth.sin(phase + 1.3F) * 0.12F * effort, 0.06F, amount);
		pose(model.leftLeg, 0.08F - Mth.sin(phase + 1.3F) * 0.12F * effort, -0.06F, amount);
		// head: tipped back to look along the ground, then turned and tilted toward where it's looking; turning a head
		// left or right is now about the body's front-to-back axis
		ModelPart head = model.head;
		float yaw = state.yRot * Mth.DEG_TO_RAD;
		float pitch = state.xRot * Mth.DEG_TO_RAD;
		head.xRot = Mth.lerp(amount, head.xRot, -Mth.HALF_PI + Mth.clamp(pitch, -0.6F, 0.8F) + reach * 0.06F * effort);
		head.yRot = Mth.lerp(amount, head.yRot, 0.0F);
		head.zRot = Mth.lerp(amount, head.zRot, -Mth.clamp(yaw, -1.2F, 1.2F));
	}

	private static void pose(ModelPart part, float xRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, 0.0F);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
