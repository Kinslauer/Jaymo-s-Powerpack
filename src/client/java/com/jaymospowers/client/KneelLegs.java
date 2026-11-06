package com.jaymospowers.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.util.Mth;

/**
 * (0.21.28) Andy: "the kneel we have on crouch right now needs a revamp too so change them all". Every kneel used to put
 * the leg on the ground as one straight stick: the front one angled half up, the back one stuck out behind, the body sunk
 * between them like a split. Now they all kneel the same, as a body really does, built from the leg's two halves (six
 * pixels each, {@link KneeBend}):
 * <ul>
 * <li>the <b>knee down</b>: the thigh straight down under the hip (a touch back), the shin flat along the ground behind;</li>
 * <li>the <b>foot planted</b>: the thigh straight out level ahead, the shin straight down to the foot;</li>
 * <li>the hips {@link #SINK} lower than standing, one thigh's length, so the knee and the foot are both on the ground.</li>
 * </ul>
 * Kneeling poses ask for the knees here ({@link #want}) while they animate, and the bends are laid just before the model is
 * drawn ({@link #bend}). Poses keyed in the old way (a front leg about -1.15, a back leg about 1.35) are read through
 * {@link #remap}, so the finishers, the web executions and Gravity's crush all kneel properly too.
 */
public final class KneelLegs {
	/** How far the hips drop from standing to kneel (blocks): one thigh, so knee and foot both touch the ground. */
	public static final float SINK = 6.0F / 16.0F;
	/** The knee-down leg as the world sees it: the thigh a touch back from straight down, the knee bent to lay the shin flat. */
	public static final float DOWN_THIGH = 0.12F, DOWN_KNEE = 1.33F;
	/** The planted leg as the world sees it: the thigh out level ahead, the knee bent to stand the shin straight. */
	public static final float UP_THIGH = -1.5708F, UP_KNEE = 1.5708F;
	/** Old-style keys: a leg swung back past this is a knee on the ground; one forward past this is a planted foot. */
	static final float OLD_DOWN_FROM = 0.7F, OLD_DOWN_FULL = 1.3F, OLD_UP_FROM = 0.6F, OLD_UP_FULL = 1.1F;

	/** What each body asked for this frame: right knee, left knee (radians). By render state, so bodies never share. */
	private static final Map<Object, float[]> WANTED = new WeakHashMap<>();

	private KneelLegs() {
	}

	/**
	 * A kneel read from an old-style key: {@code sink} (blocks), each leg's swing as the world sees it, and how far each
	 * knee bends. {@code down} is how much it's a kneel at all (0: not, the key's own sink and legs).
	 */
	public record Legs(float sink, float right, float left, float rightKnee, float leftKnee, float down) {
	}

	/**
	 * Reads an old-style kneeling key: a leg swung well back ({@code right} or {@code left}, world, radians) is a knee on the
	 * ground; with one there, a leg swung well forward is the planted foot. Both back: on both knees. Anything else (a
	 * stoop, a lunge, astride on both knees) is left as it is.
	 */
	public static Legs remap(float sink, float right, float left) {
		float rd = share(right, OLD_DOWN_FROM, OLD_DOWN_FULL), ld = share(left, OLD_DOWN_FROM, OLD_DOWN_FULL);
		float down = Math.max(rd, ld);
		if (down <= 0.0F) {
			return new Legs(sink, right, left, 0.0F, 0.0F, 0.0F);
		}
		float ru = rd > 0.0F ? 0.0F : share(-right, OLD_UP_FROM, OLD_UP_FULL) * down;
		float lu = ld > 0.0F ? 0.0F : share(-left, OLD_UP_FROM, OLD_UP_FULL) * down;
		float r = Mth.lerp(rd, Mth.lerp(ru, right, UP_THIGH), DOWN_THIGH);
		float l = Mth.lerp(ld, Mth.lerp(lu, left, UP_THIGH), DOWN_THIGH);
		return new Legs(Mth.lerp(down, sink, SINK), r, l, rd * DOWN_KNEE + ru * UP_KNEE, ld * DOWN_KNEE + lu * UP_KNEE, down);
	}

	private static float share(float a, float from, float full) {
		return Mth.clamp((a - from) / (full - from), 0.0F, 1.0F);
	}

	/** {@code state}'s body wants its knees bent this much this frame (radians; the most asked for counts). */
	public static void want(Object state, float rightKnee, float leftKnee) {
		if (rightKnee <= 0.0F && leftKnee <= 0.0F) {
			return;
		}
		float[] k = WANTED.computeIfAbsent(state, s -> new float[2]);
		k[0] = Math.max(k[0], rightKnee);
		k[1] = Math.max(k[1], leftKnee);
	}

	/** Right before the model is drawn: the knees asked for this frame, bent ({@link KneeBend#restore} once drawn). */
	public static void bend(Model<?> model, Object state) {
		float[] k = WANTED.remove(state);
		if (k == null || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		if (k[0] > 0.0F) {
			KneeBend.bend(h.rightLeg, k[0]);
		}
		if (k[1] > 0.0F) {
			KneeBend.bend(h.leftLeg, k[1]);
		}
	}
}
