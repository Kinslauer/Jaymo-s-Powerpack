package com.jaymospowers.client;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Falling a long way (anyone, for everyone to see): past the speed of about five blocks' drop, the arms fly up and
 * out, flailing, the legs kick, and the head looks down at what's coming. A wind user doesn't flail: they dive,
 * head first and sure of it, arms swept back, legs together, until they call the wind (their flight) and pull out. It eases in with the speed. Worked out from
 * the actual motion, so it shows on every player in view, not only your own; anything else that has the body (a glide,
 * a swing, a ragdoll, an elytra, a power's own pose) is applied after and wins.
 */
public final class FreefallPose {
	/** Falling at least this fast (blocks/tick: about five blocks' drop) it starts; by {@code FULL} it's all limbs. */
	static final float START = 0.8F, FULL = 1.3F;

	/** {@code tip}: how far over a wind's dive has gone (0-1), growing slowly with the speed of the fall. */
	public record Fall(float strength, float time, boolean dive, float tip) {
	}

	/**
	 * A wind's dive tips over gradually as the fall speeds up: from {@code DIVE_START} degrees as it begins to
	 * {@code DIVE_PITCH} (head pointing down at the ground, not lying flat) by a fall of {@code DIVE_FULL} blocks/tick.
	 */
	static final float DIVE_START = 20.0F, DIVE_PITCH = 150.0F, DIVE_FULL = 2.6F;

	public static final RenderStateDataKey<Fall> KEY = RenderStateDataKey.create(() -> "jaymospowers:freefall");

	private FreefallPose() {
	}

	/** From the living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || player.isFallFlying() || player.isSwimming() || player.isPassenger()
			|| player.getAbilities().flying || player.isInWater() || player.onClimbable() || controlled(player)) {
			state.setData(KEY, null);
			return;
		}
		float falling = (float)(player.yo - player.getY());
		float strength = Mth.clamp((falling - START) / (FULL - START), 0.0F, 1.0F);
		// (0.14.3) and a freerunner with Leap of Faith, falling looking down
		boolean dive = com.jaymospowers.core.Powers.has(player, com.jaymospowers.power.wind.WindPower.INSTANCE)
			|| com.jaymospowers.freerun.Freerun.dives(player);
		float tip = Mth.clamp((falling - START) / (DIVE_FULL - START), 0.0F, 1.0F);
		state.setData(KEY, strength <= 0.0F ? null : new Fall(strength, player.tickCount + partialTicks, dive, tip * tip * (3.0F - 2.0F * tip)));
	}

	/** Falling on purpose: a wind's flight, web wings or lines, a rock slab, Weightless, a forward fall, a wall walk. */
	private static boolean controlled(Player player) {
		return player.getAttached(com.jaymospowers.power.wind.WindPower.FLIGHT) != null
			|| player.getAttached(com.jaymospowers.power.web.WebPower.WINGS) != null
			|| player.getAttached(com.jaymospowers.power.web.WebPower.LINE) != null
			|| player.getAttached(com.jaymospowers.power.web.WebPower.LINE_OFF) != null
			|| player.getAttached(com.jaymospowers.power.gravity.GravityPower.ROCK_FLIGHT) != null
			|| player.getAttached(com.jaymospowers.power.gravity.GravityPower.WEIGHTLESS_ON) != null
			|| player.getAttached(com.jaymospowers.power.gravity.GravityForward.FALLING) != null
			|| player.getAttached(com.jaymospowers.power.gravity.GravityWalk.SURFACE) != null
			|| player.getAttached(com.jaymospowers.power.chrono.ChronoSpeed.WALL_RUN) != null;
	}

	/** After the body faces its way: a wind's dive tips the whole body over, head first, about its middle. */
	public static void rotate(LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, float entityScale) {
		Fall fall = state.getData(KEY);
		if (fall == null || !fall.dive()) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		poseStack.translate(0.0F, middle, 0.0F);
		// (a negative turn about X tips the top forward)
		poseStack.rotateDegrees(com.mojang.math.Axis.XP, -Mth.lerp(fall.tip(), DIVE_START * fall.strength(), DIVE_PITCH));
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the model animates (and before any power's pose, which may take the body over). */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Fall fall = living.getData(KEY);
		if (fall == null) {
			return;
		}
		float s = fall.strength();
		float t = fall.time();
		if (fall.dive()) {
			// streamlined: arms swept back along the sides, legs together, chin up to see where it's going
			set(h.rightArm, 0.35F, 0.0F, 0.25F, s);
			set(h.leftArm, 0.35F, 0.0F, -0.25F, s);
			set(h.rightLeg, 0.05F + Mth.sin(t * 0.3F) * 0.04F, 0.0F, 0.03F, s);
			set(h.leftLeg, 0.05F - Mth.sin(t * 0.3F) * 0.04F, 0.0F, -0.03F, s);
			h.head.xRot = Mth.lerp(s, h.head.xRot, -0.9F);
			return;
		}
		// arms thrown up and out, windmilling
		set(h.rightArm, -2.6F + Mth.sin(t * 0.9F) * 0.5F, 0.0F, 0.9F + Mth.cos(t * 0.7F) * 0.35F, s);
		set(h.leftArm, -2.6F + Mth.sin(t * 0.9F + 2.0F) * 0.5F, 0.0F, -0.9F - Mth.cos(t * 0.7F + 1.0F) * 0.35F, s);
		// legs kicking apart
		set(h.rightLeg, Mth.sin(t * 1.1F) * 0.7F, 0.0F, 0.25F, s);
		set(h.leftLeg, -Mth.sin(t * 1.1F) * 0.7F, 0.0F, -0.25F, s);
		// looking down at the ground rushing up
		h.head.xRot = Mth.lerp(s, h.head.xRot, 0.7F);
		h.hat.xRot = h.head.xRot;
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
