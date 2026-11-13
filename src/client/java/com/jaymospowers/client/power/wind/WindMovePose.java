package com.jaymospowers.client.power.wind;

import com.jaymospowers.power.wind.WindMoves;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;

/**
 * (Jung's wind revamp) The bodies of {@link WindMoves}, anyone's, from the synced {@link WindMoves#MOVE}:
 * <ul>
 * <li>gathering the Gust: both arms wheeling round in front, out of step, drawing the wind in;</li>
 * <li>the Gust's release: a one-armed push straight out, flowing into an uppercut with the other fist as the body turns
 * through;</li>
 * <li>the Gale Barrier: both arms flung out to the sides at shoulder height;</li>
 * <li>Zephyr's Breath: spinning round in the spiral, arms and legs loose and trailing;</li>
 * <li>Cyclone Lance: the lead arm drawn far back, the other aiming, body wound up; then the throw.</li>
 * <li>(Jung) the Spiral Dive: tipped right over, head first, arms together ahead like a spear's point and legs together
 * behind, the whole body spinning about its own length like a drill.</li>
 * </ul>
 * Turning the whole body (twists and the spin) is done in the PoseStack; the head is turned back against a twist so the
 * eyes stay on the target.
 */
final class WindMovePose {
	static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_move");

	/** The pose and how far into it (ticks, with the partial tick). */
	record Pose(int kind, float t) {
	}

	/** Easing in and out of a pose (ticks). */
	private static final float EASE = 3.0F;
	/** The push's and uppercut's body turn, and the lance's wind-up (degrees). */
	private static final float PUSH_TWIST = 28.0F, LANCE_TWIST = 35.0F;
	/** The spin: degrees a tick, and the forward tip into it. */
	private static final float SPIN_RATE = 40.0F, SPIN_TIP = 12.0F;
	/**
	 * The Spiral Dive: how far over the body tips (degrees; 180 is straight down, a little less keeps the head leading
	 * the way it steers), how fast it spins about its own length (degrees a tick: a turn and a third a second), and how
	 * long it takes to tip over (ticks).
	 */
	private static final float DIVE_TIP = 172.0F, DIVE_SPIN = 24.0F, DIVE_EASE = 6.0F;

	private WindMovePose() {
	}

	static Pose extract(Entity entity, float partialTicks) {
		WindMoves.Move move = entity.getAttached(WindMoves.MOVE);
		if (move == null) {
			return null;
		}
		float t = entity.level().getGameTime() - move.start() + partialTicks;
		return t < 0.0F ? null : new Pose(move.kind(), t);
	}

	/** How much of the pose shows: eased in at the start and, for a timed one, out at the end. */
	private static float amount(Pose p) {
		if (p.kind() == WindMoves.DIVE) {
			// tipped over once and held until the dive ends
			float over = Mth.clamp(p.t() / DIVE_EASE, 0.0F, 1.0F);
			return over * over * (3.0F - 2.0F * over);
		}
		float in = Mth.clamp(p.t() / EASE, 0.0F, 1.0F);
		float length = switch (p.kind()) {
			case WindMoves.PUSH -> WindMoves.PUSH_TICKS;
			case WindMoves.SPIN -> WindMoves.SPIN_TICKS;
			case WindMoves.LANCE -> WindMoves.LANCE_WINDUP + WindMoves.LANCE_THROW;
			case WindMoves.ARMS_OUT -> -1.0F;
			default -> -1.0F;
		};
		float out = length < 0.0F ? 1.0F : Mth.clamp((length - p.t()) / EASE, 0.0F, 1.0F);
		float a = Math.min(in, out);
		return a * a * (3.0F - 2.0F * a);
	}

	private static float smooth(float from, float to, float t) {
		float a = Mth.clamp((t - from) / Math.max(0.001F, to - from), 0.0F, 1.0F);
		return a * a * (3.0F - 2.0F * a);
	}

