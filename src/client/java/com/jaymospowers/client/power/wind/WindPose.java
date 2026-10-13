package com.jaymospowers.client.power.wind;

import com.jaymospowers.power.wind.WindFlight;
import com.jaymospowers.power.wind.WindKick;
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

	/**
	 * {@code ticks} since the kick began (with the partial tick), the kicking leg, the style and jitter seed, and the
	 * wind-up's length (ticks; longer in the cold).
	 */
	public record KickPose(float ticks, boolean left, int seed, int windup) {
		/**
		 * The kick's own clock: the ticks with a cold wind-up's extra spent holding the chambered pose, so the beats
		 * below always fall where they do in a plain kick.
		 */
		float t() {
			return this.ticks - Mth.clamp(this.ticks - WindKick.WINDUP_TICKS, 0.0F, Math.max(0, this.windup - WindKick.WINDUP_TICKS));
		}

		/** Holding the chambered leg through a cold wind-up. */
		boolean holding() {
			return this.ticks > WindKick.WINDUP_TICKS && this.ticks < this.windup;
		}

		/** The style, from the seed: a spinning back kick (dwit chagi) one time in {@code BACK_KICK_ONE_IN}, else a roundhouse. */
		boolean back() {
			return Math.floorMod(this.seed >>> 2, BACK_KICK_ONE_IN) == 0;
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

	/** One kick in this many is a spinning back kick (dwit chagi); the rest are roundhouses. */
	private static final int BACK_KICK_ONE_IN = 3;
	/**
	 * The roundhouse's whole-body lean, pivoting on the standing foot: the body pivots round so the kicking hip faces
	 * the target ({@code KICK_TURN_*}, degrees: by the end of the chamber, then at the snap), tips hard away from the
	 * kicking leg, a little back, and rises onto the ball of the foot (blocks).
	 */
	private static final float KICK_LEAN = 30.0F, KICK_LEAN_JITTER = 5.0F, KICK_LEAN_BACK = 6.0F;
	private static final float KICK_TURN_CHAMBER = 35.0F, KICK_TURN_APEX = 75.0F, KICK_TURN_JITTER = 8.0F, KICK_RISE = 0.07F;
	/**
	 * The spinning back kick: the body spins away from the target toward the kicking side ({@code BACK_TURN_*}, degrees:
	 * back to the target at the snap) and keeps spinning the same way round to face it again as it recovers. It pitches
	 * forward over the standing leg and tips a little away; the head looks back over the kicking-side shoulder (radians).
	 */
	private static final float BACK_TURN_CHAMBER = 110.0F, BACK_TURN_APEX = 175.0F, BACK_PITCH = 30.0F, BACK_LEAN = 8.0F;
	private static final float BACK_HEAD_TURN = 1.35F;
	/** Where the standing foot is, out from the body's middle (blocks). */
	private static final float KICK_FOOT_OFFSET = 0.12F;
	/**
	 * The kick's beats (ticks): the snap starts at the end of the wind-up and ends at {@code KICK_SNAP_END}; the leg and
	 * the lean hold {@code KICK_LEAN_HOLD} past it, then ease out until the kick ends; the last turn runs over
	 * {@code KICK_TWIST_TICKS}. The leg pulls back to the chamber over {@code KICK_RETRACT_TICKS} before it drops.
	 */
	private static final float KICK_SNAP_END = WindKick.WINDUP_TICKS + WindKick.SNAP_TICKS, KICK_LEAN_HOLD = 2.0F;
	private static final float KICK_RECOVER = KICK_SNAP_END + KICK_LEAN_HOLD, KICK_TWIST_TICKS = 2.5F, KICK_RETRACT_TICKS = 3.0F;
	/**
	 * The roundhouse's bend at the hip: on top of the whole-body lean, everything above the hip (torso, head, arms) leans
	 * this much further away from the kick (degrees, before jitter), pivoting at the hip ({@code KICK_HIP_Y} model units
	 * below the neck). That's what brings the kicking leg up level with the head. The neck turns back a share of it.
	 */
	private static final float KICK_HIP_BEND = 20.0F, KICK_HIP_BEND_JITTER = 4.0F, KICK_HIP_Y = 12.0F, KICK_HIP_HEAD_LEVEL = 0.4F;
	/** Held in the chamber through a cold wind-up, the leg trembles (radians, and how fast). */
	private static final float KICK_HOLD_SHAKE = 0.05F, KICK_HOLD_SHAKE_SPEED = 2.3F;
	/**
	 * Roundhouse kicking leg (model radians, mirrored by side): chambered (knee up in front and out) and at full
	 * extension (straight out to the side, a touch forward; the lean and hip bend lift it to head height).
	 */
	private static final float LEG_CHAMBER_X = -1.45F, LEG_CHAMBER_Z = 0.55F;
	private static final float LEG_APEX_X = -0.35F, LEG_APEX_Z = 1.45F;
	/** Back kick leg: tucked up in front, then driven straight out behind (the forward pitch lifts it). */
	private static final float BACK_CHAMBER_X = -0.95F, BACK_CHAMBER_Z = 0.1F;
	private static final float BACK_APEX_X = 1.55F, BACK_APEX_Z = 0.2F;
	private static final float LIMB_JITTER = 0.15F;
	/**
	 * The guard: both fists up in front of the face, the lead arm (the kicking side's) higher and further out, turned in
	 * toward the chin; in the roundhouse the guard turns {@code GUARD_TRACK} of the way back toward the target.
	 */
	private static final float GUARD_LEAD_X = -1.95F, GUARD_REAR_X = -1.65F, GUARD_IN = 0.55F, GUARD_REAR_IN = 0.5F, GUARD_TRACK = 0.6F;
	/** How much of the lean the standing leg turns back against. */
	private static final float STANDING_LEG_UPRIGHT = 0.3F;
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
		// a flying alien (Xenoform's Pyre, Wyrm, Nadir) cruises the same way
		if (com.jaymospowers.client.power.xenoform.XenoformFlightPose.cruising(player)) {
			return true;
		}
		if (player == minecraft.player) {
			return WindPowerClient.INSTANCE.flight().state().fast() && WindPowerClient.isActive(minecraft.player);
		}
		Integer state = player.getAttached(WindPower.FLIGHT);
		WindFlight flight = state == null ? null : WindFlight.byId(state);
		return flight != null && flight.fast();
	}

	/** How far into the cruise pose this entity is (0-1), between ticks. */
	public static float amount(Entity entity, float partialTicks) {
		float[] amount = AMOUNTS.get(entity.getId());
		return amount == null ? 0.0F : Mth.lerp(partialTicks, amount[0], amount[1]);
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
		state.setData(KICK_KEY, kick >= 0.0F && kick < kicked.length()
			? new KickPose(kick, kicked.left(), kicked.seed(), kicked.windup()) : null);
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
		float in = Mth.clamp(ticks / WindKick.WINDUP_TICKS, 0.0F, 1.0F);
		float out = Mth.clamp((WindKick.KICK_TICKS - ticks) / (WindKick.KICK_TICKS - KICK_RECOVER), 0.0F, 1.0F);
		return smooth(Math.min(in, out));
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	/**
	 * The kick's body turn (degrees; positive turns the body to its left). The roundhouse turns away from the kicking
	 * leg so its hip faces the target, and turns back as the lean eases out. The back kick spins the other way until its
	 * back is to the target, then carries on round to face it again.
	 */
	private static float kickTurn(KickPose kick, float amount) {
		float side = kick.left() ? -1.0F : 1.0F;
		float through = smooth(Mth.clamp((kick.t() - WindKick.WINDUP_TICKS + 0.5F) / KICK_TWIST_TICKS, 0.0F, 1.0F));
		if (!kick.back()) {
			return side * Mth.lerp(through, KICK_TURN_CHAMBER, KICK_TURN_APEX + kick.jitter(1) * KICK_TURN_JITTER) * amount;
		}
		float turn;
		if (kick.t() <= KICK_RECOVER) {
			turn = Mth.lerp(through, BACK_TURN_CHAMBER, BACK_TURN_APEX) * amount;
		} else {
			// spun on round, the same way, to face the target again
			float on = smooth(Mth.clamp((kick.t() - KICK_RECOVER) / (WindKick.KICK_TICKS - KICK_RECOVER), 0.0F, 1.0F));
			turn = Mth.lerp(on, BACK_TURN_APEX, 360.0F);
		}
		return -side * turn;
	}

	/**
	 * The kick's whole-body lean: pivoting on the standing foot, the body turns (see {@link #kickTurn}), and tips: the
	 * roundhouse hard away from the kicking leg and a little back, the back kick forward over the standing leg. Up on
	 * the ball of the foot. Done on the whole model, so the head and arms go with it.
	 */
	private static void kickLean(LivingEntityRenderState state, PoseStack poseStack) {
		KickPose kick = state.getData(KICK_KEY);
		if (kick == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float amount = kickAmount(kick.t());
		// +1: the right leg kicks, so the left foot stands and the body leans left (local +X is the body's right)
		float side = kick.left() ? -1.0F : 1.0F;
		float lean = kick.back() ? BACK_LEAN * amount : (KICK_LEAN + kick.jitter(0) * KICK_LEAN_JITTER) * amount;
		float pitch = kick.back() ? -BACK_PITCH * amount : KICK_LEAN_BACK * amount;
		float footX = -side * KICK_FOOT_OFFSET;
		poseStack.translate(footX, KICK_RISE * amount, 0.0F);
		// (a positive turn about Y turns the body to its left; about Z it tips the top to the right; about X, back)
		poseStack.rotateDegrees(Axis.YP, kickTurn(kick, amount));
		poseStack.rotateDegrees(Axis.ZP, -side * lean);
		poseStack.rotateDegrees(Axis.XP, pitch);
		poseStack.translate(-footX, 0.0F, 0.0F);
	}

	/**
	 * The kick's limbs. The kicking leg chambers through the wind-up, snaps out to full extension (the roundhouse out to
	 * the side at head height, the back kick straight out behind), holds there a moment, pulls back to the chamber and
	 * drops. The standing leg stays straight (against the lean); the fists stay up in a guard by the face.
	 */
	private static void kickLimbs(HumanoidModel<?> h, KickPose kick) {
		float t = kick.t();
		float amount = kickAmount(t);
		boolean back = kick.back();
		float side = kick.left() ? -1.0F : 1.0F;
		ModelPart kicking = kick.left() ? h.leftLeg : h.rightLeg;
		ModelPart standing = kick.left() ? h.rightLeg : h.leftLeg;
		float windup = WindKick.WINDUP_TICKS;
		float j = kick.jitter(2) * LIMB_JITTER;
		float chamberX = back ? BACK_CHAMBER_X : LEG_CHAMBER_X, chamberZ = back ? BACK_CHAMBER_Z : LEG_CHAMBER_Z;
		float apexX = back ? BACK_APEX_X - j : LEG_APEX_X, apexZ = back ? BACK_APEX_Z : LEG_APEX_Z + j;
		float x, z;
		if (t < windup) {
			// chambering: knee up (and out, for the roundhouse)
			float c = smooth(t / windup);
			x = chamberX * c;
			z = chamberZ * c;
		} else if (t < KICK_SNAP_END) {
			// the snap: straight out to full extension
			float u = smooth((t - windup) / (KICK_SNAP_END - windup));
			x = Mth.lerp(u, chamberX, apexX);
			z = Mth.lerp(u, chamberZ, apexZ);
		} else if (t < KICK_RECOVER) {
			// held out at full extension
			x = apexX;
			z = apexZ;
		} else if (t < KICK_RECOVER + KICK_RETRACT_TICKS) {
			// pulled back to the chamber
			float u = smooth((t - KICK_RECOVER) / KICK_RETRACT_TICKS);
			x = Mth.lerp(u, apexX, chamberX);
			z = Mth.lerp(u, apexZ, chamberZ);
		} else {
			// and set down
			float down = smooth(Mth.clamp((t - KICK_RECOVER - KICK_RETRACT_TICKS) / (WindKick.KICK_TICKS - KICK_RECOVER - KICK_RETRACT_TICKS), 0.0F, 1.0F));
			x = Mth.lerp(down, chamberX, 0.0F);
			z = Mth.lerp(down, chamberZ, 0.0F);
		}
		if (kick.holding()) {
			// the cold won't let it go: the chambered leg trembles, waiting
			x += Mth.sin(kick.ticks() * KICK_HOLD_SHAKE_SPEED) * KICK_HOLD_SHAKE;
		}
		pose(kicking, x, 0.0F, side * z, 1.0F);
		// the standing leg straight, a little more upright than the leaning body (the foot stays planted)
		float lean = (back ? BACK_LEAN : KICK_LEAN + kick.jitter(0) * KICK_LEAN_JITTER) * Mth.DEG_TO_RAD;
		pose(standing, back ? -0.15F : 0.12F, 0.0F, -side * lean * STANDING_LEG_UPRIGHT, amount);
		// the head stays on the target: the roundhouse turns it back against the body's turn, the back kick looks over
		// the kicking-side shoulder (a positive head yRot turns it to the right)
		float turn = kickTurn(kick, amount) * Mth.DEG_TO_RAD;
		float look = back ? side * BACK_HEAD_TURN * amount : turn;
		h.head.yRot += look;
		h.head.zRot -= side * lean * 0.5F * amount;
		if (back) {
			// lifted against the forward pitch to keep the eyes up
			h.head.xRot -= BACK_PITCH * Mth.DEG_TO_RAD * 0.5F * amount;
		}
		// the guard: fists up by the face, turned in toward the chin (the right arm turns in with -, the left with +)
		ModelPart lead = kick.left() ? h.leftArm : h.rightArm;
		ModelPart rear = kick.left() ? h.rightArm : h.leftArm;
		float track = back ? 0.0F : turn * GUARD_TRACK;
		float a1 = kick.jitter(3) * LIMB_JITTER, a2 = kick.jitter(4) * LIMB_JITTER;
		pose(lead, GUARD_LEAD_X + a1, -side * GUARD_IN + track, 0.0F, amount);
		pose(rear, GUARD_REAR_X + a2, side * GUARD_REAR_IN + track, 0.0F, amount);
		h.body.yRot = 0.0F;
		if (!back) {
			hipBend(h, side * (KICK_HIP_BEND + kick.jitter(6) * KICK_HIP_BEND_JITTER) * Mth.DEG_TO_RAD * amount);
		}
	}
	/**
	 * Bends everything above the hip sideways by {@code bend} radians (positive: the top goes to the body's left, model
	 * +X), pivoting about the hip: each upper part's pivot swings round the hip and the part tips with it, so the arms
	 * and head stay on the shoulders. The legs stay where they are.
	 */
	private static void hipBend(HumanoidModel<?> h, float bend) {
		if (Math.abs(bend) < 1.0E-4F) {
			return;
		}
		float hipX = h.body.x, hipY = h.body.y + KICK_HIP_Y;
		float cos = Mth.cos(bend), sin = Mth.sin(bend);
		for (ModelPart part : new ModelPart[] {h.body, h.head, h.rightArm, h.leftArm}) {
			float dx = part.x - hipX, dy = part.y - hipY;
			part.x = hipX + dx * cos - dy * sin;
			part.y = hipY + dx * sin + dy * cos;
			// (zRot is applied outermost, so this tips the part, already posed, about its new pivot)
			part.zRot += bend;
		}
		// the neck keeps the eyes nearer level
		h.head.zRot -= bend * KICK_HIP_HEAD_LEVEL;
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
