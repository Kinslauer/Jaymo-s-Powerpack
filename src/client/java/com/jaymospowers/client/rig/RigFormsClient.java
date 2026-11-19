package com.jaymospowers.client.rig;

import com.jaymospowers.rig.RigMoves;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Client poses for the Rig's arm forms ({@link com.jaymospowers.rig.RigForms}): plates slide, lock and fold on the
 * wearer's arm. Called from {@link RigPose#applyModel} with the move's kind and ticks into it.
 */
public final class RigFormsClient {
	/** Blade: how far the blade slides out (radians), and how long the wind-up takes (ticks). */
	static final float BLADE_OUT = 0.9F;
	static final int BLADE_WIND = 2;
	/** Mace: how far the arm swells down (radians), the smash's hold (ticks). */
	static final float MACE_SWELL = 0.5F;
	static final int MACE_HOLD = 3;
	/** Pile: how far the spike punches out (radians), the hold (ticks). */
	static final float PILE_OUT = 1.1F;
	static final int PILE_HOLD = 2;
	/** Drill: how far the drill spins out (radians). */
	static final float DRILL_SPIN = 0.7F;
	/** Lance: how far the lance levels out (radians). */
	static final float LANCE_LEVEL = 0.6F;
	/** Lash: how far the whip cracks out (radians). */
	static final float LASH_CRACK = 0.8F;
	/** Mimic: how long the glow lasts (ticks). */
	static final int MIMIC_GLOW = 20;

	private RigFormsClient() {
	}

	/** Apply an arm form's pose to the humanoid model, called from {@link RigPose#applyModel}. */
	public static void applyForm(Model<?> model, int kind, float t, float recoil, float still) {
		if (!(model instanceof HumanoidModel<?> h)) {
			return;
		}
		switch (kind) {
			case RigMoves.ARM_BLADE -> blade(h, t);
			case RigMoves.ARM_MACE -> mace(h, t);
			case RigMoves.ARM_PILE -> pile(h, t);
			case RigMoves.ARM_DRILL -> drill(h, t);
			case RigMoves.ARM_LANCE -> lance(h, t);
			case RigMoves.ARM_LASH -> lash(h, t);
			case RigMoves.ARM_MIMIC -> mimic(h, t);
			default -> {
			}
		}
	}

	private static void blade(HumanoidModel<?> h, float t) {
		int fire = RigMoves.ARM_BLADE_FIRE;
		float out = RigBeats.at(t, 0.0F, 0.0F, RigBeats.LINEAR, fire, 1.0F, RigBeats.BACK, RigMoves.ARM_BLADE_TICKS, 0.0F, RigBeats.SINE);
		ModelPart main = mainArm(h);
		set(main, -Mth.HALF_PI + BLADE_OUT * out, 0.0F, 0.0F, out);
		set(offArm(h), 0.4F, 0.0F, -0.2F, out);
	}

	private static void mace(HumanoidModel<?> h, float t) {
		int fire = RigMoves.ARM_MACE_FIRE;
		float swell = RigBeats.at(t, 0.0F, 0.0F, RigBeats.LINEAR, fire, 1.0F, RigBeats.BACK, RigMoves.ARM_MACE_TICKS, 0.0F, RigBeats.SINE);
		ModelPart main = mainArm(h);
		set(main, 0.2F + MACE_SWELL * swell, 0.0F, 0.1F, swell);
		set(offArm(h), 0.3F, 0.0F, -0.3F, swell);
	}

	private static void pile(HumanoidModel<?> h, float t) {
		int fire = RigMoves.ARM_PILE_FIRE;
		float out = RigBeats.at(t, 0.0F, 0.0F, RigBeats.LINEAR, fire, 1.0F, RigBeats.BACK, RigMoves.ARM_PILE_TICKS, 0.0F, RigBeats.SINE);
		ModelPart main = mainArm(h);
		set(main, -Mth.HALF_PI + PILE_OUT * out, 0.0F, 0.0F, out);
		set(offArm(h), 0.5F, 0.0F, -0.3F, out);
	}

	private static void drill(HumanoidModel<?> h, float t) {
		float spin = Mth.sin(t * 0.6F) * DRILL_SPIN;
		ModelPart main = mainArm(h);
		main.zRot += spin;
		set(offArm(h), 0.3F, 0.0F, -0.2F, 1.0F);
	}

	private static void lance(HumanoidModel<?> h, float t) {
		int fire = RigMoves.ARM_LANCE_FIRE;
		float level = RigBeats.at(t, 0.0F, 0.0F, RigBeats.LINEAR, fire, 1.0F, RigBeats.BACK, RigMoves.ARM_LANCE_TICKS, 0.0F, RigBeats.SINE);
		ModelPart main = mainArm(h);
		set(main, -Mth.HALF_PI + LANCE_LEVEL * level, 0.0F, 0.0F, level);
		set(offArm(h), 0.4F, 0.0F, -0.2F, level);
	}

	private static void lash(HumanoidModel<?> h, float t) {
		int fire = RigMoves.ARM_LASH_FIRE;
		float crack = RigBeats.at(t, 0.0F, 0.0F, RigBeats.LINEAR, fire, 1.0F, RigBeats.BACK, RigMoves.ARM_LASH_TICKS, 0.0F, RigBeats.SINE);
		ModelPart main = mainArm(h);
		set(main, 0.3F + LASH_CRACK * crack, 0.0F, 0.2F, crack);
		set(offArm(h), 0.3F, 0.0F, -0.3F, crack);
	}

	private static void mimic(HumanoidModel<?> h, float t) {
		float glow = Mth.sin(t * 0.4F) * 0.3F;
		ModelPart main = mainArm(h);
		main.zRot += glow;
		set(offArm(h), 0.3F, 0.0F, -0.2F, 1.0F);
	}

	private static ModelPart mainArm(HumanoidModel<?> h) {
		return h.rightArm;
	}

	private static ModelPart offArm(HumanoidModel<?> h) {
		return h.leftArm;
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
