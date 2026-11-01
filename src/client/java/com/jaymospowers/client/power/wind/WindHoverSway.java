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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Hovering isn't walking: a hovering player hangs in the air like a ragdoll held up by the wind (the same
 * spring-and-damper approach as Jaymo's Kitchen's corpses). Every part is a damped spring chasing a target, so nothing
 * loops: the body swings from the shoulders against the way it's moving and settles after one soft overshoot; it
 * lags behind the camera, twisting after a turn and leaning into it, and tips a little with where you look; climbing
 * and sinking make it sag and float; and slow, wandering gusts keep it from ever hanging dead still. The arms and
 * legs follow the body on softer springs of their own, so they trail it and settle a beat later. The walk cycle is
 * switched off. In first person the same body rocks the view by a fraction of a degree and bobs it a little.
 * Works for every player in view (others' state comes from the synced flight attachment; their motion and look from
 * their position and rotation each tick).
 */
public final class WindHoverSway {
	public static final RenderStateDataKey<Sway> KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_hover_sway");

	/** Lean (radians) per block/tick of movement, and the most it leans either way. */
	private static final float LEAN_PER_SPEED = 0.7F;
	private static final float LEAN_MAX = 0.3F;
	/** Climbing or sinking swings the legs a little too. */
	private static final float LEAN_PER_RISE = 0.3F;
	/** Share of the look's pitch the body tips with (look down, lean forward). */
	private static final float LOOK_PITCH = 0.12F;
	/** Leaning into a camera turn: radians per degree a tick the view swings, and the most. */
	private static final float TURN_LEAN = 0.01F;
	private static final float TURN_LEAN_MAX = 0.18F;
	/** The hips twisting after a camera turn: degrees per degree a tick, and the most. */
	private static final float TURN_TWIST = 0.6F;
	private static final float TURN_TWIST_MAX = 12.0F;
	/** Sag (blocks) per block/tick of climb, and the most either way. */
	private static final float SAG_PER_RISE = 0.5F;
	private static final float SAG_MAX = 0.1F;
	/** The body's springs: pull toward the target per tick, and velocity kept per tick (one soft overshoot, ~1 s). */
	private static final float SPRING = 0.12F;
	private static final float DAMPING = 0.8F;
	/** The arms' and legs' springs, softer, so they trail the body. */
	private static final float LIMB_SPRING = 0.08F;
	private static final float LIMB_DAMPING = 0.82F;
	/** Gusts: how fast they wander (per tick), and how far they push the body (radians) and the bob (blocks). */
	private static final float GUST_WANDER = 0.04F;
	private static final float GUST_SWAY = 0.05F;
	private static final float GUST_BOB = 0.03F;
	private static final float GUST_LIMB = 0.06F;
	/** First person: degrees of view per radian of body sway, and share of the bob the eye gets. */
	private static final float VIEW_PITCH = 3.0F;
	private static final float VIEW_YAW = 0.12F;
	private static final float VIEW_BOB = 0.5F;
	/** How fast the hover pose eases in and out, per tick. */
	private static final float EASE = 0.2F;
	/** The body hangs from about the shoulders (fraction of its height). */
	private static final float PIVOT = 0.85F;

	/**
	 * This frame's sway: {@code pitch} > 0 swings the legs back, {@code roll} > 0 swings them left, {@code twist}
	 * (degrees) > 0 turns the body back against a right turn, {@code bob} (blocks) lifts it; the limbs' own lagging
	 * swings, and each limb's drift (radians: right arm, left arm, right leg, left leg).
	 */
	public record Sway(float amount, float pitch, float roll, float twist, float bob, float armPitch, float legPitch,
		float limbRoll, float[] drift) {
	}

	/** A damped spring, one per tick. */
	private static final class Spring {
		float value, velocity, prev;

		void step(float target, float stiffness, float damping) {
			this.prev = this.value;
			this.velocity = (this.velocity + (target - this.value) * stiffness) * damping;
			this.value += this.velocity;
		}

		float at(float partialTicks) {
			return Mth.lerp(partialTicks, this.prev, this.value);
		}
	}

