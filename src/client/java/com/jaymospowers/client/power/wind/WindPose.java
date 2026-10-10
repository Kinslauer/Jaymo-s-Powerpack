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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * The "superman" pose: a player cruising (theirs or anyone's, from the synced flight state) leans into the flight
 * direction, body along their look, one fist out ahead, the other arm at their side and legs trailing. Eases in
 * and out over a few ticks.
 */
public final class WindPose {
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_pose");
	/** The air brake's pose amount (palms pushed out ahead, body pulled upright). */
	public static final RenderStateDataKey<Float> BRAKE_KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_brake");
	private static final float BRAKE_EASE_IN = 0.3F;
	private static final float BRAKE_EASE_OUT = 0.15F;
	/** How far back the body pulls while braking (degrees, against the flight lean). */
	private static final float BRAKE_LEAN_BACK = 25.0F;
	/** entity id → {previous, current} brake amount. */
	private static final Map<Integer, float[]> BRAKES = new HashMap<>();
	/** How fast the pose eases in and out, per tick. */
	private static final float EASE_IN = 0.12F;
	private static final float EASE_OUT = 0.18F;

	/** entity id → {previous, current} pose amount. */
	private static final Map<Integer, float[]> AMOUNTS = new HashMap<>();

	/**
	 * Banking: at speed the body keeps to its path (the way it's actually moving, not where the head looks) and rolls
	 * into a turn, {@code BANK_PER_DEGREE} degrees for each degree a tick the path swings, up to {@code BANK_MAX}.
	 */
	public static final RenderStateDataKey<float[]> BANK_KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_bank");
	private static final float BANK_PER_DEGREE = 7.0F, BANK_MAX = 60.0F, BANK_EASE = 0.2F;

	/** A Tempest Kick under way: how far through it (ticks since it began), which leg, and its seed. */
	public static final RenderStateDataKey<KickPose> KICK_KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_kick");

	/** {@code ticks} since the kick began (with the partial tick), the kicking leg, and the arm variant and jitter seed. */
	public record KickPose(float ticks, boolean left, int seed) {
		/** The arm variant, as on the server's {@code WindKick.Kicking}. */
		int arms() {
			return this.seed & 3;
		}

		/** A jitter in [-1, 1], different for each {@code slot} of the same kick. */
		float jitter(int slot) {
			int h = this.seed * 0x9E3779B1 + slot * 0x85EBCA6B;
			h ^= h >>> 15;
			h *= 0x2C1B3C6D;
			h ^= h >>> 13;
			return (h & 0xFFFF) / 32767.5F - 1.0F;
		}
	}

	/**
	 * The kick's lean: the whole body tips away from the kicking leg about the standing foot (degrees, before jitter),
	 * tilts back a little, twists through with the swing, and rises onto the ball of the foot (blocks).
	 */
	private static final float KICK_LEAN = 30.0F, KICK_LEAN_JITTER = 5.0F, KICK_LEAN_BACK = 8.0F;
	private static final float KICK_TWIST_WINDUP = -12.0F, KICK_TWIST_THROUGH = 28.0F, KICK_RISE = 0.07F;
	/** Where the standing foot is, out from the body's middle (blocks). */
	private static final float KICK_FOOT_OFFSET = 0.12F;
	/** The kick's beats (ticks): the snap starts at the end of the wind-up, the recovery at {@code KICK_RECOVER}. */
	private static final float KICK_SNAP_END = com.jaymospowers.power.wind.WindKick.WINDUP_TICKS + 1.5F, KICK_RECOVER = 6.5F;
	/** Kicking leg: chambered, at the top of the arc (out to the side, head high) and swept through across the front. */
	private static final float LEG_CHAMBER_X = -1.25F, LEG_CHAMBER_Y = 0.35F, LEG_CHAMBER_Z = 0.25F;
	private static final float LEG_APEX_X = -1.95F, LEG_APEX_Y = 1.25F, LEG_APEX_Z = 0.15F;
	private static final float LEG_THROUGH_X = -1.75F, LEG_THROUGH_Y = -0.45F, LEG_THROUGH_Z = 0.1F;
	private static final float LIMB_JITTER = 0.15F;
	/** The high arm: up and back over the head. How much of the lean the standing leg turns back against. */
	private static final float HIGH_ARM_X = -3.2F, STANDING_LEG_UPRIGHT = 0.3F;

	/** entity id → {previous heading, heading, previous bank, bank, last x, last z}. */
	private static final Map<Integer, double[]> PATHS = new HashMap<>();

	private WindPose() {
	}

	static void tick(Minecraft minecraft) {
		WindHoverSway.tick(minecraft);
		WindTumble.tick(minecraft);
		WindCyclonePose.tick(minecraft);
		ClientLevel level = minecraft.level;
		if (level == null) {
			AMOUNTS.clear();
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			boolean fast = isFast(minecraft, player);
			float[] amount = AMOUNTS.get(player.getId());
			if (amount == null) {
				if (!fast) {
					continue;
				}
				amount = new float[2];
				AMOUNTS.put(player.getId(), amount);
			}
			amount[0] = amount[1];
			amount[1] = fast ? Math.min(1.0F, amount[1] + EASE_IN) : Math.max(0.0F, amount[1] - EASE_OUT);
		}
		AMOUNTS.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
		for (AbstractClientPlayer player : level.players()) {
			bank(player, AMOUNTS.containsKey(player.getId()));
		}
		PATHS.keySet().removeIf(id -> !AMOUNTS.containsKey(id));
		for (AbstractClientPlayer player : level.players()) {
			boolean braking = player == minecraft.player
				? WindPowerClient.isActive(minecraft.player) && WindPowerClient.INSTANCE.flight().braking()
				: player.getAttached(WindPower.BRAKING) != null;
			float[] brake = BRAKES.get(player.getId());
			if (brake == null) {
				if (!braking) {
					continue;
				}
				brake = new float[2];
				BRAKES.put(player.getId(), brake);
			}
			brake[0] = brake[1];
			brake[1] = braking ? Math.min(1.0F, brake[1] + BRAKE_EASE_IN) : Math.max(0.0F, brake[1] - BRAKE_EASE_OUT);
		}
		BRAKES.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	/** The path's heading, and the roll into the turn as it swings. */
	private static void bank(Player player, boolean flying) {
		if (!flying) {
			return;
		}
		double[] path = PATHS.get(player.getId());
		if (path == null) {
			path = new double[] {player.getYRot(), player.getYRot(), 0.0, 0.0, player.getX(), player.getZ()};
			PATHS.put(player.getId(), path);
		}
		double dx = player.getX() - path[4], dz = player.getZ() - path[5];
		path[4] = player.getX();
		path[5] = player.getZ();
		path[0] = path[1];
		path[2] = path[3];
		double target = 0.0;
		if (dx * dx + dz * dz > 0.05 * 0.05) {
			double heading = Mth.atan2(dz, dx) * Mth.RAD_TO_DEG - 90.0;
			double swing = Mth.wrapDegrees(heading - path[1]);
			path[1] = path[1] + swing;
			target = Mth.clamp(swing * BANK_PER_DEGREE, -BANK_MAX, BANK_MAX);
		}
		path[3] = path[3] + (target - path[3]) * BANK_EASE;
	}

	private static boolean isFast(Minecraft minecraft, Player player) {
		if (player == minecraft.player) {
			return WindPowerClient.INSTANCE.flight().state().fast() && WindPowerClient.isActive(minecraft.player);
		}
		Integer state = player.getAttached(WindPower.FLIGHT);
		WindFlight flight = state == null ? null : WindFlight.byId(state);
		return flight != null && flight.fast();
	}

	/** From the avatar renderer's extractRenderState: put this frame's pose amount on the state. */
	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		if (com.jaymospowers.core.Carry.isCarried(entity)) {
			// held in someone's arms: none of the wind's poses (the carry has the body)
			state.setData(KICK_KEY, null);
			state.setData(KEY, null);
			state.setData(BRAKE_KEY, null);
			state.setData(BANK_KEY, null);
			state.setData(WindHoverSway.KEY, null);
			state.setData(WindTumble.KEY, null);
			state.setData(WindCyclonePose.KEY, null);
			return;
		}
		WindCyclonePose.Stance stance = WindCyclonePose.extract(entity, partialTicks);
		state.setData(WindCyclonePose.KEY, stance);
		if (stance != null) {
			// down on one knee: the landing has the whole body
			state.isCrouching = false;
			state.setData(KICK_KEY, null);
			state.setData(KEY, null);
			state.setData(BRAKE_KEY, null);
			state.setData(BANK_KEY, null);
			state.setData(WindHoverSway.KEY, null);
			state.setData(WindTumble.KEY, null);
			return;
		}
		com.jaymospowers.power.wind.WindKick.Kicking kicked = entity.getAttached(com.jaymospowers.power.wind.WindKick.KICKING);
		float kick = kicked == null ? -1.0F : entity.level().getGameTime() - kicked.start() + partialTicks;
		state.setData(KICK_KEY, kick >= 0.0F && kick < com.jaymospowers.power.wind.WindKick.KICK_TICKS
			? new KickPose(kick, kicked.left(), kicked.seed()) : null);
		WindHoverSway.extract(entity, state, partialTicks);
		WindTumble.extract(entity, state, partialTicks);
		float[] amount = AMOUNTS.get(entity.getId());
		float value = amount == null ? 0.0F : Mth.lerp(partialTicks, amount[0], amount[1]);
		float[] brake = BRAKES.get(entity.getId());
		float brakeValue = brake == null ? 0.0F : Mth.lerp(partialTicks, brake[0], brake[1]);
		state.setData(BRAKE_KEY, brakeValue > 0.001F ? brakeValue : null);
		if (value <= 0.001F || state.isFallFlying || state.isVisuallySwimming) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, value);
		double[] path = PATHS.get(entity.getId());
		state.setData(BANK_KEY, path == null ? null
			: new float[] {(float)Mth.rotLerp(partialTicks, path[0], path[1]), (float)Mth.lerp(partialTicks, path[2], path[3])});
		if (value > 0.3F) {
			// no hunched-over crouch while flying (Sneak toggles the cruise lock)
			state.isCrouching = false;
		}
	}

	/** After the body has been turned to face its way: tip it into the flight, pivoting about its middle. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		WindHoverSway.rotate(state, poseStack, entityScale);
		WindTumble.rotate(state, poseStack, entityScale);
		kickLean(state, poseStack);
		WindCyclonePose.rotate(state, poseStack);
		Float amount = state.getData(KEY);
		if (amount == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		Float brake = state.getData(BRAKE_KEY);
		tilt(poseStack, amount, brake == null ? 0.0F : brake, state.getData(BANK_KEY), state.yRot, state.xRot, state.bodyRot, middle);
	}

	/**
	 * The same tilt as {@link #rotate} gives this player's own body, for something drawn with them (a body they're
	 * carrying), in their body's frame. Nothing when they aren't cruising.
	 */
	public static void tiltLike(Entity entity, float partialTicks, PoseStack poseStack) {
		float[] amount = AMOUNTS.get(entity.getId());
		float value = amount == null ? 0.0F : Mth.lerp(partialTicks, amount[0], amount[1]);
		if (value <= 0.001F || !(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
			return;
		}
		float[] brake = BRAKES.get(entity.getId());
		float b = brake == null ? 0.0F : Mth.lerp(partialTicks, brake[0], brake[1]);
		double[] path = PATHS.get(entity.getId());
		float[] bank = path == null ? null
			: new float[] {(float)Mth.rotLerp(partialTicks, path[0], path[1]), (float)Mth.lerp(partialTicks, path[2], path[3])};
		float bodyRot = Mth.rotLerp(partialTicks, living.yBodyRotO, living.yBodyRot);
		float headYaw = Mth.wrapDegrees(entity.getYRot(partialTicks) - bodyRot);
		tilt(poseStack, value, b, bank, headYaw, entity.getXRot(partialTicks), bodyRot, entity.getBbHeight() * 0.5F);
	}

	private static void tilt(PoseStack poseStack, float amount, float b, float @org.jspecify.annotations.Nullable [] bank, float yRot, float xRot,
		float bodyRot, float middle) {
		// the air brake pulls the body up out of the dive, leaning back into the gust
		// face along the path it's actually taking (a quick look aside doesn't swing the body round), else where the head faces
		float facing = bank == null ? yRot : Mth.wrapDegrees(bank[0] - bodyRot);
		poseStack.rotateDegrees(Axis.YP, -facing * amount);
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotateDegrees(Axis.XP, amount * (1.0F - 0.75F * b) * (-90.0F - xRot) + b * BRAKE_LEAN_BACK);
		if (bank != null) {
			// rolled into the turn, about the body's own length
			poseStack.rotateDegrees(Axis.YP, bank[1] * amount * (1.0F - b));
		}
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** How far into the kick's lean (0-1): eased in over the wind-up, held through the snap, eased out in the recovery. */
	private static float kickAmount(float ticks) {
		float in = Mth.clamp(ticks / com.jaymospowers.power.wind.WindKick.WINDUP_TICKS, 0.0F, 1.0F);
		float out = Mth.clamp((com.jaymospowers.power.wind.WindKick.KICK_TICKS - ticks)
			/ (com.jaymospowers.power.wind.WindKick.KICK_TICKS - KICK_RECOVER), 0.0F, 1.0F);
		return smooth(Math.min(in, out));
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	/**
	 * The kick's whole-body lean: pivoting on the standing foot, the body tips hard away from the kicking leg, a
	 * little back, and twists through with the swing, up on the ball of the foot. Done on the whole model, so the
	 * head and arms go with it.
	 */
	private static void kickLean(LivingEntityRenderState state, PoseStack poseStack) {
		KickPose kick = state.getData(KICK_KEY);
		if (kick == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float amount = kickAmount(kick.ticks());
		// +1: the right leg kicks, so the left foot stands and the body leans left (local +X is the body's right)
		float side = kick.left() ? -1.0F : 1.0F;
		float lean = (KICK_LEAN + kick.jitter(0) * KICK_LEAN_JITTER) * amount;
		float through = smooth(Mth.clamp((kick.ticks() - com.jaymospowers.power.wind.WindKick.WINDUP_TICKS + 0.5F) / 2.5F, 0.0F, 1.0F));
		float twist = Mth.lerp(through, KICK_TWIST_WINDUP, KICK_TWIST_THROUGH + kick.jitter(1) * 6.0F) * amount;
		float footX = -side * KICK_FOOT_OFFSET;
		poseStack.translate(footX, KICK_RISE * amount, 0.0F);
		// (a positive turn about Y turns the body to its left; about Z it tips the top to the right)
		poseStack.rotateDegrees(Axis.YP, side * twist);
		poseStack.rotateDegrees(Axis.ZP, -side * lean);
		poseStack.rotateDegrees(Axis.XP, KICK_LEAN_BACK * amount);
		poseStack.translate(-footX, 0.0F, 0.0F);
	}

	/**
	 * The kick's limbs. The kicking leg chambers through the wind-up, whips round to head height at the snap and
	 * across the front, then drops back; the standing leg stays straight (against the lean); the arms are thrown
	 * opposite ways in one of four variants.
	 */
	private static void kickLimbs(HumanoidModel<?> h, KickPose kick) {
		float t = kick.ticks();
		float amount = kickAmount(t);
		float side = kick.left() ? -1.0F : 1.0F;
		ModelPart kicking = kick.left() ? h.leftLeg : h.rightLeg;
		ModelPart standing = kick.left() ? h.rightLeg : h.leftLeg;
		float windup = com.jaymospowers.power.wind.WindKick.WINDUP_TICKS;
		float j = kick.jitter(2) * LIMB_JITTER;
		float x, y, z;
		if (t < windup) {
			// chambering: knee up and out to the side
			float c = smooth(t / windup);
			x = LEG_CHAMBER_X * c;
			y = LEG_CHAMBER_Y * c;
			z = LEG_CHAMBER_Z * c;
		} else if (t < KICK_SNAP_END) {
			// the snap: out and up to head height, then round across the front
			float snap = (t - windup) / (KICK_SNAP_END - windup);
			if (snap < 0.4F) {
				float u = smooth(snap / 0.4F);
				x = Mth.lerp(u, LEG_CHAMBER_X, LEG_APEX_X + j);
				y = Mth.lerp(u, LEG_CHAMBER_Y, LEG_APEX_Y);
				z = Mth.lerp(u, LEG_CHAMBER_Z, LEG_APEX_Z);
			} else {
				float u = smooth((snap - 0.4F) / 0.6F);
				x = Mth.lerp(u, LEG_APEX_X + j, LEG_THROUGH_X);
				y = Mth.lerp(u, LEG_APEX_Y, LEG_THROUGH_Y);
				z = Mth.lerp(u, LEG_APEX_Z, LEG_THROUGH_Z);
			}
		} else {
			// follow-through, then down again
			float down = smooth(Mth.clamp((t - KICK_SNAP_END) / (com.jaymospowers.power.wind.WindKick.KICK_TICKS - KICK_SNAP_END), 0.0F, 1.0F));
			x = Mth.lerp(down, LEG_THROUGH_X, 0.0F);
			y = Mth.lerp(down, LEG_THROUGH_Y, 0.0F);
			z = Mth.lerp(down, LEG_THROUGH_Z, 0.0F);
		}
		pose(kicking, x, side * y, side * z, 1.0F);
		// the standing leg straight, a little more upright than the leaning body (the foot stays planted)
		float lean = (KICK_LEAN + kick.jitter(0) * KICK_LEAN_JITTER) * Mth.DEG_TO_RAD;
		pose(standing, 0.12F, 0.0F, -side * lean * STANDING_LEG_UPRIGHT, amount);
		// the arms: "high" goes up and back above the head, fist clenched; "low" across the hip (or back behind)
		ModelPart kickSide = kick.left() ? h.leftArm : h.rightArm;
		ModelPart leanSide = kick.left() ? h.rightArm : h.leftArm;
		// outward zRot for each arm: the right arm swings out with +, the left with -
		float kickOut = kick.left() ? -1.0F : 1.0F;
		float leanOut = -kickOut;
		float a1 = kick.jitter(3) * LIMB_JITTER, a2 = kick.jitter(4) * LIMB_JITTER, a3 = kick.jitter(5) * LIMB_JITTER;
		switch (kick.arms()) {
			case 0 -> {
				pose(leanSide, HIGH_ARM_X + a1, 0.0F, leanOut * (0.35F + a2), amount);
				pose(kickSide, -0.45F + a3, 0.0F, -kickOut * (0.55F + a2), amount);
			}
			case 1 -> {
				pose(kickSide, HIGH_ARM_X + a1, 0.0F, kickOut * (0.35F + a2), amount);
				pose(leanSide, -0.45F + a3, 0.0F, -leanOut * (0.55F + a2), amount);
			}
			case 2 -> {
				pose(leanSide, -2.2F + a1, 0.0F, leanOut * (0.95F + a2), amount);
				pose(kickSide, -2.0F + a3, 0.0F, kickOut * (1.05F - a2), amount);
			}
			default -> {
				pose(leanSide, HIGH_ARM_X + a1, 0.0F, leanOut * (0.35F + a2), amount);
				pose(kickSide, 0.95F + a3, 0.0F, kickOut * (0.25F + a2), amount);
			}
		}
		// the head stays on the target: turned back against the twist, levelled against some of the lean
		float through = smooth(Mth.clamp((t - windup + 0.5F) / 2.5F, 0.0F, 1.0F));
		float twist = Mth.lerp(through, KICK_TWIST_WINDUP, KICK_TWIST_THROUGH) * Mth.DEG_TO_RAD;
		h.head.yRot += side * twist * amount;
		h.head.zRot -= side * lean * 0.5F * amount;
		h.body.yRot = 0.0F;
	}

	/** After the model animates: the limbs. */
	public static void applyModel(Model<?> model, Object state) {
		// the hover ragdoll first; the cruise pose, easing in over it, wins as speed picks up
		WindHoverSway.applyModel(model, state);
		WindTumble.applyModel(model, state);
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		WindCyclonePose.Stance stance = avatar.getData(WindCyclonePose.KEY);
		if (stance != null) {
			WindCyclonePose.applyModel(humanoid, stance);
			return;
		}
		KickPose kick = avatar.getData(KICK_KEY);
		if (kick != null) {
			kickLimbs(humanoid, kick);
			return;
		}
		Float amount = avatar.getData(KEY);
		if (amount == null) {
			return;
		}
		float flutter = Mth.sin(avatar.ageInTicks * 0.9F) * 0.05F;
		// carrying someone, the arms are busy with the hold (see CarryPose)
		boolean armsFree = avatar.getData(com.jaymospowers.client.CarryPose.CARRIER_KEY) == null;
		// head up to look along the flight
		pose(humanoid.head, -1.15F, 0.0F, 0.0F, amount);
		ModelPart lead = avatar.mainArm == HumanoidArm.LEFT ? humanoid.leftArm : humanoid.rightArm;
		ModelPart trail = lead == humanoid.rightArm ? humanoid.leftArm : humanoid.rightArm;
		float outward = trail == humanoid.rightArm ? 1.0F : -1.0F;
		if (armsFree) {
			// fist out ahead
			pose(lead, -Mth.PI + 0.12F + flutter, 0.0F, -outward * 0.06F, amount);
			// the other arm swept back along the side
			pose(trail, 0.15F - flutter, 0.0F, outward * 0.18F, amount);
		}
		// legs together, trailing, rippling slightly in the wind
		pose(humanoid.rightLeg, 0.06F + flutter, 0.0F, 0.04F, amount);
		pose(humanoid.leftLeg, 0.06F - flutter, 0.0F, -0.04F, amount);
		Float brake = avatar.getData(BRAKE_KEY);
		if (brake != null && armsFree) {
			// the air brake: both palms thrust out ahead, shaking against the gust; legs swung forward to stop
			float shake = Mth.sin(avatar.ageInTicks * 2.3F) * 0.04F;
			pose(humanoid.rightArm, -Mth.HALF_PI - 0.25F + shake, -0.25F, 0.1F, brake);
			pose(humanoid.leftArm, -Mth.HALF_PI - 0.25F - shake, 0.25F, -0.1F, brake);
			pose(humanoid.rightLeg, -0.45F, 0.0F, 0.08F, brake);
			pose(humanoid.leftLeg, -0.25F, 0.0F, -0.08F, brake);
			pose(humanoid.head, -0.2F, 0.0F, 0.0F, brake);
		}
	}

	private static void pose(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
