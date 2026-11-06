package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.corpse.Reanimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.5.84) Andy: "zombies should have their own reanimation animation too". The risen dead's way up
 * ({@link Reanimation}), unlike a living body's: it lies on its back; the arms jerk up stiff first, reaching; then the
 * whole body tips up from the heels like a plank, in fits and starts, never bending at the waist; at the last it
 * lurches forward past upright and settles, the head lolling. Drawn from the synced start time, so everyone sees the
 * same rise.
 */
public final class ReanimatePose {
	/** 0 lying to 1 risen. */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:reanimate");

	/** The share of the rise spent lying with only the arms coming up, and where the plank tip ends. */
	static final float ARMS_SHARE = 0.3F, TIP_END = 0.85F;
	/** Fits: how many stalls on the way up, and how far each one jerks (degrees). */
	static final float FITS = 4.0F, FIT_JERK = 4.0F;
	/** The lurch past upright at the end (degrees forward), and the body lifted off the ground lying (blocks). */
	static final float LURCH = 12.0F, LIFT = 0.12F;
	/** The head lolling (radians) and the arms held up and out stiff (radians, - is up/forward). */
	static final float LOLL = 0.35F, ARMS_UP = -1.7F, ARMS_SPREAD = 0.15F;

	private ReanimatePose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Long since = entity.getAttached(Reanimation.RISING);
		if (since == null) {
			state.setData(KEY, null);
			return;
		}
		float t = (entity.level().getGameTime() - since + partialTicks) / Reanimation.TICKS;
		state.setData(KEY, t >= 1.0F || t < 0.0F ? null : t);
	}

	/** How far the body is tipped up from lying on its back (degrees, 90 standing), with the lurch past it. */
	static float upDegrees(float t) {
		if (t <= ARMS_SHARE) {
			return 0.0F;
		}
		if (t <= TIP_END) {
			float k = (t - ARMS_SHARE) / (TIP_END - ARMS_SHARE);
			// in fits: each stall holds, then jerks on
			float steps = k * FITS;
			float step = (float) Math.floor(steps);
			float into = steps - step;
			float jerked = (step + Mth.clamp(into * 2.5F, 0.0F, 1.0F)) / FITS;
			return 90.0F * jerked;
		}
		float k = (t - TIP_END) / (1.0F - TIP_END);
		return 90.0F + LURCH * Mth.sin(k * Mth.PI);
	}

	/** After the body's been turned to face its way: laid on its back and tipped up from the heels. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		Float t = state.getData(KEY);
		if (t == null) {
			return;
		}
		float up = upDegrees(t);
		float lying = 1.0F - Mth.clamp(up / 90.0F, 0.0F, 1.0F);
		poseStack.translate(0.0F, LIFT * lying, 0.0F);
		// (about the feet: the origin here is where they stand) the twitch while it lies there
		float twitch = t <= ARMS_SHARE ? Mth.sin(state.ageInTicks * 2.1F) * 1.5F : 0.0F;
		poseStack.rotate(Axis.XP.rotationDegrees(90.0F - up + twitch));
	}

	/** After the model animates: the arms up stiff (jerking up first), the legs straight, the head lolling. */
	public static void apply(Model<?> model, Object state) {
		if (!(model instanceof HumanoidModel<?> humanoid) || !(state instanceof LivingEntityRenderState living)) {
			return;
		}
		Float t = living.getData(KEY);
		if (t == null) {
			return;
		}
		// the arms come up in the first part, in two jerks, then stay up and reaching
		float arms = t <= ARMS_SHARE ? (t < ARMS_SHARE * 0.5F ? 0.4F : 1.0F) * Mth.clamp(t / (ARMS_SHARE * 0.35F), 0.0F, 1.0F) : 1.0F;
		float shake = Mth.sin(living.ageInTicks * 1.7F) * 0.05F;
		humanoid.rightArm.xRot = ARMS_UP * arms + shake;
		humanoid.leftArm.xRot = ARMS_UP * arms - shake;
		humanoid.rightArm.yRot = 0.0F;
		humanoid.leftArm.yRot = 0.0F;
		humanoid.rightArm.zRot = ARMS_SPREAD * arms;
		humanoid.leftArm.zRot = -ARMS_SPREAD * arms;
		// stiff as a board: no stride
		humanoid.rightLeg.xRot = 0.0F;
		humanoid.leftLeg.xRot = 0.0F;
		humanoid.rightLeg.yRot = 0.0F;
		humanoid.leftLeg.yRot = 0.0F;
		humanoid.body.xRot = 0.0F;
		// the head lolls side to side, and hangs back while it lies
		float loll = Mth.sin(living.ageInTicks * 0.25F) * LOLL;
		humanoid.head.zRot = loll;
		humanoid.head.yRot = 0.0F;
		humanoid.head.xRot = t <= TIP_END ? 0.3F : 0.5F * Mth.sin((t - TIP_END) / (1.0F - TIP_END) * Mth.PI);
	}
}
