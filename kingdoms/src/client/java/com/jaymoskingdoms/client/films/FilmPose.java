package com.jaymoskingdoms.client.films;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * How a lore film's actor ({@link LoreFilmClient}) holds itself this frame: on its knees, an axe overhead, a hand to an
 * orc's brow, a lunge; and what a finisher took off it (its head or an arm isn't drawn). Laid over the model right after
 * it's posed, like {@code HandshakePose}. Only the film's own actors (entity ids below zero) are touched.
 */
public final class FilmPose {
	/**
	 * A pose's numbers (radians): the head down and turned, the lean forward from the waist, the right arm's and the left
	 * arm's {forward (negative is up and forward), turned, out to the side}, each leg forward (negative) or back. NaN leaves
	 * a number as the walk has it.
	 */
	static final int HEAD_X = 0, HEAD_Y = 1, LEAN = 2, RIGHT_ARM = 3, LEFT_ARM = 6, RIGHT_LEG = 9, LEFT_LEG = 10, NUMBERS = 11;
	/** After the numbers: how much of the pose is on (0 to 1), and what's gone ({@link #NO_HEAD}, {@link #NO_RIGHT_ARM}). */
	static final int WEIGHT = NUMBERS, GONE = NUMBERS + 1, LENGTH = NUMBERS + 2;
	static final int NO_HEAD = 1, NO_RIGHT_ARM = 2;
	static final float NO = Float.NaN;

	static final float[] KNEEL = {0.45F, 0.0F, 0.12F, -0.15F, 0.0F, 0.12F, -0.15F, 0.0F, -0.12F, 1.45F, 1.45F};
	/** On its knees, its head wrenched back to look up. */
	static final float[] KNEEL_UP = {-0.42F, 0.0F, 0.0F, 0.2F, 0.0F, 0.25F, 0.2F, 0.0F, -0.25F, 1.45F, 1.45F};
	static final float[] OVERHEAD = {-0.1F, 0.0F, -0.1F, -2.8F, 0.0F, -0.25F, -2.8F, 0.0F, 0.25F, 0.0F, 0.0F};
	static final float[] CHOP = {0.25F, 0.0F, 0.35F, -0.75F, 0.0F, -0.3F, -0.75F, 0.0F, 0.3F, -0.3F, 0.3F};
	static final float[] THRUST = {0.0F, 0.0F, 0.3F, -1.45F, 0.0F, 0.0F, 0.5F, 0.0F, -0.2F, -0.7F, 0.5F};
	static final float[] STAGGER = {-0.35F, 0.0F, -0.25F, -0.3F, 0.0F, 1.0F, -0.4F, 0.0F, -1.0F, 0.3F, -0.2F};
	/** The sorcerer: both arms raised, the head back. */
	static final float[] SUMMON = {-0.35F, 0.0F, -0.08F, -2.75F, 0.0F, 0.45F, -2.75F, 0.0F, -0.45F, 0.0F, 0.0F};
	static final float[] CAST_RIGHT = {0.0F, 0.0F, 0.1F, -1.5F, -0.1F, 0.0F, 0.2F, 0.0F, -0.15F, NO, NO};
	static final float[] CAST_LEFT = {0.0F, 0.0F, 0.1F, 0.2F, 0.0F, 0.15F, -1.5F, 0.1F, 0.0F, NO, NO};
	/** Varro's disarm: the wrist caught, the arm wrenched down and round, the kick behind the knee, the hand to the brow, the send-off. */
	static final float[] CATCH = {0.1F, 0.3F, 0.15F, 0.3F, 0.0F, 0.3F, -1.35F, 0.45F, 0.0F, -0.4F, 0.4F};
	static final float[] WRENCH = {0.2F, -0.5F, 0.25F, 0.4F, 0.0F, 0.4F, -0.5F, -0.5F, -0.7F, 0.3F, -0.3F};
	static final float[] KICK = {0.2F, 0.0F, -0.15F, 0.3F, 0.0F, 0.5F, 0.3F, 0.0F, -0.5F, -1.15F, 0.1F};
	static final float[] REACH = {0.35F, 0.0F, 0.12F, 0.1F, 0.0F, 0.15F, -1.0F, 0.2F, 0.0F, 0.0F, 0.0F};
	static final float[] POINT = {0.0F, 0.0F, 0.0F, -1.55F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, NO, NO};
	/** The fifth: running low, the blade trailing; and the lunge. */
	static final float[] RUN = {0.05F, 0.0F, 0.32F, 0.6F, 0.0F, 0.15F, NO, NO, NO, NO, NO};
	static final float[] LUNGE = {-0.1F, 0.0F, 0.5F, -1.5F, 0.0F, 0.0F, 0.9F, 0.0F, -0.3F, -0.95F, 0.75F};

	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:film_pose");

	private FilmPose() {
	}

	/** Render-state extraction: a film actor's pose this frame, or none. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (entity.getId() < 0) {
			state.setData(KEY, LoreFilmClient.pose(entity, partialTicks));
		}
	}

	/** Right after a model was posed for this state. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		float[] p = living.getData(KEY);
		if (p == null) {
			return;
		}
		float w = Mth.clamp(p[WEIGHT], 0.0F, 1.0F);
		if (w > 0.001F) {
			if (!Float.isNaN(p[LEAN])) {
				// bent at the waist as a crouch is: the head and shoulders brought down and forward, the legs back to match
				float lean = p[LEAN] * w, c = lean / 0.5F;
				m.body.xRot += lean;
				m.body.y += 3.2F * Math.abs(c);
				m.head.y += 4.2F * Math.abs(c);
				m.rightArm.y += 3.2F * Math.abs(c);
				m.leftArm.y += 3.2F * Math.abs(c);
				m.rightLeg.z += 4.0F * c;
				m.leftLeg.z += 4.0F * c;
			}
			m.head.xRot = ease(w, m.head.xRot, p[HEAD_X]);
			m.head.yRot = ease(w, m.head.yRot, p[HEAD_Y]);
			arm(m.rightArm, p, RIGHT_ARM, w);
			arm(m.leftArm, p, LEFT_ARM, w);
			m.rightLeg.xRot = ease(w, m.rightLeg.xRot, p[RIGHT_LEG]);
			m.leftLeg.xRot = ease(w, m.leftLeg.xRot, p[LEFT_LEG]);
			if (!Float.isNaN(p[RIGHT_LEG])) {
				m.rightLeg.yRot = ease(w, m.rightLeg.yRot, 0.0F);
				m.leftLeg.yRot = ease(w, m.leftLeg.yRot, 0.0F);
			}
		}
		int gone = (int)p[GONE];
		if ((gone & NO_HEAD) != 0) {
			hide(m.head);
			hide(m.hat);
		}
		if ((gone & NO_RIGHT_ARM) != 0) {
			hide(m.rightArm);
			if (model instanceof PlayerModel player) {
				hide(player.rightSleeve);
			}
		}
	}

	private static void arm(ModelPart part, float[] p, int at, float w) {
		part.xRot = ease(w, part.xRot, p[at]);
		part.yRot = ease(w, part.yRot, p[at + 1]);
		part.zRot = ease(w, part.zRot, p[at + 2]);
	}

	private static float ease(float w, float from, float to) {
		return Float.isNaN(to) ? from : Mth.lerp(w, from, to);
	}

	/** Not drawn (the pose is reset before each draw, so this lasts the one draw). */
	private static void hide(ModelPart part) {
		part.xScale = part.yScale = part.zScale = 0.0F;
	}
}
