package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoFlow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

/**
 * A creature hit by a Time Flow blow that holds it (a stunning blow, a barrage punch) reels: the whole body jolts back
 * and twists to one side, and on anything with a head and arms the head snaps back and the arms fly up, then it all
 * eases back. (A thrown creature ragdolls instead: see {@code WebRagdoll}.) Set from the blow's progress, so applying it
 * twice changes nothing.
 */
public final class ChronoReelPose {
	/** The flinch's progress (0-1) and which way it's jolted. */
	public record Flinch(float p, int side) {
	}

	public static final RenderStateDataKey<Flinch> KEY = RenderStateDataKey.create(() -> "jaymospowers:chrono_reel");

	/** How far the body tips back and twists (degrees), and how far the head snaps back and the arms fly (radians). */
	private static final float BODY_TIP = 14.0F, BODY_TWIST = 8.0F;
	private static final float HEAD_SNAP = 0.7F, HEAD_TILT = 0.3F, ARM_FLING = 0.7F, ARM_RAISE = 0.5F;

	private ChronoReelPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		ChronoFlow.Reel reel = entity.getAttached(ChronoFlow.REEL);
		if (reel == null || reel.kind() != ChronoFlow.FLINCH) {
			state.setData(KEY, null);
			return;
		}
		float p = (entity.level().getGameTime() - reel.start() + partialTicks) / ChronoFlow.FLINCH_TICKS;
		state.setData(KEY, p >= 0.0F && p < 1.0F ? new Flinch(p, reel.side()) : null);
	}

	/** The jolt: snapped in at once, eased off. */
	private static float jolt(float p) {
		float e = p < 0.15F ? p / 0.15F : 1.0F - (p - 0.15F) / 0.85F;
		return e * e * (3.0F - 2.0F * e);
	}

	/** After the body has been turned to face its way: tipped back and twisted, about its middle. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Flinch flinch = state.getData(KEY);
		if (flinch == null || state.deathTime > 0.0F) {
			return;
		}
		float e = jolt(flinch.p());
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		poseStack.translate(0.0F, middle, 0.0F);
		// a positive turn about X tips the top back (away from the blow)
		poseStack.rotateDegrees(Axis.XP, BODY_TIP * e);
		poseStack.rotateDegrees(Axis.YP, flinch.side() * BODY_TWIST * e);
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the model animates: the head snaps back and tilts, the arms fly up and out. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living)) {
			return;
		}
		Flinch flinch = living.getData(KEY);
		if (flinch == null) {
			return;
		}
		ModelPart root = model.root();
		ModelPart head = part(root, "head");
		ModelPart right = part(root, "right_arm");
		ModelPart left = part(root, "left_arm");
		if (head == null || right == null && left == null && part(root, "arms") == null) {
			return; // not humanoid enough to flinch like one
		}
		float e = jolt(flinch.p());
		head.xRot -= HEAD_SNAP * e;
		head.zRot += flinch.side() * HEAD_TILT * e;
		if (right != null) {
			right.xRot -= ARM_RAISE * e;
			right.zRot += ARM_FLING * e;
		}
		if (left != null) {
			left.xRot -= ARM_RAISE * e;
			left.zRot -= ARM_FLING * e;
		}
		ModelPart arms = part(root, "arms");
		if (arms != null) {
			arms.xRot -= ARM_RAISE * e;
		}
	}

	/** A part on the root, or on its body. */
	private static ModelPart part(ModelPart root, String name) {
		if (root.hasChild(name)) {
			return root.getChild(name);
		}
		if (root.hasChild("body")) {
			ModelPart body = root.getChild("body");
			if (body.hasChild(name)) {
				return body.getChild(name);
			}
		}
		return null;
	}
}
