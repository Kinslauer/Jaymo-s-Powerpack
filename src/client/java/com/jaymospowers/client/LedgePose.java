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
	/**
	 * (0.13.25) First person, placed straight in the view (vanilla's own arm placement undone): the shoulder's
	 * {x, y, z} (blocks, x toward the arm's side) and how far the arm tips away from you and out to its side (degrees),
	 * hanging and at the end of the press. Hanging, the arms rise from below the view to the hands on the edge at its
	 * top; climbing, the edge sinks through the view and the hands with it, gone under it by {@link #FP_GONE_AT}.
	 */
	static final float[] FP_HANG = {-0.05F, -0.55F, -0.45F, 12.0F, 10.0F}, FP_PRESS = {-0.05F, -0.95F, -0.5F, 35.0F, 8.0F};
	static final float FP_PRESS_END = 0.55F, FP_GONE_FROM = 0.5F, FP_GONE_AT = 1.0F, FP_GONE_DROP = 0.6F;

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
	 * hands reach up to the edge at the top of the view, taking the weight in turn; climbing ({@code climb} 0-1), the
	 * edge sinks through the view and the hands go down with it, out of sight before you stand. {@code equip} is the
	 * arm height vanilla's renderPlayerArm is then called with: its placement is undone here, so the arm sits exactly
	 * where this puts it.
	 */
	public static boolean firstPersonArm(PoseStack poseStack, float climb, float time, boolean right, float equip) {
		float side = right ? 1.0F : -1.0F;
		float hang = climb < 0.0F ? 1.0F : 0.0F;
		float press = climb < 0.0F ? 0.0F : smooth(climb / FP_PRESS_END);
		float gone = climb < 0.0F ? 0.0F : smooth((climb - FP_GONE_FROM) / (FP_GONE_AT - FP_GONE_FROM));
		if (gone >= 1.0F) {
			return false;
		}
		// hanging, the hands shift their grip a little, one then the other
		float shift = hang * Mth.sin(time * SWAY_SPEED * 1.3F + (right ? 0.0F : Mth.PI)) * 0.02F;
		poseStack.translate(side * Mth.lerp(press, FP_HANG[0], FP_PRESS[0]), Mth.lerp(press, FP_HANG[1], FP_PRESS[1]) + shift - gone * FP_GONE_DROP,
			Mth.lerp(press, FP_HANG[2], FP_PRESS[2]));
		poseStack.rotateDegrees(Axis.XP, -Mth.lerp(press, FP_HANG[3], FP_PRESS[3]));
		// (model space faces the other way: the arm's own side is -x there)
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.rotateDegrees(Axis.ZP, -side * Mth.lerp(press, FP_HANG[4], FP_PRESS[4]));
		undoVanillaArm(poseStack, side, equip);
		return true;
	}

	/** The inverse of vanilla's first-person arm placement (FirstPersonHandsAndItemsRenderer#renderPlayerArm, no swing). */
	private static void undoVanillaArm(PoseStack poseStack, float side, float equip) {
		poseStack.translate(-side * 5.6F, 0.0F, 0.0F);
		poseStack.rotateDegrees(Axis.YP, side * 135.0F);
		poseStack.rotateDegrees(Axis.XP, -200.0F);
		poseStack.rotateDegrees(Axis.ZP, -side * 120.0F);
		poseStack.translate(side * 1.0F, -3.6F, -3.5F);
		poseStack.rotateDegrees(Axis.YP, -side * 45.0F);
		poseStack.translate(-side * 0.64000005F, 0.6F + equip * 0.6F, 0.71999997F);
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
