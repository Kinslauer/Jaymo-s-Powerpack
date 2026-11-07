package com.jaymospowers.client.freerun.moves;

import com.jaymospowers.freerun.moves.LongJump;
import com.jaymospowers.freerun.moves.RunMoves;
import com.jaymospowers.freerun.moves.SkyDive;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The running and falling moves ({@link RunMoves}) as everyone sees them, from the synced {@link RunMoves#POSE} and
 * {@link SkyDive#ACTIVE}:
 * <ul>
 * <li>Long jump: the arms swing up and over, the legs reach out ahead together, the body leaning into the leap.</li>
 * <li>Safety tap: a deep crouch on landing, knees taking it, arms out in front for balance.</li>
 * <li>Sky dive: laid out flat, belly to the ground, arms and legs spread wide, head up to see where it's going.</li>
 * </ul>
 * Laid over the model after the fall's own flailing ({@code FreefallPose}) and before any power's pose, which wins.
 */
public final class RunMovesPose {
	// ===== the knobs ================================================================================================
	/** How long the safety tap's crouch shows (ticks). */
	static final int SAFETY_TICKS = 10;
	/** The safety tap: how far the body drops (blocks) and tips forward (degrees). */
	static final float SAFETY_DROP = 0.3F, SAFETY_LEAN = 25.0F;
	/** The long jump's lean into the leap (degrees). */
	static final float LONG_LEAN = 15.0F;
	/** The sky dive's tilt (degrees: 90 is flat) and how fast it eases in (share a tick). */
	static final float SKY_TILT = 82.0F, SKY_EASE = 0.15F;
	// ================================================================================================================

	public static final int SKY = 100;

	/** {@code kind}: {@link RunMoves#LONG_JUMP}, {@link RunMoves#SAFETY_TAP} or {@link #SKY}; {@code e}: how far in (0-1). */
	public record Move(int kind, float age, float time, float e) {
	}

	public static final RenderStateDataKey<Move> KEY = RenderStateDataKey.create(() -> "jaymospowers:run_move");

	private RunMovesPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Move move = entity instanceof Player player ? move(player, partialTicks) : null;
		state.setData(KEY, move);
		if (move != null && move.kind() == SKY) {
			// spread out, not flailing (nor Leap of Faith's head-first dive)
			state.setData(com.jaymospowers.client.FreefallPose.KEY, null);
		}
	}

	private static @Nullable Move move(Player player, float partialTicks) {
		float time = player.tickCount + partialTicks;
		if (SkyDive.active(player)) {
			return new Move(SKY, 0.0F, time, 1.0F);
		}
		long[] pose = RunMoves.pose(player);
		if (pose == null) {
			return null;
		}
		float age = (float)(player.level().getGameTime() - pose[1]) + partialTicks;
		int kind = (int)pose[0];
		int length = kind == RunMoves.LONG_JUMP ? LongJump.POSE_TICKS : kind == RunMoves.SAFETY_TAP ? SAFETY_TICKS : 0;
		if (age < 0.0F || age >= length) {
			return null;
		}
		// in over two ticks, out over the last three
		float e = Math.min(1.0F, Math.min(age / 2.0F, (length - age) / 3.0F));
		return new Move(kind, age, time, e * e * (3.0F - 2.0F * e));
	}

	static @Nullable Move move(LivingEntityRenderState state) {
		return state.deathTime > 0.0F ? null : state.getData(KEY);
	}

	/** After the body faces its way: the lean, the crouch's drop, the dive laid flat. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Move m = move(state);
		if (m == null) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		switch (m.kind()) {
			case SKY -> {
				poseStack.translate(0.0F, middle, 0.0F);
				// a negative turn about X tips the top forward
				poseStack.rotateDegrees(Axis.XP, -SKY_TILT + Mth.sin(m.time() * 0.2F) * 3.0F);
				poseStack.rotateDegrees(Axis.ZP, Mth.sin(m.time() * 0.13F) * 4.0F);
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			case RunMoves.SAFETY_TAP -> {
				poseStack.translate(0.0F, -SAFETY_DROP * m.e(), 0.0F);
				poseStack.rotateDegrees(Axis.XP, -SAFETY_LEAN * m.e());
			}
			case RunMoves.LONG_JUMP -> {
				poseStack.translate(0.0F, middle, 0.0F);
				poseStack.rotateDegrees(Axis.XP, -LONG_LEAN * m.e());
				poseStack.translate(0.0F, -middle, 0.0F);
			}
			default -> {
			}
		}
	}

	/** After the model animates: the limbs of each move. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Move m = move(living);
		if (m == null) {
			return;
		}
		float e = m.e(), t = m.time();
		switch (m.kind()) {
			case SKY -> {
				// spread wide, the arms a little forward of the shoulders, fluttering in the wind
				float flutter = Mth.sin(t * 0.9F) * 0.06F;
				set(h.rightArm, -0.35F + flutter, 0.0F, 1.45F, e);
				set(h.leftArm, -0.35F - flutter, 0.0F, -1.45F, e);
				set(h.rightLeg, 0.3F, 0.0F, 0.4F + flutter, e);
				set(h.leftLeg, 0.3F, 0.0F, -0.4F - flutter, e);
				// laid flat: looking up from the body is looking ahead
				h.head.xRot = Mth.lerp(e, h.head.xRot, -1.2F);
			}
			case RunMoves.SAFETY_TAP -> {
				set(h.rightArm, -1.0F, 0.0F, 0.3F, e);
				set(h.leftArm, -1.0F, 0.0F, -0.3F, e);
				set(h.rightLeg, -1.1F, 0.0F, 0.15F, e);
				set(h.leftLeg, -1.1F, 0.0F, -0.15F, e);
				h.head.xRot = Mth.lerp(e, h.head.xRot, -0.2F);
			}
			case RunMoves.LONG_JUMP -> {
				// the arms wheel up and over and back down; the legs come up together to reach for the far side
				float share = Mth.clamp(m.age() / LongJump.POSE_TICKS, 0.0F, 1.0F);
				float arm = -3.0F + share * 3.6F;
				set(h.rightArm, arm, 0.0F, 0.25F, e);
				set(h.leftArm, arm, 0.0F, -0.25F, e);
				float legs = -0.2F - Mth.sin(share * Mth.PI) * 1.1F;
				set(h.rightLeg, legs, 0.0F, 0.05F, e);
				set(h.leftLeg, legs, 0.0F, -0.05F, e);
			}
			default -> {
				return;
			}
		}
		h.hat.xRot = h.head.xRot;
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
