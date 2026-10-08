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
 * Losing control at speed, drawn the ragdoll way (like Jaymo's Kitchen's corpses): while tumbling the body flips end
 * over end about its middle with the limbs flailing each on its own beat, the spin slowing as it ends; on regaining
 * control it snaps into the recovery pose (torso pitched forward, head up, both arms swept back, one knee driven up
 * and the other leg stretched out behind) for a moment before the hover takes over. Works for every player in view.
 */
public final class WindTumble {
	public static final RenderStateDataKey<Tumble> KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_tumble");

	/** End-over-end spin, degrees per tick at the start of the tumble (it slows to a third by the end). */
	private static final float SPIN_SPEED = 30.0F;
	/** How far the recovery pose pitches the body forward (degrees). */
	private static final float RECOVER_LEAN = 35.0F;

	/** This frame: {@code tumble} 0-1 with its spin (degrees) and age (ticks); {@code recover} 0-1. */
	public record Tumble(float tumble, float spin, float age, float recover) {
	}

	private static final class Body {
		boolean tumbling;
		int age;
		float spin, prevSpin;
		int recover;
		float tumbleAmount, prevTumbleAmount;
	}

	private static final Map<Integer, Body> BODIES = new HashMap<>();

	private WindTumble() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			BODIES.clear();
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			boolean tumbling = isTumbling(minecraft, player);
			Body body = BODIES.get(player.getId());
			if (body == null) {
				if (!tumbling) {
					continue;
				}
				body = new Body();
				BODIES.put(player.getId(), body);
			}
			body.prevSpin = body.spin;
			body.prevTumbleAmount = body.tumbleAmount;
			if (tumbling) {
				if (!body.tumbling) {
					body.age = 0;
					body.recover = 0;
				}
				body.age++;
				float slow = 1.0F - 0.66F * Math.min(1.0F, body.age / (float)WindFlightController.TUMBLE_TICKS);
				body.spin += SPIN_SPEED * slow;
				body.tumbleAmount = Math.min(1.0F, body.tumbleAmount + 0.5F);
			} else {
				if (body.tumbling && isFlying(minecraft, player)) {
					// caught themselves: into the recovery pose
					body.recover = WindFlightController.RECOVER_TICKS;
				} else if (body.recover > 0) {
					body.recover--;
				}
				body.tumbleAmount = Math.max(0.0F, body.tumbleAmount - 0.35F);
			}
			body.tumbling = tumbling;
		}
		BODIES.entrySet().removeIf(entry -> !entry.getValue().tumbling && entry.getValue().recover <= 0
			&& entry.getValue().tumbleAmount <= 0.0F && entry.getValue().prevTumbleAmount <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	private static boolean isTumbling(Minecraft minecraft, Player player) {
		if (player == minecraft.player) {
			return WindPowerClient.isActive(minecraft.player) && WindPowerClient.INSTANCE.flight().state() == WindFlight.TUMBLE;
		}
		Integer state = player.getAttached(WindPower.FLIGHT);
		return state != null && WindFlight.byId(state) == WindFlight.TUMBLE;
	}

	private static boolean isFlying(Minecraft minecraft, Player player) {
		if (player == minecraft.player) {
			return WindPowerClient.INSTANCE.flight().state().flying();
		}
		Integer state = player.getAttached(WindPower.FLIGHT);
		WindFlight flight = state == null ? null : WindFlight.byId(state);
		return flight != null && flight.flying();
	}

	static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		Body body = BODIES.get(entity.getId());
		if (body == null || state.isFallFlying || state.isVisuallySwimming) {
			state.setData(KEY, null);
			return;
		}
		float tumble = Mth.lerp(partialTicks, body.prevTumbleAmount, body.tumbleAmount);
		// the recovery pose snaps in, holds, then eases out over its last third
		float recover = body.recover <= 0 ? 0.0F
			: Math.min(1.0F, (body.recover - partialTicks) / (WindFlightController.RECOVER_TICKS / 3.0F));
		if (tumble <= 0.001F && recover <= 0.001F) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Tumble(tumble, Mth.lerp(partialTicks, body.prevSpin, body.spin), body.age + partialTicks, Math.max(0.0F, recover)));
		state.walkAnimationSpeed *= 1.0F - Math.max(tumble, recover);
		state.isCrouching = false;
	}

	/** After the body has been turned to face its way: the flip, or the forward lean of the recovery. */
	static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Tumble tumble = state.getData(KEY);
		if (tumble == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		poseStack.translate(0.0F, middle, 0.0F);
		if (tumble.tumble() > 0.001F) {
			// head over heels, wobbling side to side as it goes
			poseStack.rotateDegrees(Axis.XP, -tumble.spin() * tumble.tumble());
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(tumble.age() * 0.7F) * 25.0F * tumble.tumble());
		}
		if (tumble.recover() > 0.001F) {
			poseStack.rotateDegrees(Axis.XP, -RECOVER_LEAN * tumble.recover());
		}
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the model animates: flailing limbs, or the recovery pose. */
	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		Tumble tumble = avatar.getData(KEY);
		if (tumble == null) {
			return;
		}
		float t = tumble.tumble();
		if (t > 0.001F) {
			float age = tumble.age();
			// each limb flails on its own beat (as a jolted corpse does), flung outward by the spin
			set(humanoid.rightArm, Mth.sin(age * 1.3F) * 1.6F - 0.6F, 0.0F, 1.1F + Mth.cos(age * 0.9F) * 0.5F, t);
			set(humanoid.leftArm, Mth.sin(age * 1.1F + 2.0F) * 1.6F - 0.6F, 0.0F, -1.1F - Mth.cos(age * 1.2F) * 0.5F, t);
			set(humanoid.rightLeg, Mth.sin(age * 0.9F + 1.0F) * 1.1F, 0.0F, 0.35F + Mth.sin(age * 1.4F) * 0.25F, t);
			set(humanoid.leftLeg, Mth.sin(age * 1.0F + 3.0F) * 1.1F, 0.0F, -0.35F - Mth.sin(age * 1.6F) * 0.25F, t);
			set(humanoid.head, Mth.sin(age * 1.5F) * 0.6F, Mth.cos(age * 1.1F) * 0.4F, 0.0F, t);
		}
		float r = tumble.recover();
		if (r > 0.001F) {
			// head up to see ahead past the forward lean
			set(humanoid.head, -0.55F, humanoid.head.yRot, 0.0F, r);
			// both arms swept back and a little out
			set(humanoid.rightArm, 0.95F, 0.0F, 0.3F, r);
			set(humanoid.leftArm, 0.8F, 0.0F, -0.3F, r);
			// one knee driven up and forward, the other leg stretched out behind
			set(humanoid.rightLeg, -1.15F, 0.0F, 0.06F, r);
			set(humanoid.leftLeg, 0.55F, 0.0F, -0.06F, r);
		}
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
