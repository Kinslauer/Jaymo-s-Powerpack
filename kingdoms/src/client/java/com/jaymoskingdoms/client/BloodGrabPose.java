package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BloodGrab;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.615) The grab-drink ({@link BloodGrab}): a vampire holding their prey up by the throat in one hand, the main arm
 * out in front and raised a little, leaning back against the weight, the hand shaking as they drink. Laid over the
 * player's model right after it's posed, like {@link BioAbsorbPose}.
 * <p>
 * (0.7.635) Andy: "the grab should animate within the vampire's hand hands at the throat the whole time, even if they go
 * down". The arm follows the look up and down ({@link BloodGrab#raise}, the same angle the server holds the body at), so
 * the hand stays on the throat, First-person Model's view included. And the held one is posed too ({@link #victim}):
 * hanging from the hand, head pushed back, both hands clawing at the wrist and legs kicking while it can; once down,
 * limp, its arms and legs swinging loose (Powers no longer lays it on the ground while it's held).
 */
public final class BloodGrabPose {
	/** How long the arm takes to come up (ticks), the lean back, the shake. ((0.7.635) How far it's raised: {@link BloodGrab#raise}.) */
	static final float REACH_TICKS = 4.0F, LEAN = 0.08F, SHAKE = 0.035F;
	/** The arm turns this far in toward the middle (radians), so the hand is before the chest. */
	static final float INWARD = 0.2F;

	/** This frame's {weight, age} for a vampire holding someone. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:blood_grab");

	/**
	 * (0.7.635) The held one: hands up at the wrist (radians up from hanging, turned in), the struggle's pace (radians a
	 * tick) and how far the arms tug and the legs kick (radians), the head pushed back; once down, how far its limbs swing.
	 */
	static final float CLAW = 2.2F, CLAW_IN = 0.45F, STRUGGLE_RATE = 0.55F, TUG = 0.18F, KICK = 0.45F, HEAD_BACK = 0.45F, LIMP_SWING = 0.12F;

	/** (0.7.635) This frame's {limp (0 struggling, 1 down, 2 out cold), age, id} for someone held up by the throat. */
	static final RenderStateDataKey<float[]> VICTIM = RenderStateDataKey.create(() -> "jaymoskingdoms:blood_grab_victim");

	private static final Map<Integer, Float> STARTED = new HashMap<>();

	private BloodGrabPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Avatar)) {
			victim(entity, state);
			return;
		}
		if (!entity.hasAttached(BloodGrab.GRABBING)) {
			STARTED.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		float started = STARTED.computeIfAbsent(entity.getId(), k -> state.ageInTicks);
		float w = Mth.clamp((state.ageInTicks - started) / REACH_TICKS, 0.0F, 1.0F);
		state.setData(KEY, new float[] {w * w * (3.0F - 2.0F * w), state.ageInTicks});
	}

	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] held = living.getData(VICTIM);
		if (held != null) {
			victim(m, held);
			return;
		}
		float[] pose = living.getData(KEY);
		if (pose == null) {
			return;
		}
		boolean left = state instanceof HumanoidRenderState h && h.mainArm == HumanoidArm.LEFT;
		ModelPart arm = left ? m.leftArm : m.rightArm;
		float w = pose[0];
		float shake = Mth.sin(pose[1] * 2.1F) * SHAKE;
		// (0.7.635) raised with the look, as the server holds the body (BloodGrab.raise)
		arm.xRot = Mth.lerp(w, arm.xRot, (float)(-Math.PI / 2.0) - BloodGrab.raise(living.xRot) + shake);
		arm.yRot = Mth.lerp(w, arm.yRot, left ? INWARD : -INWARD);
		arm.zRot = Mth.lerp(w, arm.zRot, 0.0F);
		m.body.xRot = Mth.lerp(w, m.body.xRot, -LEAN);
	}

	/** (0.7.635) Marks someone a vampire holds up by the throat (and stills the walk the hold's carrying would swing). */
	private static void victim(LivingEntity entity, LivingEntityRenderState state) {
		if (!BloodGrab.grabbed(entity)) {
			state.setData(VICTIM, null);
			return;
		}
		float limp = com.jaymoskingdoms.PowersCompat.downedState(entity);
		state.setData(VICTIM, new float[] {limp, state.ageInTicks, entity.getId()});
		state.walkAnimationSpeed = 0.0F;
	}

	/** (0.7.635) The held one: clawing at the wrist and kicking, or hanging limp once down. */
	private static void victim(HumanoidModel<?> m, float[] held) {
		float limp = held[0], age = held[1], seed = held[2] * 1.7F;
		if (limp <= 0.0F) {
			// struggling: both hands up at the wrist, tugging out of step; legs kicking; head pushed back by the grip
			float t = age * STRUGGLE_RATE + seed;
			m.rightArm.xRot = -CLAW + Mth.sin(t) * TUG;
			m.leftArm.xRot = -CLAW + Mth.sin(t + 2.1F) * TUG;
			m.rightArm.yRot = -CLAW_IN;
			m.leftArm.yRot = CLAW_IN;
			m.rightArm.zRot = 0.0F;
			m.leftArm.zRot = 0.0F;
			m.rightLeg.xRot = Mth.sin(t * 1.3F) * KICK;
			m.leftLeg.xRot = Mth.sin(t * 1.3F + (float)Math.PI) * KICK;
			m.rightLeg.yRot = 0.0F;
			m.leftLeg.yRot = 0.0F;
			m.head.xRot = -HEAD_BACK;
		} else {
			// down: hanging from the hand, everything loose and swaying with the hold; out cold, stiller still
			float swing = LIMP_SWING * (limp >= 2.0F ? 0.5F : 1.0F);
			float t = age * 0.12F + seed;
			m.rightArm.xRot = Mth.sin(t) * swing;
			m.leftArm.xRot = Mth.sin(t + 0.7F) * swing;
			m.rightArm.yRot = 0.0F;
			m.leftArm.yRot = 0.0F;
			m.rightArm.zRot = 0.08F;
			m.leftArm.zRot = -0.08F;
			m.rightLeg.xRot = Mth.sin(t + 1.4F) * swing * 0.6F;
			m.leftLeg.xRot = Mth.sin(t + 2.0F) * swing * 0.6F;
			m.rightLeg.yRot = 0.0F;
			m.leftLeg.yRot = 0.0F;
			m.rightLeg.zRot = 0.04F;
			m.leftLeg.zRot = -0.04F;
			m.head.xRot = -HEAD_BACK * 0.6F;
			m.head.zRot = 0.25F;
		}
		m.head.yRot = 0.0F;
		m.body.xRot = 0.0F;
		m.body.yRot = 0.0F;
		m.body.zRot = 0.0F;
	}
}