	/** How far the whole body is turned about its upright (degrees, + to the lead side). */
	private static float twist(Pose p) {
		float t = p.t();
		return switch (p.kind()) {
			// into the push, then back through the other way for the uppercut
			case WindMoves.PUSH -> PUSH_TWIST * (smooth(0.0F, WindMoves.PUSH_HIT, t) - 2.0F * smooth(WindMoves.PUSH_HIT, WindMoves.UPPERCUT_HIT, t));
			// wound back through the wind-up, unwound in the throw
			case WindMoves.LANCE -> -LANCE_TWIST * smooth(0.0F, WindMoves.LANCE_WINDUP * 0.6F, t)
				+ LANCE_TWIST * 1.4F * smooth(WindMoves.LANCE_WINDUP, WindMoves.LANCE_WINDUP + 3.0F, t);
			default -> 0.0F;
		};
	}

	static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		Pose p = state.getData(KEY);
		if (p == null || !(state instanceof AvatarRenderState avatar) || state.deathTime > 0.0F) {
			return;
		}
		float a = amount(p);
		float side = avatar.mainArm == HumanoidArm.LEFT ? -1.0F : 1.0F;
		if (p.kind() == WindMoves.SPIN) {
			float h = state.boundingBoxHeight * 0.5F;
			poseStack.rotateDegrees(Axis.YP, p.t() * SPIN_RATE * a);
			poseStack.translate(0.0F, h, 0.0F);
			poseStack.rotateDegrees(Axis.XP, -SPIN_TIP * a);
			poseStack.translate(0.0F, -h, 0.0F);
			return;
		}
		if (p.kind() == WindMoves.DIVE) {
			// about the body's middle: over, head first (a negative turn about X tips the top forward), then round its own length
			float h = state.boundingBoxHeight * 0.5F;
			poseStack.translate(0.0F, h, 0.0F);
			poseStack.rotateDegrees(Axis.XP, -DIVE_TIP * a);
			poseStack.rotateDegrees(Axis.YP, p.t() * DIVE_SPIN * a);
			poseStack.translate(0.0F, -h, 0.0F);
			return;
		}
		float twist = twist(p) * a;
		if (twist != 0.0F) {
			// (a turn about +Y swings the front toward the model's left: the lead (right) shoulder comes forward with a negative one)
			poseStack.rotateDegrees(Axis.YP, -twist * side);
		}
	}

	static void applyModel(HumanoidModel<?> h, AvatarRenderState state, Pose p) {
		float a = amount(p);
		boolean rightLead = state.mainArm != HumanoidArm.LEFT;
		ModelPart lead = rightLead ? h.rightArm : h.leftArm;
		ModelPart off = rightLead ? h.leftArm : h.rightArm;
		// (zRot + swings the right arm out; the left arm's mirror)
		float leadOut = rightLead ? 1.0F : -1.0F;
		float t = p.t();
		// the head stays on the target through a twist
		h.head.yRot -= twist(p) * a * Mth.DEG_TO_RAD * leadOut;
		switch (p.kind()) {
			case WindMoves.GATHER -> {
				// both arms wheeling round in front, half a turn apart
				float w = t * 0.4F;
				pose(lead, -1.25F + Mth.sin(w) * 0.7F, 0.0F, leadOut * (0.45F + Mth.cos(w) * 0.55F), a);
				pose(off, -1.25F + Mth.sin(w + Mth.PI) * 0.7F, 0.0F, -leadOut * (0.45F + Mth.cos(w + Mth.PI) * 0.55F), a);
				pose(h.rightLeg, -0.12F, 0.0F, 0.1F, a);
				pose(h.leftLeg, 0.12F, 0.0F, -0.1F, a);
			}
			case WindMoves.PUSH -> {
				float push = smooth(0.0F, WindMoves.PUSH_HIT, t) * (1.0F - smooth(WindMoves.PUSH_HIT + 1.0F, WindMoves.UPPERCUT_HIT + 2.0F, t));
				float cut = smooth(WindMoves.PUSH_HIT - 1.0F, WindMoves.UPPERCUT_HIT, t);
				// the push: palm straight out ahead
				pose(lead, Mth.lerp(push, 0.3F, -Mth.HALF_PI), -0.1F * leadOut, leadOut * 0.05F, a);
				// the uppercut: the other fist comes up from the hip to over the head
				pose(off, Mth.lerp(cut, 0.5F, -Mth.PI + 0.35F), 0.0F, -leadOut * Mth.lerp(cut, 0.2F, 0.05F), a);
				// stepped into it, then up onto the toes with the uppercut
				pose(h.rightLeg, rightLead ? -0.4F * push : 0.35F * push - 0.2F * cut, 0.0F, 0.06F, a);
				pose(h.leftLeg, rightLead ? 0.35F * push - 0.2F * cut : -0.4F * push, 0.0F, -0.06F, a);
			}
			case WindMoves.ARMS_OUT -> {
				// both arms out to the sides, level with the shoulders, palms out, shaking against the wind
				float shake = Mth.sin(state.ageInTicks * 2.1F) * 0.04F;
				pose(h.rightArm, shake, 0.0F, Mth.HALF_PI - 0.05F, a);
				pose(h.leftArm, -shake, 0.0F, -Mth.HALF_PI + 0.05F, a);
				pose(h.rightLeg, 0.0F, 0.0F, 0.12F, a);
				pose(h.leftLeg, 0.0F, 0.0F, -0.12F, a);
			}
			case WindMoves.SPIN -> {
				// limbs loose and trailing behind the spin, fluttering
				float flutter = Mth.sin(state.ageInTicks * 1.3F) * 0.12F;
				pose(h.rightArm, 0.35F + flutter, 0.5F, 1.15F, a);
				pose(h.leftArm, 0.35F - flutter, 0.5F, -1.15F, a);
				pose(h.rightLeg, 0.3F - flutter, 0.0F, 0.35F, a);
				pose(h.leftLeg, 0.15F + flutter, 0.0F, -0.35F, a);
				pose(h.head, 0.15F, 0.0F, 0.0F, a);
			}
			case WindMoves.DIVE -> {
				// a spear: both arms straight past the head, hands together, legs together and trailing, eyes on the ground
				float shiver = Mth.sin(state.ageInTicks * 1.7F) * 0.03F;
				// (an arm raised past the head swings the other way about Z: + brings the right one in)
				pose(h.rightArm, -Mth.PI + 0.08F, 0.0F, 0.22F + shiver, a);
				pose(h.leftArm, -Mth.PI + 0.08F, 0.0F, -0.22F - shiver, a);
				pose(h.rightLeg, 0.06F, 0.0F, 0.02F, a);
				pose(h.leftLeg, 0.06F, 0.0F, -0.02F, a);
				pose(h.head, -0.55F, 0.0F, 0.0F, a);
			}
			case WindMoves.LANCE -> {
				float thrown = smooth(WindMoves.LANCE_WINDUP, WindMoves.LANCE_WINDUP + 2.5F, t);
				float tremble = (1.0F - thrown) * Mth.sin(state.ageInTicks * 2.7F) * 0.05F;
				// drawn far back and up, then driven straight out
				pose(lead, Mth.lerp(thrown, 0.95F + tremble, -Mth.HALF_PI), 0.0F, leadOut * Mth.lerp(thrown, 0.55F, 0.0F), a);
				// the other arm aims, then drops back
				pose(off, Mth.lerp(thrown, -Mth.HALF_PI + 0.1F, 0.45F), -0.25F * leadOut * (1.0F - thrown), 0.0F, a);
				pose(h.rightLeg, rightLead ? 0.35F : -0.35F, 0.0F, 0.08F, a);
				pose(h.leftLeg, rightLead ? -0.35F : 0.35F, 0.0F, -0.08F, a);
			}
			default -> {
			}
		}
	}

	private static void pose(ModelPart part, float x, float y, float z, float a) {
		part.xRot = Mth.lerp(a, part.xRot, x);
		part.yRot = Mth.lerp(a, part.yRot, y);
		part.zRot = Mth.lerp(a, part.zRot, z);
	}
}
