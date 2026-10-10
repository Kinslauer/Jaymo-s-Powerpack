package com.jaymospowers.client.power.wind;

import com.jaymospowers.power.wind.WindCyclone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The Cyclone's stance, a superhero landing (anyone's, from the synced stance): one knee on the ground, the other
 * leg bent forward with the foot planted, the torso hunched forward and the head down, one arm swept up and back
 * over the shoulder and the other hand down by the ground. The whole model is lowered and tipped in the PoseStack
 * (so the head and arms go with the torso); the legs are turned back against the tip so they stay planted.
 */
final class WindCyclonePose {
	static final RenderStateDataKey<Stance> KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_cyclone");

	/** How far into the stance (0-1), which side kneels, and the body's age for the tremble. */
	record Stance(float amount, boolean left, float age) {
	}

	/** The drop is fast (a landing), the rise slower. Per tick. */
	private static final float DROP_IN = 0.34F, RISE_OUT = 0.14F;
	/** How far the body sinks (blocks, model units) and how far the torso tips forward (degrees) about the hips. */
	private static final float DROP = 0.45F, HUNCH = 40.0F;
	/** The hips' height above the feet, and a leg's length (blocks). */
	private static final float HIP = 0.75F, LEG = 0.75F;
	/** The body turned a little toward the raised arm (degrees). */
	private static final float TWIST = 10.0F;
	/** The legs spread a little apart (radians outward). */
	private static final float LEG_SPREAD = 0.1F;
	/** The raised arm, up and back over the shoulder, in the world (radians back from hanging down); outward. */
	private static final float HIGH_ARM_BACK = 2.65F, HIGH_ARM_OUT = 0.3F;
	/** The low arm, reaching down by the ground a little ahead of the body (radians forward of straight down); inward. */
	private static final float LOW_ARM_FORWARD = 0.2F, LOW_ARM_IN = 0.12F;
	/** The head bowed (radians down, beyond the hunch). */
	private static final float HEAD_DOWN = 0.3F;
	/** The arms tremble against the wind. */
	private static final float TREMBLE = 0.05F;

	/** entity id → {previous, current} stance amount. */
	private static final Map<Integer, float[]> AMOUNTS = new HashMap<>();
	/** entity id → which side kneels (kept while the stance eases out). */
	private static final Map<Integer, Boolean> SIDES = new HashMap<>();

	private WindCyclonePose() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			AMOUNTS.clear();
			SIDES.clear();
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			WindCyclone.Stance stance = player.getAttached(WindCyclone.CYCLONE);
			float[] amount = AMOUNTS.get(player.getId());
			if (amount == null) {
				if (stance == null) {
					continue;
				}
				amount = new float[2];
				AMOUNTS.put(player.getId(), amount);
			}
			if (stance != null) {
				SIDES.put(player.getId(), stance.left());
			}
			amount[0] = amount[1];
			amount[1] = stance != null ? Math.min(1.0F, amount[1] + DROP_IN) : Math.max(0.0F, amount[1] - RISE_OUT);
		}
		AMOUNTS.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
		SIDES.keySet().removeIf(id -> !AMOUNTS.containsKey(id));
	}

	/** This frame's stance, or null. */
	static Stance extract(Entity entity, float partialTicks) {
		float[] amount = AMOUNTS.get(entity.getId());
		float value = amount == null ? 0.0F : Mth.lerp(partialTicks, amount[0], amount[1]);
		if (value <= 0.001F) {
			return null;
		}
		// eased: a hard drop that settles
		float eased = value * value * (3.0F - 2.0F * value);
		return new Stance(eased, SIDES.getOrDefault(entity.getId(), false), entity.tickCount + partialTicks);
	}

	/** The leg's angle from straight down that puts its end on the ground with the hips lowered by the drop. */
	private static float legAngle(float amount) {
		float hip = HIP - DROP * amount;
		return (float)Math.acos(Mth.clamp(hip / LEG, -1.0F, 1.0F));
	}

	/** Lowered so the knee meets the ground, and the torso tipped forward about the hips. */
	static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		Stance stance = state.getData(KEY);
		if (stance == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float a = stance.amount();
		// +1: the right knee is down and the left arm raised
		float side = stance.left() ? -1.0F : 1.0F;
		poseStack.translate(0.0F, -DROP * a, 0.0F);
		// (a positive turn about Y turns the body to its left: toward the raised left arm)
		poseStack.rotateDegrees(Axis.YP, side * TWIST * a);
		poseStack.translate(0.0F, HIP, 0.0F);
		// (a negative turn about X tips the top forward)
		poseStack.rotateDegrees(Axis.XP, -HUNCH * a);
		poseStack.translate(0.0F, -HIP, 0.0F);
	}

	/** The limbs, in the tipped body's frame. */
	static void applyModel(HumanoidModel<?> h, Stance stance) {
		float a = stance.amount();
		float hunch = HUNCH * Mth.DEG_TO_RAD * a;
		float legs = legAngle(a);
		boolean left = stance.left();
		ModelPart kneeling = left ? h.leftLeg : h.rightLeg;
		ModelPart planted = left ? h.rightLeg : h.leftLeg;
		ModelPart lowArm = left ? h.leftArm : h.rightArm;
		ModelPart highArm = left ? h.rightArm : h.leftArm;
		// outward zRot: the right limbs swing out with +, the left with -
		float kneelOut = left ? -1.0F : 1.0F;
		float plantOut = -kneelOut;
		float tremble = Mth.sin(stance.age() * 1.9F) * TREMBLE;
		// (a leg's xRot: + swings it back, - forward; the body's tip already swings them back by the hunch)
		pose(kneeling, legs - hunch, 0.0F, kneelOut * LEG_SPREAD, a);
		pose(planted, -(legs + hunch), 0.0F, plantOut * LEG_SPREAD, a);
		// the high arm up and back over the shoulder (the hunch already turns it forward)
		pose(highArm, HIGH_ARM_BACK - hunch + tremble, 0.0F, plantOut * HIGH_ARM_OUT + tremble, a);
		// the low hand down by the ground, a little ahead
		pose(lowArm, -(hunch + LOW_ARM_FORWARD) - tremble * 0.5F, 0.0F, -kneelOut * LOW_ARM_IN, a);
		pose(h.head, HEAD_DOWN, 0.0F, 0.0F, a);
		h.body.xRot = Mth.lerp(a, h.body.xRot, 0.0F);
		h.body.yRot = Mth.lerp(a, h.body.yRot, 0.0F);
	}

	private static void pose(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
