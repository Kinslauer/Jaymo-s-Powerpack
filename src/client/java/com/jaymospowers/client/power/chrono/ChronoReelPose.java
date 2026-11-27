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
 *
 * <p>Blitz strikes (Chrono Punch, the held flurry: {@code ChronoBlitz}) put a foe in one of six poses, held a moment
 * ({@link ChronoFlow#STRIKE_TICKS}): {@link #GUT} doubled over, {@link #SPIN} spun round, {@link #KNOCK} knocked back arms
 * wide, {@link #STAGGER} rolled to one side, {@link #LIFT} lifted off its feet, {@link #CRUMPLE} dropped into a crouch.
 */
public final class ChronoReelPose {
	/** The six blitz poses (the plain flinch is {@link #FLINCH}). */
	public static final int FLINCH = -1, GUT = 0, SPIN = 1, KNOCK = 2, STAGGER = 3, LIFT = 4, CRUMPLE = 5;

	/** The reel's progress (0-1), which way it's jolted, and which pose ({@link #FLINCH} or one of the six). */
	public record Flinch(float p, int side, int pose) {
	}

	public static final RenderStateDataKey<Flinch> KEY = RenderStateDataKey.create(() -> "jaymospowers:chrono_reel");

	/** How far the body tips back and twists (degrees), and how far the head snaps back and the arms fly (radians). */
	private static final float BODY_TIP = 14.0F, BODY_TWIST = 8.0F;
	private static final float HEAD_SNAP = 0.7F, HEAD_TILT = 0.3F, ARM_FLING = 0.7F, ARM_RAISE = 0.5F;

	private ChronoReelPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		ChronoFlow.Reel reel = entity.getAttached(ChronoFlow.REEL);
		if (reel == null || reel.kind() != ChronoFlow.FLINCH && reel.kind() != ChronoFlow.STRIKE) {
			state.setData(KEY, null);
			return;
		}
		boolean strike = reel.kind() == ChronoFlow.STRIKE;
		float p = (entity.level().getGameTime() - reel.start() + partialTicks) / (strike ? ChronoFlow.STRIKE_TICKS : ChronoFlow.FLINCH_TICKS);
		int pose = strike ? Math.floorMod(reel.pose(), ChronoFlow.STRIKE_POSES) : FLINCH;
		state.setData(KEY, p >= 0.0F && p < 1.0F ? new Flinch(p, reel.side(), pose) : null);
	}

	/** The jolt: snapped in at once, eased off. */
	private static float jolt(float p) {
		float e = p < 0.15F ? p / 0.15F : 1.0F - (p - 0.15F) / 0.85F;
		return e * e * (3.0F - 2.0F * e);
	}

	/** A blitz pose's weight: in fast, held a while, eased out. */
	private static float hold(float p) {
		float e = p < 0.15F ? p / 0.15F : p < 0.6F ? 1.0F : 1.0F - (p - 0.6F) / 0.4F;
		return e * e * (3.0F - 2.0F * e);
	}

	private static float weight(Flinch flinch) {
		return flinch.pose() == FLINCH ? jolt(flinch.p()) : hold(flinch.p());
	}

	/** After the body has been turned to face its way: tipped back and twisted, about its middle (or its pose's own turn). */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Flinch flinch = state.getData(KEY);
		if (flinch == null || state.deathTime > 0.0F) {
			return;
		}
		float e = weight(flinch);
		float height = state.boundingBoxHeight / Math.max(entityScale, 0.01F);
		float middle = height * 0.5F;
		int side = flinch.side();
		// the body's turn about its middle: X tips the top back (positive) or forward, Y spins it, Z rolls it to a side
		float tip, spin, roll, lift = 0.0F;
		switch (flinch.pose()) {
			case GUT -> {
				tip = -36.0F;
				spin = 0.0F;
				roll = 0.0F;
			}
			case SPIN -> {
				tip = 6.0F;
				spin = side * 80.0F;
				roll = 0.0F;
			}
			case KNOCK -> {
				tip = 32.0F;
				spin = 0.0F;
				roll = 0.0F;
			}
			case STAGGER -> {
				tip = 6.0F;
				spin = side * 10.0F;
				roll = side * 26.0F;
			}
			case LIFT -> {
				tip = 22.0F;
				spin = 0.0F;
				roll = 0.0F;
				lift = 0.28F;
			}
			case CRUMPLE -> {
				tip = -22.0F;
				spin = 0.0F;
				roll = side * 8.0F;
				lift = -0.16F;
			}
			default -> {
				tip = BODY_TIP;
				spin = side * BODY_TWIST;
				roll = 0.0F;
			}
		}
		poseStack.translate(0.0F, lift * e * height * 0.55F, 0.0F);
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotateDegrees(Axis.XP, tip * e);
		poseStack.rotateDegrees(Axis.YP, spin * e);
		poseStack.rotateDegrees(Axis.ZP, roll * e);
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the model animates: the head and arms take the pose's shape. */
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
		ModelPart arms = part(root, "arms");
		if (head == null || right == null && left == null && arms == null) {
			return; // not humanoid enough to flinch like one
		}
		float e = weight(flinch);
		int side = flinch.side();
		// the head's nod (negative snaps it back), its tilt, then each arm's lift (negative raises it forward/up) and its swing out
		float nod, tilt, lift, out;
		switch (flinch.pose()) {
			case GUT -> {
				nod = 0.7F;
				tilt = 0.0F;
				lift = -0.9F;
				out = -0.25F;
			}
			case SPIN -> {
				nod = -0.2F;
				tilt = side * 0.25F;
				lift = -1.6F;
				out = 1.2F;
			}
			case KNOCK -> {
				nod = -0.8F;
				tilt = 0.0F;
				lift = -0.3F;
				out = 1.4F;
			}
			case STAGGER -> {
				nod = 0.0F;
				tilt = side * 0.55F;
				lift = -0.5F;
				out = 0.6F;
			}
			case LIFT -> {
				nod = -0.6F;
				tilt = 0.0F;
				lift = -2.7F;
				out = 0.3F;
			}
			case CRUMPLE -> {
				nod = 0.9F;
				tilt = side * 0.2F;
				lift = 0.5F;
				out = 0.2F;
			}
			default -> {
				nod = -HEAD_SNAP;
				tilt = side * HEAD_TILT;
				lift = -ARM_RAISE;
				out = ARM_FLING;
			}
		}
		head.xRot += nod * e;
		head.zRot += tilt * e;
		if (right != null) {
			right.xRot += lift * e;
			right.zRot += out * e;
		}
		if (left != null) {
			left.xRot += lift * e;
			left.zRot -= out * e;
		}
		if (arms != null) {
			arms.xRot += lift * e;
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
