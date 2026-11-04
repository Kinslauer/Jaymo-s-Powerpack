package com.jaymospowers.client;

import com.jaymospowers.core.Meditate;
import com.jaymospowers.core.Shadowbox;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How a meditating player looks (see {@link Meditate}), drawn for everyone. Meditating: they sit down on the ground,
 * legs folded out to the sides, hands resting on the knees, head a little bowed, breathing slowly. A Z Fighter or a
 * Gravity user shadowboxes instead: a fighting stance, bouncing on the feet, running through combos ({@link Shadowbox}).
 * The basic one ends in a high kick with the body tipped over at the hip, then next time round in a side kick (turned
 * side-on, leaning away); six more combos come with unlocked abilities. Under heavy gravity the stance sinks lower and
 * every blow trembles.
 *
 * <p>(0.19.67) Sitting has {@link Meditate#POSES} ways each, on the ground (cross-legged, knees up, leaning back on the
 * hands) and on a seat (upright, legs crossed, slouched), changed with the Sit key. On a seat the body keeps the seat's
 * way and the head turns within {@link #SEAT_LOOK}. What's in the main hand changes the arms: a book held open to read,
 * a cup (anything drunk) raised, a sword across the lap.
 */
public final class MeditatePose {
	/** How long sitting down (or settling into the stance) takes, in ticks. */
	static final float SETTLE = 12.0F;
	/** Sitting: how far the body sinks (blocks), so the hips rest on the ground. */
	static final float SIT_SINK = 0.62F;
	/** (0.19.67) On a seat, how far the head may turn from the seat's way (degrees). */
	static final float SEAT_LOOK = 80.0F;
	/** (0.19.67) What's in the main hand while sitting. */
	static final int EMPTY = 0, BOOK = 1, CUP = 2, BLADE = 3;
	/** Shadowboxing: the heavy stance's sink. */
	static final float HEAVY_SINK = 0.12F;

	/**
	 * {@code time}: ticks since they began; {@code kind}: {@link Meditate#MEDITATING}...; {@code frame}: the shadowboxing pose;
	 * (0.19.67) {@code pose}: which way they sit; {@code seated}: on a seat; {@code held}: {@link #EMPTY}, {@link #BOOK}...
	 */
	public record State(float time, int kind, float[] frame, int pose, boolean seated, int held) {
	}

	public static final RenderStateDataKey<State> KEY = RenderStateDataKey.create(() -> "jaymospowers:meditate");

	// frame slots: right arm x y z, left arm x y z, right leg x z, left leg x z, waist twist (y), tip at the hip (x: + forward,
	// z: + over to the left), whole-body turn (turns, + brings the right side forward), how much the head keeps facing ahead
	private static final int RAX = 0, RAY = 1, RAZ = 2, LAX = 3, LAY = 4, LAZ = 5, RLX = 6, RLZ = 7, LLX = 8, LLZ = 9, BY = 10, TX = 11,
		TZ = 12, TURN = 13, HOLD = 14, SLOTS = 15;

	/** Fists up before the chin, elbows in, left foot forward, knees soft. */
	private static final float[] STANCE = {-1.35F, -0.45F, 0.0F, -1.35F, 0.45F, 0.0F, 0.3F, 0.1F, -0.3F, -0.1F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F};
	/** Shares of a move: wound up, struck (the blow lands just before), held. Then back to the stance. */
	private static final float WOUND = 0.3F, STRUCK = 0.5F, HELD = 0.72F;
	/** The hips, in model units: the tip pivots here. */
	private static final Vector3f HIP = new Vector3f(0.0F, 12.0F, 0.0F);

	/** Each move's wind-up and strike, as changes from the stance (slot, value pairs). */
	private static final Map<Shadowbox.Move, float[][]> MOVES = new EnumMap<>(Shadowbox.Move.class);

	static {
		move(Shadowbox.Move.JAB,
			new float[] {LAX, -1.2F, LAY, 0.55F, BY, 0.15F},
			new float[] {LAX, -1.62F, LAY, 0.08F, BY, 0.45F, LLX, -0.4F});
		move(Shadowbox.Move.CROSS,
			new float[] {RAX, -1.1F, RAY, -0.3F, BY, 0.3F},
			new float[] {RAX, -1.62F, RAY, -0.05F, BY, -0.55F, RLX, 0.45F});
		move(Shadowbox.Move.LEAD_HOOK,
			new float[] {LAX, -1.5F, LAY, -0.7F, LAZ, -0.3F, BY, -0.25F},
			new float[] {LAX, -1.55F, LAY, 0.75F, BY, 0.6F, TZ, 0.06F});
		move(Shadowbox.Move.REAR_HOOK,
			new float[] {RAX, -1.5F, RAY, 0.7F, RAZ, 0.3F, BY, 0.25F},
			new float[] {RAX, -1.55F, RAY, -0.75F, BY, -0.6F, TZ, -0.06F});
		move(Shadowbox.Move.LEAD_UPPERCUT,
			new float[] {LAX, -0.25F, LAY, 0.15F, BY, -0.3F, TX, 0.2F, LLX, 0.0F},
			new float[] {LAX, -2.85F, LAY, 0.2F, BY, 0.45F, TX, -0.12F});
		move(Shadowbox.Move.REAR_UPPERCUT,
			new float[] {RAX, -0.25F, RAY, -0.15F, BY, 0.3F, TX, 0.2F, RLX, 0.35F, LLX, -0.4F},
			new float[] {RAX, -2.85F, RAY, -0.2F, BY, -0.45F, TX, -0.12F});
		move(Shadowbox.Move.REAR_ELBOW,
			new float[] {RAX, -1.6F, RAY, 0.6F, RAZ, 0.4F, BY, 0.35F},
			new float[] {RAX, -1.7F, RAY, -1.15F, BY, -0.7F, TZ, -0.06F, RLX, 0.45F});
		move(Shadowbox.Move.REAR_KNEE,
			new float[] {RLX, 0.45F, TX, 0.05F, RAX, -1.5F, LAX, -1.5F},
			new float[] {RLX, -1.5F, RAX, -1.0F, RAY, -0.2F, LAX, -1.0F, LAY, 0.2F, TX, -0.25F, LLX, 0.05F});
		move(Shadowbox.Move.TEEP,
			new float[] {RLX, -1.25F, TX, -0.05F},
			new float[] {RLX, -1.6F, TX, -0.3F, LLX, 0.05F, RAX, -0.9F, RAZ, 0.25F});
		// the high kick in the pictures: the leg swings up and out, the body tips over at the hip the other way
		move(Shadowbox.Move.HIP_KICK,
			new float[] {RLX, -1.3F, RLZ, 0.5F, TURN, 0.06F, TZ, 0.15F, BY, -0.15F},
			new float[] {RLX, -0.75F, RLZ, 1.45F, TURN, 0.15F, TZ, 0.55F, TX, -0.12F, BY, -0.3F, LLX, 0.0F, LLZ, -0.05F, RAX, -1.5F,
				LAX, -1.5F});
		move(Shadowbox.Move.LEAD_HIP_KICK,
			new float[] {LLX, -1.3F, LLZ, -0.5F, TURN, -0.06F, TZ, -0.15F, BY, 0.15F},
			new float[] {LLX, -0.75F, LLZ, -1.45F, TURN, -0.15F, TZ, -0.55F, TX, -0.12F, BY, 0.3F, RLX, 0.0F, RLZ, 0.05F, RAX, -1.5F,
				LAX, -1.5F});
		// the side kick: turned side-on, knee up, then the leg drives straight out with the body leaning right away from it
		move(Shadowbox.Move.SIDE_KICK,
			new float[] {RLX, -1.45F, RLZ, 0.3F, TURN, 0.2F, TZ, 0.15F, HOLD, 0.85F},
			new float[] {RLX, -0.1F, RLZ, 1.5F, TURN, 0.25F, TZ, 0.85F, HOLD, 0.85F, LLX, 0.0F, LLZ, -0.05F, RAX, -0.35F, RAZ, 0.45F,
				LAX, -1.4F});
		move(Shadowbox.Move.LEAD_SIDE_KICK,
			new float[] {LLX, -1.45F, LLZ, -0.3F, TURN, -0.2F, TZ, -0.15F, HOLD, 0.85F},
			new float[] {LLX, -0.1F, LLZ, -1.5F, TURN, -0.25F, TZ, -0.85F, HOLD, 0.85F, RLX, 0.0F, RLZ, 0.05F, LAX, -0.35F, LAZ, -0.45F,
				RAX, -1.4F});
		move(Shadowbox.Move.LOW_KICK,
			new float[] {RLX, -0.2F, RLZ, 0.7F, TURN, 0.05F, BY, -0.2F},
			new float[] {RLX, -0.55F, RLZ, -0.25F, TURN, 0.12F, TZ, 0.2F, BY, -0.5F, RAX, -0.6F, RAZ, 0.4F});
		// all the way round: the kick lands with his back to the target, leg driven out behind, body tipped forward
		move(Shadowbox.Move.SPIN_BACK_KICK,
			new float[] {RLX, 0.1F, TX, 0.15F, TURN, -0.3F},
			new float[] {RLX, 1.25F, RLZ, 0.2F, TX, 0.5F, TURN, -0.5F, LLX, 0.0F, RAX, -1.0F, LAX, -1.0F});
		move(Shadowbox.Move.AXE_KICK,
			new float[] {RLX, -2.5F, RLZ, 0.2F, TX, -0.3F},
			new float[] {RLX, -0.3F, RLZ, 0.0F, TX, 0.25F});
	}

	private static void move(Shadowbox.Move move, float[] wound, float[] struck) {
		MOVES.put(move, new float[][] {apply(wound), apply(struck)});
	}

	private static float[] apply(float[] changes) {
		float[] out = STANCE.clone();
		for (int i = 0; i < changes.length; i += 2) {
			out[(int)changes[i]] = changes[i + 1];
		}
		return out;
	}

	private MeditatePose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Meditate.State meditating = entity instanceof Player player ? Meditate.of(player) : null;
		if (meditating == null) {
			state.setData(KEY, null);
			return;
		}
		long ticks = Math.max(0L, entity.level().getGameTime() - meditating.since());
		float time = ticks + partialTicks;
		float[] frame = null;
		if (meditating.kind() != Meditate.MEDITATING) {
			frame = frame(Shadowbox.step(ticks, meditating.combos()), partialTicks);
			// turning side-on (or all the way round): the whole body goes, the head stays on the target as far as it can
			float turn = frame[TURN] * 360.0F * settle(time);
			if (turn != 0.0F) {
				state.bodyRot -= turn;
				state.yRot += turn * frame[HOLD];
			}
		}
		int held = EMPTY;
		if (meditating.kind() == Meditate.MEDITATING) {
			held = held(entity.getMainHandItem());
			if (meditating.seated()) {
				// the body keeps the seat's way; the head looks about within reason
				float head = state.bodyRot + state.yRot;
				state.bodyRot = meditating.seatYaw();
				state.yRot = Mth.clamp(Mth.wrapDegrees(head - meditating.seatYaw()), -SEAT_LOOK, SEAT_LOOK);
			}
		}
		state.setData(KEY, new State(time, meditating.kind(), frame, meditating.pose(), meditating.seated(), held));
	}

	private static int held(ItemStack stack) {
		if (stack.isEmpty()) {
			return EMPTY;
		}
		if (stack.is(ItemTags.BOOKSHELF_BOOKS)) {
			return BOOK;
		}
		if (stack.getUseAnimation() == ItemUseAnimation.DRINK) {
			return CUP;
		}
		return stack.is(ItemTags.SWORDS) ? BLADE : EMPTY;
	}

	/** Stance, wound up, struck, held, back to the stance; between combos, just the stance. */
	private static float[] frame(Shadowbox.Step step, float partialTicks) {
		if (step.move() == null) {
			return STANCE.clone();
		}
		float[][] keys = MOVES.get(step.move());
		float t = step.progress(partialTicks);
		boolean spin = step.move() == Shadowbox.Move.SPIN_BACK_KICK;
		float[] out = new float[SLOTS];
		for (int i = 0; i < SLOTS; i++) {
			float[] wound = keys[0];
			float[] struck = keys[1];
			float v;
			if (t < WOUND) {
				v = Mth.lerp(smooth(t / WOUND), STANCE[i], wound[i]);
			} else if (t < STRUCK) {
				// the snap: fast out of the wind-up
				float k = (t - WOUND) / (STRUCK - WOUND);
				v = Mth.lerp(1.0F - (1.0F - k) * (1.0F - k), wound[i], struck[i]);
			} else if (t < HELD) {
				v = struck[i];
			} else {
				// a spin carries on round to a whole turn (the same as none)
				float to = spin && i == TURN ? -1.0F : STANCE[i];
				v = Mth.lerp(smooth((t - HELD) / (1.0F - HELD)), struck[i], to);
			}
			out[i] = v;
		}
		return out;
	}

	/** Down onto the ground (sitting), or into the stance's crouch and bounce (shadowboxing). */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		State m = state.getData(KEY);
		if (m == null || state.deathTime > 0.0F) {
			return;
		}
		float in = settle(m.time());
		if (m.kind() == Meditate.MEDITATING) {
			float breath = 0.012F * Mth.sin(m.time() * 0.12F);
			poseStack.translate(0.0F, -SIT_SINK * in + breath * in, 0.0F);
			return;
		}
		// on the balls of the feet: a bounce twice a beat (still on one foot while kicking)
		float bounce = 0.035F * Math.abs(Mth.sin(m.time() * Mth.PI / Shadowbox.BEAT));
		float kicking = Math.min(1.0F, Math.abs(m.frame()[TZ]) + Math.abs(m.frame()[TX]) * 0.5F);
		float sink = m.kind() == Meditate.SHADOWBOXING_HEAVY ? HEAVY_SINK : 0.04F;
		poseStack.translate(0.0F, (-sink + bounce * (1.0F - kicking)) * in, 0.0F);
	}

	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h) || state.deathTime > 0.0F) {
			return;
		}
		State m = state.getData(KEY);
		if (m == null) {
			return;
		}
		float in = settle(m.time());
		if (m.kind() == Meditate.MEDITATING) {
			sit(h, m, state.mainArm == HumanoidArm.RIGHT, in);
		} else {
			box(h, m.frame(), m.time(), in, m.kind() == Meditate.SHADOWBOXING_HEAVY);
		}
	}

	/**
	 * Sitting, slow breaths. On the ground: cross-legged with the hands on the knees, knees up with the arms round them,
	 * or leaning back on the hands. On a seat: upright, legs crossed, or slouched. Then the main hand's item.
	 */
	private static void sit(HumanoidModel<?> h, State m, boolean rightHanded, float in) {
		float breath = Mth.sin(m.time() * 0.12F);
		float lean = 0.0F;
		float headX = 0.25F;
		int pose = Math.floorMod(m.pose(), Meditate.POSES);
		if (!m.seated()) {
			if (pose == 0) {
				// thighs out to the sides along the ground; the shins fold back across in front (bendKnees)
				blend(h.rightLeg, -1.5F, 0.9F, 0.0F, in);
				blend(h.leftLeg, -1.5F, -0.9F, 0.0F, in);
				blend(h.rightArm, -0.75F, 0.25F, 0.2F, in);
				blend(h.leftArm, -0.75F, -0.25F, -0.2F, in);
			} else if (pose == 1) {
				// knees drawn up together, the shins back down to the feet flat in front (bendKnees)
				blend(h.rightLeg, -2.3F, 0.08F, 0.0F, in);
				blend(h.leftLeg, -2.3F, -0.08F, 0.0F, in);
				blend(h.rightArm, -1.0F, -0.35F, 0.0F, in);
				blend(h.leftArm, -1.0F, 0.35F, 0.0F, in);
				headX = 0.35F;
			} else {
				blend(h.rightLeg, -1.5F, 0.12F, 0.0F, in);
				blend(h.leftLeg, -1.5F, -0.12F, 0.0F, in);
				blend(h.rightArm, 0.55F, 0.0F, 0.25F, in);
				blend(h.leftArm, 0.55F, 0.0F, -0.25F, in);
				lean = -0.35F;
				headX = -0.1F;
			}
		} else if (pose == 0) {
			// thighs along the seat, knees together, shins hanging down (bendKnees)
			blend(h.rightLeg, -1.5F, 0.04F, 0.0F, in);
			blend(h.leftLeg, -1.5F, -0.04F, 0.0F, in);
			blend(h.rightArm, -0.55F, 0.05F, 0.05F, in);
			blend(h.leftArm, -0.55F, -0.05F, -0.05F, in);
			headX = 0.05F;
		} else if (pose == 1) {
			// the right leg over the left
			blend(h.rightLeg, -1.95F, -0.35F, 0.0F, in);
			blend(h.leftLeg, -1.5F, 0.0F, 0.0F, in);
			blend(h.rightArm, -0.75F, -0.25F, 0.0F, in);
			blend(h.leftArm, -0.45F, 0.35F, 0.0F, in);
			headX = 0.05F;
		} else {
			blend(h.rightLeg, -1.4F, 0.2F, 0.0F, in);
			blend(h.leftLeg, -1.4F, -0.2F, 0.0F, in);
			blend(h.rightArm, -0.15F, 0.0F, 0.2F, in);
			blend(h.leftArm, -0.15F, 0.0F, -0.2F, in);
			lean = -0.25F;
			headX = 0.1F;
		}
		ModelPart main = rightHanded ? h.rightArm : h.leftArm;
		ModelPart other = rightHanded ? h.leftArm : h.rightArm;
		switch (m.held()) {
			case BOOK -> {
				// held open before the chest, head bowed to read
				arm(main, -1.05F, 0.3F, 0.0F, rightHanded, in);
				arm(other, -1.05F, 0.3F, 0.0F, !rightHanded, in);
				headX = 0.5F;
			}
			case CUP -> arm(main, -1.35F, 0.35F, 0.0F, rightHanded, in);
			case BLADE -> {
				// the blade laid across the lap, the other hand resting on it
				arm(main, -0.35F, 1.2F, 0.0F, rightHanded, in);
				arm(other, -0.4F, 0.6F, 0.0F, !rightHanded, in);
			}
			default -> {
			}
		}
		blend(h.body, 0.04F * breath, 0.0F, 0.0F, in);
		h.head.xRot = Mth.lerp(in, h.head.xRot, headX + 0.03F * breath);
		if (!m.seated()) {
			h.head.yRot = Mth.lerp(in, h.head.yRot, 0.0F);
		}
		tip(h, lean * in, 0.0F);
	}

	/**
	 * (0.20.38) The knees, bent for each way of sitting (radians: right, left; Andy: "the knees should bend"): {@code
	 * [seated ? 1 : 0][pose]}. On the ground cross-legged the shins fold sideways, back across in front; knees up and on
	 * a seat they fold down at the knee. Leaning back on the hands keeps the legs straight.
	 */
	private static final float[][][] KNEES = {
		{{-2.6F, 2.6F}, {2.05F, 2.05F}, {0.0F, 0.0F}},
		{{1.45F, 1.45F}, {1.35F, 1.55F}, {0.9F, 0.9F}}};

	/**
	 * (0.20.38) Right before the model is drawn (after {@link #applyModel}): a sitter's knees bent for this one draw
	 * ({@link KneeBend#restore} once it's drawn).
	 */
	public static void bendKnees(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h) || state.deathTime > 0.0F) {
			return;
		}
		State m = state.getData(KEY);
		if (m == null || m.kind() != Meditate.MEDITATING) {
			return;
		}
		float in = settle(m.time());
		int pose = Math.floorMod(m.pose(), Meditate.POSES);
		float[] knees = KNEES[m.seated() ? 1 : 0][pose];
		// cross-legged folds across (the right shin toward the left, the left toward the right); the rest at the knee
		boolean sideways = !m.seated() && pose == 0;
		KneeBend.bend(h.rightLeg, knees[0] * in, KneeBend.KNEE, sideways);
		KneeBend.bend(h.leftLeg, knees[1] * in, KneeBend.KNEE, sideways);
	}

	/** An arm's pose with its turn toward the middle of the body ({@code inward}) and out to the side ({@code out}), either arm. */
	private static void arm(ModelPart arm, float x, float inward, float out, boolean right, float in) {
		blend(arm, x, right ? -inward : inward, right ? out : -out, in);
	}

	/** The frame's blow; heavy gravity sinks the stance and shakes every blow. */
	private static void box(HumanoidModel<?> h, float[] f, float time, float in, boolean heavy) {
		float shake = heavy ? Mth.sin(time * 2.9F) * 0.05F : 0.0F;
		float spread = heavy ? 0.12F : 0.0F;
		blend(h.body, 0.0F, f[BY], 0.0F, in);
		// the shoulders go round with the waist
		float turn = h.body.yRot;
		h.rightArm.z = Mth.lerp(in, h.rightArm.z, Mth.sin(turn) * 5.0F);
		h.rightArm.x = Mth.lerp(in, h.rightArm.x, -Mth.cos(turn) * 5.0F);
		h.leftArm.z = Mth.lerp(in, h.leftArm.z, -Mth.sin(turn) * 5.0F);
		h.leftArm.x = Mth.lerp(in, h.leftArm.x, Mth.cos(turn) * 5.0F);
		blend(h.rightArm, f[RAX] + shake, f[RAY] + turn, f[RAZ], in);
		blend(h.leftArm, f[LAX] + shake, f[LAY] + turn, f[LAZ], in);
		blend(h.rightLeg, f[RLX], 0.0F, f[RLZ] + spread, in);
		blend(h.leftLeg, f[LLX], 0.0F, f[LLZ] - spread, in);
		h.head.xRot = Mth.lerp(in, h.head.xRot, 0.1F + shake);
		tip(h, f[TX] * in, f[TZ] * in);
	}

	/** Tips everything above the waist over at the hip (the legs stay where they are). */
	private static void tip(HumanoidModel<?> h, float x, float z) {
		if (Math.abs(x) < 1.0E-4F && Math.abs(z) < 1.0E-4F) {
			return;
		}
		Quaternionf tip = new Quaternionf().rotationZYX(z, 0.0F, x);
		Vector3f at = new Vector3f();
		Vector3f angles = new Vector3f();
		for (ModelPart part : new ModelPart[] {h.body, h.head, h.rightArm, h.leftArm}) {
			tip.transform(at.set(part.x, part.y, part.z).sub(HIP)).add(HIP);
			part.x = at.x;
			part.y = at.y;
			part.z = at.z;
			new Quaternionf(tip).mul(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot)).getEulerAnglesZYX(angles);
			part.xRot = angles.x;
			part.yRot = angles.y;
			part.zRot = angles.z;
		}
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static float settle(float time) {
		return smooth(time / SETTLE);
	}

	private static void blend(ModelPart part, float x, float y, float z, float k) {
		part.xRot = Mth.lerp(k, part.xRot, x);
		part.yRot = Mth.lerp(k, part.yRot, y);
		part.zRot = Mth.lerp(k, part.zRot, z);
	}
}
