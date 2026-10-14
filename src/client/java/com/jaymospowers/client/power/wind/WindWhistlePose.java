package com.jaymospowers.client.power.wind;

import com.jaymospowers.power.wind.WindJet;
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
 * The Wind Whistle's pose ({@link WindJet}, anyone's, from the synced {@link WindJet#JETTING}): leaned over into it from
 * the hips, blowing hard along where they look, feet braced one ahead of the other, and both arms flung out behind
 * them by the force of their own wind, fluttering in it. The body is tipped in the PoseStack; the legs and arms are
 * turned back against the tip so they keep to the world.
 */
final class WindWhistlePose {
	static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_whistle");

	/** Leaning in and straightening up again (per tick). */
	private static final float LEAN_IN = 0.2F, LEAN_OUT = 0.12F;
	/** How far over they lean from the hips (degrees), and how far they sink into the brace (blocks). */
	private static final float LEAN = 32.0F, SINK = 0.06F;
	/** The hips' height above the feet (blocks). */
	private static final float HIP = 0.75F;
	/** The legs, as the world sees them (radians, + back): the front foot ahead, the back one braced behind; outward. */
	private static final float FRONT_LEG = -0.35F, BACK_LEG = 0.5F, LEG_SPREAD = 0.08F;
	/** The arms, as the world sees them: flung out behind (radians back from hanging down), and out from the sides. */
	private static final float ARMS_BACK = 1.35F, ARMS_OUT = 0.4F;
	/** How hard they flutter in the wind, and how fast. */
	private static final float FLUTTER = 0.09F, FLUTTER_RATE = 1.7F;
	/** The chin pushed up a little to blow (radians). */
	private static final float CHIN = 0.12F;

	/** entity id → {previous, current} pose amount. */
	private static final Map<Integer, float[]> AMOUNTS = new HashMap<>();

	private WindWhistlePose() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			AMOUNTS.clear();
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			boolean on = WindJet.isActive(player);
			float[] amount = AMOUNTS.get(player.getId());
			if (amount == null) {
				if (!on) {
					continue;
				}
				amount = new float[2];
				AMOUNTS.put(player.getId(), amount);
			}
			amount[0] = amount[1];
			amount[1] = on ? Math.min(1.0F, amount[1] + LEAN_IN) : Math.max(0.0F, amount[1] - LEAN_OUT);
		}
		AMOUNTS.entrySet().removeIf(entry -> entry.getValue()[0] <= 0.0F && entry.getValue()[1] <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	/** This frame's pose amount (eased), or null. */
	static Float extract(Entity entity, float partialTicks) {
		float[] amount = AMOUNTS.get(entity.getId());
		float value = amount == null ? 0.0F : Mth.lerp(partialTicks, amount[0], amount[1]);
		return value <= 0.001F ? null : value * value * (3.0F - 2.0F * value);
	}

	/** Sunk into the brace and leaned over from the hips. */
	static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		Float a = state.getData(KEY);
		if (a == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		poseStack.translate(0.0F, -SINK * a, 0.0F);
		poseStack.translate(0.0F, HIP, 0.0F);
		// (a negative turn about X tips the top forward)
		poseStack.rotateDegrees(Axis.XP, -LEAN * a);
		poseStack.translate(0.0F, -HIP, 0.0F);
	}

	/** The limbs, in the leaned body's frame. */
	static void applyModel(HumanoidModel<?> h, AvatarRenderState state, float a) {
		float lean = LEAN * Mth.DEG_TO_RAD * a;
		float age = state.ageInTicks;
		// the head keeps looking where they blow, chin up
		h.head.xRot -= lean + CHIN * a;
		// (a limb's xRot: + swings it back; the lean already swings them back by as much, so it's taken off)
		boolean rightAhead = state.mainArm != net.minecraft.world.entity.HumanoidArm.LEFT;
		pose(rightAhead ? h.rightLeg : h.leftLeg, FRONT_LEG - lean, rightAhead ? LEG_SPREAD : -LEG_SPREAD, a);
		pose(rightAhead ? h.leftLeg : h.rightLeg, BACK_LEG - lean, rightAhead ? -LEG_SPREAD : LEG_SPREAD, a);
		float flutterR = Mth.sin(age * FLUTTER_RATE) * FLUTTER, flutterL = Mth.sin(age * FLUTTER_RATE + 2.1F) * FLUTTER;
		pose(h.rightArm, ARMS_BACK - lean + flutterR, ARMS_OUT + flutterL * 0.5F, a);
		pose(h.leftArm, ARMS_BACK - lean + flutterL, -ARMS_OUT - flutterR * 0.5F, a);
		h.body.xRot = Mth.lerp(a, h.body.xRot, 0.0F);
	}

	private static void pose(ModelPart part, float x, float z, float a) {
		part.xRot = Mth.lerp(a, part.xRot, x);
		part.yRot = Mth.lerp(a, part.yRot, 0.0F);
		part.zRot = Mth.lerp(a, part.zRot, z);
	}
}
