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

	/** A Tempest Kick under way: how far through it (0-1). */
	public static final RenderStateDataKey<Float> KICK_KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_kick");

	/** entity id → {previous heading, heading, previous bank, bank, last x, last z}. */
	private static final Map<Integer, double[]> PATHS = new HashMap<>();

	private WindPose() {
	}

	static void tick(Minecraft minecraft) {
		WindHoverSway.tick(minecraft);
		WindTumble.tick(minecraft);
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
		Long kicked = entity.getAttached(com.jaymospowers.power.wind.WindKick.KICKING);
		float kick = kicked == null ? -1.0F : (entity.level().getGameTime() - kicked + partialTicks) / com.jaymospowers.power.wind.WindKick.KICK_TICKS;
		state.setData(KICK_KEY, kick >= 0.0F && kick < 1.0F ? kick : null);
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
		Float amount = state.getData(KEY);
		if (amount == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		// the air brake pulls the body up out of the dive, leaning back into the gust
		Float brake = state.getData(BRAKE_KEY);
		float b = brake == null ? 0.0F : brake;
		// face along the path it's actually taking (a quick look aside doesn't swing the body round), else where the head faces
		float[] bank = state.getData(BANK_KEY);
		float facing = bank == null ? state.yRot : Mth.wrapDegrees(bank[0] - state.bodyRot);
		poseStack.rotateDegrees(Axis.YP, -facing * amount);
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotateDegrees(Axis.XP, amount * (1.0F - 0.75F * b) * (-90.0F - state.xRot) + b * BRAKE_LEAN_BACK);
		if (bank != null) {
			// rolled into the turn, about the body's own length
			poseStack.rotateDegrees(Axis.YP, bank[1] * amount * (1.0F - b));
		}
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the model animates: the limbs. */
	public static void applyModel(Model<?> model, Object state) {
		// the hover ragdoll first; the cruise pose, easing in over it, wins as speed picks up
		WindHoverSway.applyModel(model, state);
		WindTumble.applyModel(model, state);
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		Float kick = avatar.getData(KICK_KEY);
		if (kick != null) {
			// the roundhouse: the leg comes up out to the side and sweeps round in front, the body twisting with it,
			// arms flung out for balance
			float up = Math.min(1.0F, kick * 3.0F) * (kick > 0.75F ? (1.0F - kick) / 0.25F : 1.0F);
			float sweep = Mth.clamp((kick - 0.2F) / 0.5F, 0.0F, 1.0F);
			pose(humanoid.rightLeg, -1.35F, Mth.lerp(sweep, 0.9F, -0.7F), -0.9F, up);
			pose(humanoid.leftLeg, 0.15F, 0.0F, 0.1F, up);
			pose(humanoid.rightArm, -0.3F, 0.0F, 1.3F, up);
			pose(humanoid.leftArm, -0.3F, 0.0F, -1.3F, up);
			humanoid.body.yRot = Mth.lerp(sweep, 0.5F, -0.6F) * up;
			return;
		}
		Float amount = avatar.getData(KEY);
		if (amount == null) {
			return;
		}
		float flutter = Mth.sin(avatar.ageInTicks * 0.9F) * 0.05F;
		// head up to look along the flight
		pose(humanoid.head, -1.15F, 0.0F, 0.0F, amount);
		ModelPart lead = avatar.mainArm == HumanoidArm.LEFT ? humanoid.leftArm : humanoid.rightArm;
		ModelPart trail = lead == humanoid.rightArm ? humanoid.leftArm : humanoid.rightArm;
		float outward = trail == humanoid.rightArm ? 1.0F : -1.0F;
		// fist out ahead
		pose(lead, -Mth.PI + 0.12F + flutter, 0.0F, -outward * 0.06F, amount);
		// the other arm swept back along the side
		pose(trail, 0.15F - flutter, 0.0F, outward * 0.18F, amount);
		// legs together, trailing, rippling slightly in the wind
		pose(humanoid.rightLeg, 0.06F + flutter, 0.0F, 0.04F, amount);
		pose(humanoid.leftLeg, 0.06F - flutter, 0.0F, -0.04F, amount);
		Float brake = avatar.getData(BRAKE_KEY);
		if (brake != null) {
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
