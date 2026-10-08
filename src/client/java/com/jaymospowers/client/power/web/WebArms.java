package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebLine;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebPower;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Web-slinger body physics, built the same way as Jaymo's Kitchen's corpse ragdolls (Limp / Jolt): a little
 * spring-and-damper state per player, ticked here, put on the player's render state, and applied to the model's
 * parts by name right after the model animates (see the web client mixins).
 * <ul>
 * <li>Each arm with a web out reaches along its web: it springs toward the anchor with a bit of lag and overshoot, so a
 * fresh web snaps the arm up and a swing tugs it around.</li>
 * <li>While hanging from a web the legs go limp: they trail behind the swing and flop sideways with it, settling as
 * the swing slows.</li>
 * </ul>
 * The web line is drawn from the posed hand ({@link #handPos}), so it really comes out of the arm.
 */
public final class WebArms {
	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_arms");

	/** Arm aim spring: pull toward the web per tick, and velocity kept per tick (lower = more damped). */
	private static final double ARM_SPRING = 0.35;
	private static final double ARM_DAMPING = 0.55;
	/** Leg ragdoll spring, damping, and how far the swing speed throws them. */
	private static final float LEG_SPRING = 0.22F;
	private static final float LEG_DAMPING = 0.78F;
	private static final float LEG_TRAIL_PER_SPEED = 0.9F;
	private static final float LEG_TRAIL_MAX = 1.1F;
	/** How fast the whole pose eases in and out, per tick. */
	private static final float EASE = 0.25F;
	/** Shoulder pivot above the feet and out to the side, and the shoulder-to-hand length (model units / 16). */
	private static final double SHOULDER_HEIGHT = 22.0 / 16.0;
	private static final double SHOULDER_OUT = 5.0 / 16.0;
	private static final double ARM_LENGTH = 10.0 / 16.0;

	/** This frame's pose: an arm's aim is null when that hand has no web out. Angles are model-part radians. */
	public record Pose(float amount, float @Nullable [] mainArm, float @Nullable [] offArm, float legTrail, float legSide, float wings, float lean) {
	}

	/** Gliding: the body leans this far forward (degrees), easing in over {@code LEAN_TICKS} of the glide. */
	private static final float GLIDE_LEAN = 70.0F;
	private static final float LEAN_TICKS = 30.0F;

	/** Spread wings: how far the arms are raised out from the sides (radians from hanging down). */
	private static final float WING_SPREAD = 1.35F;

	/** A player's springy state. Directions are world-space unit vectors (from the shoulder toward the web). */
	private static final class Body {
		final Vec3[] dir = new Vec3[2];
		final Vec3[] prevDir = new Vec3[2];
		final Vec3[] dirVel = {Vec3.ZERO, Vec3.ZERO};
		float trail, trailVel, prevTrail;
		float side, sideVel, prevSide;
		float amount, prevAmount;
		float wings, prevWings;
		int wingsAge;
		float lean, prevLean;
	}

	private static final Map<Integer, Body> BODIES = new HashMap<>();

	private WebArms() {
	}

	// ---- ticking --------------------------------------------------------------------------------------------------

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			BODIES.clear();
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			Vec3 main = lineEnd(minecraft, level, player, WebNet.MAIN, 1.0F);
			Vec3 off = lineEnd(minecraft, level, player, WebNet.OFF, 1.0F);
			boolean winged = isWinged(minecraft, player);
			Body body = BODIES.get(player.getId());
			if (body == null) {
				if (main == null && off == null && !winged) {
					continue;
				}
				body = new Body();
				BODIES.put(player.getId(), body);
			}
			body.prevWings = body.wings;
			body.wings = winged ? Math.min(1.0F, body.wings + 0.3F) : Math.max(0.0F, body.wings - 0.2F);
			// the lean comes on gradually through the glide (smoothstep), and comes back up quickly when the wings fold
			body.wingsAge = winged ? body.wingsAge + 1 : 0;
			body.prevLean = body.lean;
			float t = Math.min(1.0F, body.wingsAge / LEAN_TICKS);
			body.lean = winged ? t * t * (3.0F - 2.0F * t) : Math.max(0.0F, body.lean - 0.15F);
			tickBody(player, body, main, off);
		}
		BODIES.entrySet().removeIf(entry -> entry.getValue().amount <= 0.0F && entry.getValue().prevAmount <= 0.0F
			&& entry.getValue().wings <= 0.0F && entry.getValue().prevWings <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	private static boolean isWinged(Minecraft minecraft, Player player) {
		if (player == minecraft.player) {
			return WebPowerClient.isActive(minecraft.player) && WebPowerClient.INSTANCE.swing.wingsTicks > 0;
		}
		return player.getAttached(WebPower.WINGS) != null;
	}

	/** How spread a player's wings are this frame (0 = folded). */
	static float wings(Player player, float partialTick) {
		Body body = BODIES.get(player.getId());
		return body == null ? 0.0F : Mth.lerp(partialTick, body.prevWings, body.wings);
	}

	/** A spread wing's tip (the hand, out to the side) for the body's right ({@code side} 1) or left (-1). */
	static Vec3 wingTip(Player player, int side, float spread, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
		double scale = player.getScale();
		Vec3 shoulder = player.getPosition(partialTick).add(right.scale(side * SHOULDER_OUT * scale)).add(0.0, SHOULDER_HEIGHT * scale, 0.0);
		double angle = WING_SPREAD * spread;
		return shoulder.add(right.scale(side * Math.sin(angle) * ARM_LENGTH * 1.25 * scale)).add(0.0, -Math.cos(angle) * ARM_LENGTH * 1.25 * scale, 0.0);
	}

	/** Where a wing's web meets the body: the hip on that side, and the shoulder. */
	static Vec3 wingHip(Player player, int side, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
		return player.getPosition(partialTick).add(right.scale(side * 0.22 * player.getScale())).add(0.0, 0.7 * player.getScale(), 0.0);
	}

	static Vec3 wingShoulder(Player player, int side, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
		return player.getPosition(partialTick).add(right.scale(side * SHOULDER_OUT * player.getScale())).add(0.0, SHOULDER_HEIGHT * player.getScale(), 0.0);
	}

	private static void tickBody(Player player, Body body, @Nullable Vec3 main, @Nullable Vec3 off) {
		boolean hanging = main != null || off != null;
		body.prevAmount = body.amount;
		body.amount = hanging ? Math.min(1.0F, body.amount + EASE) : Math.max(0.0F, body.amount - EASE);
		Vec3[] targets = {main, off};
		for (int hand = 0; hand < 2; hand++) {
			Vec3 target = targets[hand];
			if (target == null) {
				body.dir[hand] = null;
				body.prevDir[hand] = null;
				body.dirVel[hand] = Vec3.ZERO;
				continue;
			}
			Vec3 shoulder = shoulder(player, hand, 1.0F);
			Vec3 want = target.subtract(shoulder);
			want = want.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : want.normalize();
			if (body.dir[hand] == null) {
				// a fresh web: the arm starts from hanging at the side and snaps up to it
				body.dir[hand] = new Vec3(0.0, -1.0, 0.0);
				body.dirVel[hand] = Vec3.ZERO;
			}
			body.prevDir[hand] = body.dir[hand];
			Vec3 vel = body.dirVel[hand].add(want.subtract(body.dir[hand]).scale(ARM_SPRING)).scale(ARM_DAMPING);
			Vec3 next = body.dir[hand].add(vel);
			body.dirVel[hand] = vel;
			body.dir[hand] = next.lengthSqr() < 1.0E-6 ? want : next.normalize();
		}
		// legs: trail behind the body's motion (in its own frame) and flop sideways, like a hanging ragdoll
		Vec3 v = player.getDeltaMovement();
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		double forwardSpeed = -Mth.sin(yaw) * v.x + Mth.cos(yaw) * v.z;
		double sideSpeed = Mth.cos(yaw) * v.x + Mth.sin(yaw) * v.z;
		boolean airborne = hanging && !player.onGround();
		float trailWant = airborne ? Mth.clamp((float)(forwardSpeed - v.y * 0.35) * LEG_TRAIL_PER_SPEED, -LEG_TRAIL_MAX, LEG_TRAIL_MAX) : 0.0F;
		float sideWant = airborne ? Mth.clamp((float)sideSpeed * 0.8F, -0.6F, 0.6F) : 0.0F;
		body.prevTrail = body.trail;
		body.trailVel = (body.trailVel + (trailWant - body.trail) * LEG_SPRING) * LEG_DAMPING;
		body.trail += body.trailVel;
		body.prevSide = body.side;
		body.sideVel = (body.sideVel + (sideWant - body.side) * LEG_SPRING) * LEG_DAMPING;
		body.side += body.sideVel;
	}

	/** Where the hand's web ends for any player: the local player's own state, else the synced lines. */
	static @Nullable Vec3 lineEnd(Minecraft minecraft, ClientLevel level, Player player, int hand, float partialTick) {
		if (player == minecraft.player && WebPowerClient.isActive(minecraft.player)) {
			return WebPowerClient.INSTANCE.swing.lineEnd(hand, partialTick);
		}
		WebLine line = player.getAttached(WebPower.line(hand));
		if (line == null) {
			return null;
		}
		if (line.entityId() >= 0) {
			Entity entity = level.getEntity(line.entityId());
			if (entity != null) {
				return entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0);
			}
		}
		return line.anchor();
	}

	// ---- geometry -------------------------------------------------------------------------------------------------

	/** +1 for the right side of the body, -1 for the left, for a hand. */
	private static int side(Player player, int hand) {
		boolean mainRight = player.getMainArm() == HumanoidArm.RIGHT;
		boolean right = hand == WebNet.MAIN ? mainRight : !mainRight;
		return right ? 1 : -1;
	}

	/** The shoulder joint in the world. */
	static Vec3 shoulder(Player player, int hand, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		double scale = player.getScale();
		double out = side(player, hand) * SHOULDER_OUT * scale;
		// the body's right is (-cos, -sin) in x/z for Minecraft's yaw
		return player.getPosition(partialTick).add(-Mth.cos(bodyYaw) * out, SHOULDER_HEIGHT * scale, -Mth.sin(bodyYaw) * out);
	}

	/** Where the posed hand is (for the web line), or null if this player's arm isn't being posed for that hand. */
	static @Nullable Vec3 handPos(Player player, int hand, float partialTick) {
		Body body = BODIES.get(player.getId());
		if (body == null || body.dir[hand] == null) {
			return null;
		}
		Vec3 prev = body.prevDir[hand] == null ? body.dir[hand] : body.prevDir[hand];
		Vec3 dir = prev.lerp(body.dir[hand], partialTick);
		return shoulder(player, hand, partialTick).add(dir.scale(ARM_LENGTH * player.getScale()));
	}

	// ---- rendering ------------------------------------------------------------------------------------------------

	/** From the avatar renderer's extractRenderState: this frame's pose, in model-part angles. */
	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		Body body = BODIES.get(entity.getId());
		if (body == null || !(entity instanceof Player player) || state.isFallFlying || state.isVisuallySwimming) {
			state.setData(KEY, null);
			return;
		}
		float amount = Mth.lerp(partialTicks, body.prevAmount, body.amount);
		float wings = Mth.lerp(partialTicks, body.prevWings, body.wings);
		if (amount <= 0.001F && wings <= 0.001F) {
			state.setData(KEY, null);
			return;
		}
		float bodyYaw = Mth.lerp(partialTicks, player.yBodyRotO, player.yBodyRot);
		float[] main = aim(body, WebNet.MAIN, bodyYaw, partialTicks);
		float[] off = aim(body, WebNet.OFF, bodyYaw, partialTicks);
		state.setData(KEY, new Pose(amount, main, off,
			Mth.lerp(partialTicks, body.prevTrail, body.trail), Mth.lerp(partialTicks, body.prevSide, body.side), wings,
			Mth.lerp(partialTicks, body.prevLean, body.lean)));
	}

	/** An arm's {xRot, yRot} pointing it along its web, relative to the body (as the bow-aiming pose does it). */
	private static float @Nullable [] aim(Body body, int hand, float bodyYawDegrees, float partialTicks) {
		if (body.dir[hand] == null) {
			return null;
		}
		Vec3 prev = body.prevDir[hand] == null ? body.dir[hand] : body.prevDir[hand];
		Vec3 dir = prev.lerp(body.dir[hand], partialTicks);
		if (dir.lengthSqr() < 1.0E-6) {
			return null;
		}
		dir = dir.normalize();
		float elevation = (float)Math.asin(Mth.clamp(dir.y, -1.0, 1.0));
		float yaw = (float)(Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
		float relative = Mth.wrapDegrees(yaw - bodyYawDegrees) * Mth.DEG_TO_RAD;
		return new float[] {-Mth.HALF_PI - elevation, relative};
	}

	/** After the body has been turned to face its way: a glider leans forward into the flying pose. */
	public static void rotate(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, float entityScale) {
		Pose pose = state.getData(KEY);
		if (pose == null || pose.lean() <= 0.001F || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotateDegrees(com.mojang.math.Axis.XP, -GLIDE_LEAN * pose.lean());
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the model animates (body and armour alike): the arms along their webs, the legs limp. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		if (pose.wings() > 0.001F) {
			// wings spread: arms straight out to the sides, legs together behind
			float w = pose.wings();
			float flutter = Mth.sin(avatar.ageInTicks * 0.8F) * 0.04F;
			set(humanoid.rightArm, 0.1F, 0.0F, WING_SPREAD + flutter, w);
			set(humanoid.leftArm, 0.1F, 0.0F, -WING_SPREAD - flutter, w);
			set(humanoid.rightLeg, 0.15F, 0.0F, 0.05F, w);
			set(humanoid.leftLeg, 0.15F, 0.0F, -0.05F, w);
			// leaning into the glide: head up to look ahead
			set(humanoid.head, -1.1F * pose.lean(), humanoid.head.yRot, 0.0F, w);
		}
		float amount = pose.amount();
		boolean mainRight = avatar.mainArm != HumanoidArm.LEFT;
		ModelPart mainArm = mainRight ? humanoid.rightArm : humanoid.leftArm;
		ModelPart offArm = mainRight ? humanoid.leftArm : humanoid.rightArm;
		if (pose.mainArm() != null) {
			set(mainArm, pose.mainArm()[0], pose.mainArm()[1], 0.0F, amount);
		}
		if (pose.offArm() != null) {
			set(offArm, pose.offArm()[0], pose.offArm()[1], 0.0F, amount);
		}
		// limp legs, each a little out of step so they don't move as one
		float flop = Mth.sin(avatar.ageInTicks * 0.35F) * 0.04F * Math.abs(pose.legTrail());
		set(humanoid.rightLeg, pose.legTrail() + 0.12F + flop, 0.0F, 0.08F + pose.legSide() * 0.9F, amount);
		set(humanoid.leftLeg, pose.legTrail() * 0.8F - 0.05F - flop, 0.0F, -0.08F + pose.legSide() * 0.7F, amount);
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
