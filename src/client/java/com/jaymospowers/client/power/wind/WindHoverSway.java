package com.jaymospowers.client.power.wind;

import com.jaymospowers.power.wind.WindFlight;
import com.jaymospowers.power.wind.WindPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Hovering isn't walking: a hovering player hangs in the air like a ragdoll (the same spring-and-damper approach as
 * Jaymo's Kitchen's corpses). The body swings from the shoulders opposite to the way it's moving, as a pendulum: fly
 * forward and the legs trail back, stop and it swings through and settles. Arms and legs hang limp and trail with
 * it, and the walk cycle is switched off. Works for every player in view (others' state comes from the synced
 * flight attachment; their motion from their position each tick).
 */
public final class WindHoverSway {
	public static final RenderStateDataKey<Sway> KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_hover_sway");

	/** Lean (radians) per block/tick of movement, and the most it leans either way. */
	private static final float LEAN_PER_SPEED = 1.1F;
	private static final float LEAN_MAX = 0.45F;
	/** Climbing or sinking swings the legs a little too. */
	private static final float LEAN_PER_RISE = 0.5F;
	/** The pendulum: pull back toward the target per tick, and velocity kept per tick (1 = swings forever). */
	private static final float SPRING = 0.16F;
	private static final float DAMPING = 0.86F;
	/** How fast the hover pose eases in and out, per tick. */
	private static final float EASE = 0.2F;
	/** The body hangs from about the shoulders (fraction of its height). */
	private static final float PIVOT = 0.85F;

	/** This frame's sway: {@code pitch} > 0 swings the legs back, {@code roll} > 0 swings them left. */
	public record Sway(float amount, float pitch, float roll) {
	}

	private static final class Body {
		float pitch, pitchVel, prevPitch;
		float roll, rollVel, prevRoll;
		float amount, prevAmount;
	}

	private static final Map<Integer, Body> BODIES = new HashMap<>();

	private WindHoverSway() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			BODIES.clear();
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			boolean hovering = isHovering(minecraft, player);
			Body body = BODIES.get(player.getId());
			if (body == null) {
				if (!hovering) {
					continue;
				}
				body = new Body();
				BODIES.put(player.getId(), body);
			}
			body.prevAmount = body.amount;
			body.amount = hovering ? Math.min(1.0F, body.amount + EASE) : Math.max(0.0F, body.amount - EASE);
			// this tick's motion, in the body's own frame (positions work for remote players too)
			double vx = player.getX() - player.xo;
			double vy = player.getY() - player.yo;
			double vz = player.getZ() - player.zo;
			float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
			double forward = -Mth.sin(yaw) * vx + Mth.cos(yaw) * vz;
			double right = -Mth.cos(yaw) * vx - Mth.sin(yaw) * vz;
			float pitchWant = hovering ? Mth.clamp((float)(forward * LEAN_PER_SPEED - vy * LEAN_PER_RISE), -LEAN_MAX, LEAN_MAX) : 0.0F;
			float rollWant = hovering ? Mth.clamp((float)(right * LEAN_PER_SPEED), -LEAN_MAX, LEAN_MAX) : 0.0F;
			body.prevPitch = body.pitch;
			body.pitchVel = (body.pitchVel + (pitchWant - body.pitch) * SPRING) * DAMPING;
			body.pitch += body.pitchVel;
			body.prevRoll = body.roll;
			body.rollVel = (body.rollVel + (rollWant - body.roll) * SPRING) * DAMPING;
			body.roll += body.rollVel;
		}
		BODIES.entrySet().removeIf(entry -> entry.getValue().amount <= 0.0F && entry.getValue().prevAmount <= 0.0F
			|| level.getEntity(entry.getKey()) == null);
	}

	private static boolean isHovering(Minecraft minecraft, Player player) {
		if (player == minecraft.player) {
			return WindPowerClient.isActive(minecraft.player) && WindPowerClient.INSTANCE.flight().state() == WindFlight.HOVER;
		}
		Integer state = player.getAttached(WindPower.FLIGHT);
		return state != null && WindFlight.byId(state) == WindFlight.HOVER;
	}

	/** From the avatar renderer's extractRenderState: this frame's sway, and no walk cycle while hovering. */
	static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		Body body = BODIES.get(entity.getId());
		float amount = body == null ? 0.0F : Mth.lerp(partialTicks, body.prevAmount, body.amount);
		if (amount <= 0.001F || state.isFallFlying || state.isVisuallySwimming) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Sway(amount, Mth.lerp(partialTicks, body.prevPitch, body.pitch), Mth.lerp(partialTicks, body.prevRoll, body.roll)));
		// the legs shouldn't step through the air
		state.walkAnimationSpeed *= 1.0F - amount;
		if (amount > 0.5F) {
			// Sneak sinks; it shouldn't hunch
			state.isCrouching = false;
		}
	}

	/** After the body has been turned to face its way: swing it from the shoulders. */
	static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Sway sway = state.getData(KEY);
		if (sway == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float pivot = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * PIVOT;
		poseStack.translate(0.0F, pivot, 0.0F);
		// local +Z is the body's back and +X its right: a negative turn about X sends the feet back, about Z left
		poseStack.rotateDegrees(Axis.XP, -sway.pitch() * sway.amount() * Mth.RAD_TO_DEG);
		poseStack.rotateDegrees(Axis.ZP, -sway.roll() * sway.amount() * Mth.RAD_TO_DEG);
		poseStack.translate(0.0F, -pivot, 0.0F);
	}

	/** After the model animates: limp limbs that trail the swing. */
	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		Sway sway = avatar.getData(KEY);
		if (sway == null) {
			return;
		}
		float amount = sway.amount();
		float drift = Mth.sin(avatar.ageInTicks * 0.12F) * 0.05F;
		float trail = sway.pitch();
		// arms hang loose a little out from the sides, trailing the swing
		set(humanoid.rightArm, trail * 0.7F + drift, 0.0F, 0.12F + sway.roll() * 0.3F, amount);
		set(humanoid.leftArm, trail * 0.7F - drift, 0.0F, -0.12F + sway.roll() * 0.3F, amount);
		// legs dangle together, toes down, trailing a bit more than the body
		set(humanoid.rightLeg, trail * 0.5F + 0.08F + drift * 0.6F, 0.0F, 0.03F, amount);
		set(humanoid.leftLeg, trail * 0.4F - 0.02F - drift * 0.6F, 0.0F, -0.03F, amount);
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
