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
	/** How fast the pose eases in and out, per tick. */
	private static final float EASE_IN = 0.12F;
	private static final float EASE_OUT = 0.18F;

	/** entity id → {previous, current} pose amount. */
	private static final Map<Integer, float[]> AMOUNTS = new HashMap<>();

	private WindPose() {
	}

	static void tick(Minecraft minecraft) {
		WindHoverSway.tick(minecraft);
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
		WindHoverSway.extract(entity, state, partialTicks);
		float[] amount = AMOUNTS.get(entity.getId());
		float value = amount == null ? 0.0F : Mth.lerp(partialTicks, amount[0], amount[1]);
		if (value <= 0.001F || state.isFallFlying || state.isVisuallySwimming) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, value);
		if (value > 0.3F) {
			// no hunched-over crouch while flying (Sneak toggles the cruise lock)
			state.isCrouching = false;
		}
	}

	/** After the body has been turned to face its way: tip it into the flight, pivoting about its middle. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		WindHoverSway.rotate(state, poseStack, entityScale);
		Float amount = state.getData(KEY);
		if (amount == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		// face where the head faces, not where the (lagging) body was turned
		poseStack.rotateDegrees(Axis.YP, -state.yRot * amount);
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotateDegrees(Axis.XP, amount * (-90.0F - state.xRot));
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the model animates: the limbs. */
	public static void applyModel(Model<?> model, Object state) {
		// the hover ragdoll first; the cruise pose, easing in over it, wins as speed picks up
		WindHoverSway.applyModel(model, state);
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
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
	}

	private static void pose(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