	private static final class Body {
		final Spring pitch = new Spring(), roll = new Spring(), twist = new Spring(), bob = new Spring();
		final Spring armPitch = new Spring(), legPitch = new Spring(), limbRoll = new Spring();
		final Spring[] drift = {new Spring(), new Spring(), new Spring(), new Spring()};
		/** Slow wandering noise: pitch, roll, bob, then one per limb. */
		final float[] gust = new float[7];
		final RandomSource random;
		float amount, prevAmount;
		float lastYaw;

		Body(Player player) {
			this.random = RandomSource.create(player.getId() * 0x9E3779B97F4A7C15L);
			this.lastYaw = player.getYHeadRot();
		}
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
				body = new Body(player);
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
			// how far the camera swung this tick (degrees; > 0 is a turn to the right)
			float turn = Mth.wrapDegrees(player.getYHeadRot() - body.lastYaw);
			body.lastYaw = player.getYHeadRot();
			for (int i = 0; i < body.gust.length; i++) {
				body.gust[i] += ((body.random.nextFloat() * 2.0F - 1.0F) - body.gust[i]) * GUST_WANDER;
			}
			float on = hovering ? 1.0F : 0.0F;
			float pitchWant = Mth.clamp((float)(forward * LEAN_PER_SPEED - vy * LEAN_PER_RISE), -LEAN_MAX, LEAN_MAX)
				+ player.getXRot() * Mth.DEG_TO_RAD * LOOK_PITCH + body.gust[0] * GUST_SWAY;
			// a turn throws the legs out the other way, so the body leans into it
			float rollWant = Mth.clamp((float)(right * LEAN_PER_SPEED), -LEAN_MAX, LEAN_MAX)
				- Mth.clamp(turn * TURN_LEAN, -TURN_LEAN_MAX, TURN_LEAN_MAX) + body.gust[1] * GUST_SWAY;
			float twistWant = Mth.clamp(turn * TURN_TWIST, -TURN_TWIST_MAX, TURN_TWIST_MAX);
			float bobWant = Mth.clamp((float)(-vy * SAG_PER_RISE), -SAG_MAX, SAG_MAX) + body.gust[2] * GUST_BOB;
			body.pitch.step(pitchWant * on, SPRING, DAMPING);
			body.roll.step(rollWant * on, SPRING, DAMPING);
			body.twist.step(twistWant * on, SPRING, DAMPING);
			body.bob.step(bobWant * on, SPRING, DAMPING);
			// the limbs chase the body, not the target, so they settle after it does
			body.armPitch.step(body.pitch.value, LIMB_SPRING, LIMB_DAMPING);
			body.legPitch.step(body.pitch.value, LIMB_SPRING * 0.8F, LIMB_DAMPING);
			body.limbRoll.step(body.roll.value, LIMB_SPRING, LIMB_DAMPING);
			for (int i = 0; i < body.drift.length; i++) {
				body.drift[i].step(body.gust[3 + i] * GUST_LIMB * on, LIMB_SPRING, LIMB_DAMPING);
			}
		}
		BODIES.entrySet().removeIf(entry -> entry.getValue().amount <= 0.0F && entry.getValue().prevAmount <= 0.0F
			|| level.getEntity(entry.getKey()) == null);
	}

	private static boolean isHovering(Minecraft minecraft, Player player) {
		// (0.18.91) Nadir floats the same way whenever he's off the ground
		if (com.jaymospowers.client.power.xenoform.XenoformFlightPose.floating(player)) {
			return true;
		}
		// (Andy, 2026-11-01) and so does anyone held up by Thessaly's mana wings (Jaymo's Kingdoms)
		if (ManaWingsCompat.hovering(player)) {
			return true;
		}
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
		float[] drift = new float[body.drift.length];
		for (int i = 0; i < drift.length; i++) {
			drift[i] = body.drift[i].at(partialTicks);
		}
		state.setData(KEY, new Sway(amount, body.pitch.at(partialTicks), body.roll.at(partialTicks), body.twist.at(partialTicks),
			body.bob.at(partialTicks), body.armPitch.at(partialTicks), body.legPitch.at(partialTicks), body.limbRoll.at(partialTicks), drift));
		// the legs shouldn't step through the air
		state.walkAnimationSpeed *= 1.0F - amount;
		if (amount > 0.5F) {
			// Sneak sinks; it shouldn't hunch
			state.isCrouching = false;
		}
	}

	/** After the body has been turned to face its way: twist it after the camera and swing it from the shoulders. */
	static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Sway sway = state.getData(KEY);
		if (sway == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float amount = sway.amount();
		float pivot = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * PIVOT;
		poseStack.translate(0.0F, pivot + sway.bob() * amount / Math.max(entityScale, 0.01F), 0.0F);
		// a turn to the right turns the body back to the left (the head is turned back to the camera in applyModel)
		poseStack.rotateDegrees(Axis.YP, sway.twist() * amount);
		// local +Z is the body's back and +X its right: a negative turn about X sends the feet back, about Z left
		poseStack.rotateDegrees(Axis.XP, -sway.pitch() * amount * Mth.RAD_TO_DEG);
		poseStack.rotateDegrees(Axis.ZP, -sway.roll() * amount * Mth.RAD_TO_DEG);
		poseStack.translate(0.0F, -pivot, 0.0F);
	}

	/** After the model animates: limp limbs that trail the swing, and the head kept on the camera. */
	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		Sway sway = avatar.getData(KEY);
		if (sway == null) {
			return;
		}
		float amount = sway.amount();
		float[] drift = sway.drift();
		humanoid.head.yRot += sway.twist() * amount * Mth.DEG_TO_RAD;
		// how far the limbs lag behind the body: they trail a swing that's starting and run on as it stops
		float armLag = sway.armPitch() - sway.pitch();
		float legLag = sway.legPitch() - sway.pitch();
		float spread = Math.abs(sway.limbRoll()) * 0.25F;
		// arms hang loose a little out from the sides (unless they're carrying someone)
		if (avatar.getData(com.jaymospowers.client.CarryPose.CARRIER_KEY) == null) {
			// swinging a weapon, they let go for the swing (upright enough here for Better Combat's own)
			WindFlightSwing.Swing swing = avatar.getData(WindFlightSwing.KEY);
			float arms = amount * (swing == null ? 1.0F : 1.0F - swing.weight());
			set(humanoid.rightArm, sway.armPitch() * 0.5F + armLag * 0.8F + drift[0], 0.0F,
				0.1F + spread + sway.limbRoll() * 0.3F + drift[1] * 0.4F, arms);
			set(humanoid.leftArm, sway.armPitch() * 0.5F + armLag * 0.8F + drift[1], 0.0F,
				-0.1F - spread + sway.limbRoll() * 0.3F - drift[0] * 0.4F, arms);
		}
		// legs dangle together, toes down, the right a touch ahead
		set(humanoid.rightLeg, sway.legPitch() * 0.35F + legLag * 0.9F + 0.06F + drift[2], 0.0F,
			0.03F + sway.limbRoll() * 0.15F, amount);
		set(humanoid.leftLeg, sway.legPitch() * 0.3F + legLag * 0.9F - 0.03F + drift[3], 0.0F,
			-0.03F + sway.limbRoll() * 0.15F, amount);
	}

	/**
	 * First person: {yaw, pitch} view nudges (degrees) and the eye's bob (blocks) from the local player's hovering
	 * body, or null when they aren't hovering.
	 */
	public static float[] viewOffsets(Minecraft minecraft, float partialTicks) {
		Body body = minecraft.player == null ? null : BODIES.get(minecraft.player.getId());
		if (body == null) {
			return null;
		}
		float amount = Mth.lerp(partialTicks, body.prevAmount, body.amount);
		if (amount <= 0.001F) {
			return null;
		}
		// the head rides the shoulders, which rock the opposite way to the hanging legs
		float lookPitch = minecraft.player.getXRot(partialTicks) * Mth.DEG_TO_RAD * LOOK_PITCH;
		return new float[] {-body.twist.at(partialTicks) * VIEW_YAW * amount,
			(body.pitch.at(partialTicks) - lookPitch) * VIEW_PITCH * amount, body.bob.at(partialTicks) * VIEW_BOB * amount};
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
