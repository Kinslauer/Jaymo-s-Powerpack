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
 */
public final class MeditatePose {
	/** How long sitting down (or settling into the stance) takes, in ticks. */
	static final float SETTLE = 12.0F;
	/** Sitting: how far the body sinks (blocks), so the hips rest on the ground. */
	static final float SIT_SINK = 0.62F;
	/** Shadowboxing: the heavy stance's sink. */
	static final float HEAVY_SINK = 0.12F;

	/** {@code time}: ticks since they began; {@code kind}: {@link Meditate#MEDITATING}...; {@code frame}: the shadowboxing pose. */
	public record State(float time, int kind, float[] frame) {
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
		state.setData(KEY, new State(time, meditating.kind(), frame));
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
			sit(h, m.time(), in);
		} else {
			box(h, m.frame(), m.time(), in, m.kind() == Meditate.SHADOWBOXING_HEAVY);
		}
	}

	/** Cross-legged on the ground, hands on the knees, slow breaths. */
	private static void sit(HumanoidModel<?> h, float time, float in) {
		float breath = Mth.sin(time * 0.12F);
		blend(h.rightLeg, -1.45F, 0.95F, 0.1F, in);
		blend(h.leftLeg, -1.45F, -0.95F, -0.1F, in);
		blend(h.rightArm, -0.75F, 0.25F, 0.2F, in);
		blend(h.leftArm, -0.75F, -0.25F, -0.2F, in);
		blend(h.body, 0.04F * breath, 0.0F, 0.0F, in);
		h.head.xRot = Mth.lerp(in, h.head.xRot, 0.25F + 0.03F * breath);
		h.head.yRot = Mth.lerp(in, h.head.yRot, 0.0F);
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
