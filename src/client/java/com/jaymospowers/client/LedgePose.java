package com.jaymospowers.client;

import com.jaymospowers.core.Ledge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * How a player on a ledge looks, for everyone: hanging, the body square to the wall with both arms up on the edge and
 * the legs dangling, swaying a little from the hands; climbing, the arms pull down and press on the top while the body
 * leans in over it, the right knee comes up onto the edge, then the left, and they stand.
 */
public final class LedgePose {
	/** Hanging: the sway from the hands (degrees, and radians a tick) and the legs' dangle (radians). */
	static final float SWAY_DEGREES = 3.0F, SWAY_SPEED = 0.07F, DANGLE = 0.14F;
	/** Climbing: how far the body leans in over the edge at the most (degrees). */
	static final float CLIMB_LEAN_DEGREES = 38.0F;
	/** Arms up on the edge (radians: straight up is -PI, a little forward toward the wall) and spread apart. */
	static final float ARM_UP = -2.85F, ARM_SPREAD = 0.2F;
	/** Arms pressing down on the top during the climb, and the knee brought up onto the edge (radians). */
	static final float ARM_PRESS = -0.45F, KNEE_UP = -1.5F;
	/** The hands' height over the feet while hanging (blocks): the sway turns about them. */
	static final float HANDS = 2.05F;

	/** {@code climb}: 0-1 through the climb, or -1 while hanging; {@code time}: ticks, for the sway. */
	public record State(float climb, float time) {
	}

	public static final RenderStateDataKey<State> KEY = RenderStateDataKey.create(() -> "jaymospowers:ledge");

	private LedgePose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Ledge.Hold hold = entity instanceof Player player ? Ledge.hold(player) : null;
		if (hold == null) {
			state.setData(KEY, null);
			return;
		}
		Player player = (Player)entity;
		float climb;
		if (player == Minecraft.getInstance().player) {
			// your own hold is the client's (the server's copy trails it, and outlasts a finished climb a little)
			if (!LedgeClient.active()) {
				state.setData(KEY, null);
				return;
			}
			climb = LedgeClient.localProgress(partialTicks);
		} else {
			climb = hold.climbing() ? Mth.clamp((player.level().getGameTime() - hold.start() + partialTicks) / Ledge.CLIMB_TICKS, 0.0F, 1.0F) : -1.0F;
		}
		if (climb >= 1.0F) {
			state.setData(KEY, null);
			return;
		}
		// square to the wall, the head free to look about within reason
		float head = state.bodyRot + state.yRot;
		state.bodyRot = hold.direction().toYRot();
		state.yRot = Mth.clamp(Mth.wrapDegrees(head - state.bodyRot), -70.0F, 70.0F);
		state.walkAnimationSpeed = 0.0F;
		state.setData(KEY, new State(climb, player.tickCount + partialTicks));
	}

	/** After the body faces its way: hanging, a slow sway from the hands; climbing, the lean in over the edge. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		State ledge = state.getData(KEY);
		if (ledge == null) {
			return;
		}
		if (ledge.climb() < 0.0F) {
			poseStack.translate(0.0F, HANDS, 0.0F);
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(ledge.time() * SWAY_SPEED) * SWAY_DEGREES);
			poseStack.rotateDegrees(Axis.XP, Mth.sin(ledge.time() * SWAY_SPEED * 0.7F + 1.3F) * SWAY_DEGREES * 0.4F);
			poseStack.translate(0.0F, -HANDS, 0.0F);
			return;
		}
		// (a negative turn about X tips the top forward) leaning in most as the knee comes up, upright again to stand
		float lean = Mth.sin(Mth.PI * Math.min(1.0F, ledge.climb() / 0.9F));
		poseStack.translate(0.0F, 0.9F, 0.0F);
		poseStack.rotateDegrees(Axis.XP, -CLIMB_LEAN_DEGREES * lean);
		poseStack.translate(0.0F, -0.9F, 0.0F);
	}

	/** Right after the model animates: the arms on the edge, the legs dangling or climbing. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		State ledge = state.getData(KEY);
		if (ledge == null) {
			return;
		}
		h.body.xRot = 0.0F;
		h.body.yRot = 0.0F;
		float p = ledge.climb();
		if (p < 0.0F) {
			float t = ledge.time();
			float give = Mth.sin(t * SWAY_SPEED * 1.3F) * 0.04F;
			set(h.rightArm, ARM_UP + give, 0.0F, ARM_SPREAD);
			set(h.leftArm, ARM_UP - give, 0.0F, -ARM_SPREAD);
			set(h.rightLeg, DANGLE * Mth.sin(t * 0.09F) + 0.05F, 0.0F, 0.05F);
			set(h.leftLeg, DANGLE * Mth.sin(t * 0.09F + 2.1F) - 0.03F, 0.0F, -0.05F);
			return;
		}
		// the arms: pull down from overhead, press on the top, then come down to the sides as you stand
		float pull = smooth(p / 0.55F);
		float stand = smooth((p - 0.8F) / 0.2F);
		float arm = Mth.lerp(stand, Mth.lerp(pull, ARM_UP, ARM_PRESS), 0.0F);
		float spread = Mth.lerp(stand, Mth.lerp(pull, ARM_SPREAD, ARM_SPREAD * 1.6F), 0.05F);
		set(h.rightArm, arm, 0.0F, spread);
		set(h.leftArm, arm, 0.0F, -spread);
		// the right knee up onto the edge, then the left after it, both down to stand
		float right = smooth((p - 0.35F) / 0.3F) * (1.0F - smooth((p - 0.75F) / 0.25F));
		float left = smooth((p - 0.55F) / 0.25F) * (1.0F - smooth((p - 0.82F) / 0.18F));
		set(h.rightLeg, KNEE_UP * right + 0.1F * (1.0F - pull), 0.0F, 0.04F);
		set(h.leftLeg, KNEE_UP * 0.6F * left - 0.1F * (1.0F - pull), 0.0F, -0.04F);
	}

	/**
	 * First person on a ledge: how one arm sits ({@code right}: the right arm), or false to leave it out. Hanging, both
	 * wrists reach up over the top of the view onto the edge, wide apart, taking the weight in turn; climbing
	 * ({@code climb} 0-1), they come down in front to press on the top, then drop out of sight as you stand.
	 */
	public static boolean firstPersonArm(PoseStack poseStack, float climb, float time, boolean right) {
		float side = right ? 1.0F : -1.0F;
		float hang = climb < 0.0F ? 1.0F : 0.0F;
		float press = climb < 0.0F ? 0.0F : smooth(climb / 0.55F);
		float gone = climb < 0.0F ? 0.0F : smooth((climb - 0.8F) / 0.2F);
		if (gone >= 1.0F) {
			return false;
		}
		// hanging, the hands shift their grip a little, one then the other
		float shift = hang * Mth.sin(time * SWAY_SPEED * 1.3F + (right ? 0.0F : Mth.PI)) * 0.02F;
		float x = side * Mth.lerp(press, 0.2F, 0.24F);
		float y = Mth.lerp(press, 0.48F, -0.02F) + shift - gone * 0.7F;
		float z = Mth.lerp(press, 0.12F, -0.12F);
		poseStack.translate(x, y, z);
		poseStack.rotateDegrees(Axis.XP, Mth.lerp(press, -62.0F, 12.0F));
		poseStack.rotateDegrees(Axis.ZP, side * Mth.lerp(press, 10.0F, 4.0F));
		return true;
	}

	private static float smooth(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}
}
