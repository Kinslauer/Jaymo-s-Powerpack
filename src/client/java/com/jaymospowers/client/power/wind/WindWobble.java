package com.jaymospowers.client.power.wind;

import com.jaymospowers.client.PosedCopy;
import com.jaymospowers.power.wind.WindModes;
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

/**
 * (0.15.54) A Wind user who hasn't mastered the cloak yet drifts down on it unsteadily. Andy (2026-10-21): "wind users
 * early game when using wind cloak to slow their descent, wobble around unsteadily until 2 mastery (ragdoll wobble of
 * the arms and legs)". While {@link WindModes#WOBBLY} is on them the arms windmill out to the sides for balance and the
 * legs kick and dangle, each limb on its own uneven beat (like the tumble's flailing, but held up and gentler), and the
 * body rocks a little side to side and back and forth. Eases in and out; drawn for every player in view.
 */
public final class WindWobble {
	public static final RenderStateDataKey<Wobble> KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_wobble");

	/** How fast the wobble eases in and out, per tick. */
	private static final float EASE = 0.15F;
	/** How far the body rocks (degrees): side to side, and back and forth. */
	private static final float ROCK_ROLL = 9.0F, ROCK_PITCH = 6.0F;
	/** The arms: held out to the sides this far (radians), windmilling this much about it. */
	private static final float ARM_OUT = 1.15F, ARM_SWING = 0.9F, ARM_FLAP = 0.35F;
	/** The legs: kicking this much (radians), and spread this far. */
	private static final float LEG_KICK = 0.7F, LEG_SPREAD = 0.2F;
	/** The body hangs from about its middle (fraction of its height). */
	private static final float PIVOT = 0.5F;

	/** This frame's wobble: how much (0-1), and its clock (ticks). */
	public record Wobble(float amount, float age) {
	}

	private static final class Body {
		float amount, prevAmount;
		int age;
	}

	private static final Map<Integer, Body> BODIES = new HashMap<>();

	private WindWobble() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			BODIES.clear();
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			boolean wobbly = player.getAttached(WindModes.WOBBLY) != null && !player.onGround();
			Body body = BODIES.get(player.getId());
			if (body == null) {
				if (!wobbly) {
					continue;
				}
				body = new Body();
				BODIES.put(player.getId(), body);
			}
			body.prevAmount = body.amount;
			body.amount = wobbly ? Math.min(1.0F, body.amount + EASE) : Math.max(0.0F, body.amount - EASE * 2.0F);
			body.age++;
		}
		BODIES.entrySet().removeIf(entry -> entry.getValue().amount <= 0.0F && entry.getValue().prevAmount <= 0.0F
			|| level.getEntity(entry.getKey()) == null);
	}

	static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		Body body = BODIES.get(entity.getId());
		if (body == null || state.isFallFlying || state.isVisuallySwimming) {
			state.setData(KEY, null);
			return;
		}
		float amount = Mth.lerp(partialTicks, body.prevAmount, body.amount);
		if (amount <= 0.001F) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Wobble(amount, body.age + partialTicks));
		state.walkAnimationSpeed *= 1.0F - amount;
	}

	/** After the body has been turned to face its way: the rocking, about its middle. */
	static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Wobble wobble = state.getData(KEY);
		if (wobble == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float t = wobble.age(), a = wobble.amount();
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * PIVOT;
		poseStack.translate(0.0F, middle, 0.0F);
		// two beats that never line up, so it never settles into a loop
		poseStack.rotateDegrees(Axis.ZP, (Mth.sin(t * 0.23F) * 0.7F + Mth.sin(t * 0.61F + 1.3F) * 0.3F) * ROCK_ROLL * a);
		poseStack.rotateDegrees(Axis.XP, (Mth.sin(t * 0.17F + 0.7F) * 0.6F + Mth.sin(t * 0.47F) * 0.4F) * ROCK_PITCH * a);
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the model animates: windmilling arms and kicking legs. */
	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid) || PosedCopy.of(humanoid)) {
			// (a model that copied the posed body has it already: MCA's layers)
			return;
		}
		Wobble wobble = avatar.getData(KEY);
		if (wobble == null) {
			return;
		}
		float t = wobble.age(), a = wobble.amount();
		// arms out for balance, windmilling each on its own beat
		set(humanoid.rightArm, Mth.sin(t * 0.42F) * ARM_SWING - 0.3F, 0.0F, ARM_OUT + Mth.sin(t * 0.67F + 0.9F) * ARM_FLAP, a);
		set(humanoid.leftArm, Mth.sin(t * 0.37F + 2.1F) * ARM_SWING - 0.3F, 0.0F, -ARM_OUT - Mth.sin(t * 0.59F + 2.6F) * ARM_FLAP, a);
		// legs kicking about, a little apart
		set(humanoid.rightLeg, Mth.sin(t * 0.33F + 1.0F) * LEG_KICK, 0.0F, LEG_SPREAD + Mth.sin(t * 0.51F) * 0.12F, a);
		set(humanoid.leftLeg, Mth.sin(t * 0.29F + 3.4F) * LEG_KICK, 0.0F, -LEG_SPREAD - Mth.sin(t * 0.55F + 1.7F) * 0.12F, a);
		// the head bobs, looking for its balance
		humanoid.head.zRot += Mth.sin(t * 0.31F + 0.4F) * 0.15F * a;
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
