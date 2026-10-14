package com.jaymospowers.client;

import com.jaymospowers.core.Meditate;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * How a meditating player looks (see {@link Meditate}), drawn for everyone. Meditating: they sit down on the ground,
 * legs folded out to the sides, hands resting on the knees, head a little bowed, breathing slowly. A Z Fighter
 * shadowboxes instead: a fighting stance, bouncing on the feet, jabs left and right with the body turning into each,
 * and every fourth beat a front kick. Under heavy gravity the stance sinks lower and every blow trembles.
 */
public final class MeditatePose {
	/** How long sitting down (or settling into the stance) takes, in ticks. */
	static final float SETTLE = 12.0F;
	/** Sitting: how far the body sinks (blocks), so the hips rest on the ground. */
	static final float SIT_SINK = 0.62F;
	/** Shadowboxing: a punch every this many ticks (the server's swish keeps the same beat); the heavy stance's sink. */
	static final float BEAT = 9.0F;
	static final float HEAVY_SINK = 0.12F;

	/** {@code time}: ticks since they began; {@code kind}: {@link Meditate#MEDITATING}, {@link Meditate#SHADOWBOXING}... */
	public record State(float time, int kind) {
	}

	public static final RenderStateDataKey<State> KEY = RenderStateDataKey.create(() -> "jaymospowers:meditate");

	private MeditatePose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Meditate.State meditating = entity instanceof Player player ? Meditate.of(player) : null;
		if (meditating == null) {
			state.setData(KEY, null);
			return;
		}
		float time = Math.max(0.0F, entity.level().getGameTime() - meditating.since() + partialTicks);
		state.setData(KEY, new State(time, meditating.kind()));
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
		// on the balls of the feet: a bounce twice a punch
		float bounce = 0.035F * Math.abs(Mth.sin(m.time() * Mth.PI / BEAT));
		float sink = m.kind() == Meditate.SHADOWBOXING_HEAVY ? HEAVY_SINK : 0.04F;
		poseStack.translate(0.0F, (-sink + bounce) * in, 0.0F);
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
			box(h, m.time(), in, m.kind() == Meditate.SHADOWBOXING_HEAVY);
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

	/** Jab, jab, and every fourth beat a kick; heavy gravity sinks the stance and shakes every blow. */
	private static void box(HumanoidModel<?> h, float time, float in, boolean heavy) {
		float beat = time / BEAT;
		int n = Mth.floor(beat);
		float p = beat - n;
		// out fast, back slower
		float strike = p < 0.35F ? p / 0.35F : 1.0F - (p - 0.35F) / 0.65F;
		strike = strike * strike * (3.0F - 2.0F * strike);
		boolean right = (n & 1) == 0;
		boolean kick = n % 4 == 3;
		float shake = heavy ? Mth.sin(time * 2.9F) * 0.05F : 0.0F;
		// the guard: fists up before the chin, elbows in
		float guardX = -1.35F + shake, guardY = 0.45F;
		float punchX = -1.62F + shake, punchY = 0.12F;
		float rs = right && !kick ? strike : 0.0F;
		float ls = !right && !kick ? strike : 0.0F;
		blend(h.rightArm, Mth.lerp(rs, guardX, punchX), -Mth.lerp(rs, guardY, punchY), 0.0F, in);
		blend(h.leftArm, Mth.lerp(ls, guardX, punchX), Mth.lerp(ls, guardY, punchY), 0.0F, in);
		// the body turns into each punch
		blend(h.body, 0.0F, (ls - rs) * 0.3F, 0.0F, in);
		// a boxer's stance: left foot forward, knees soft (wider and lower under the weight)
		float spread = heavy ? 0.22F : 0.1F;
		float ks = kick ? strike : 0.0F;
		blend(h.rightLeg, Mth.lerp(ks, 0.3F, -1.35F), 0.0F, spread, in);
		blend(h.leftLeg, -0.3F, 0.0F, -spread, in);
		h.head.xRot = Mth.lerp(in, h.head.xRot, 0.1F + shake);
	}

	private static float settle(float time) {
		float t = Mth.clamp(time / SETTLE, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static void blend(ModelPart part, float x, float y, float z, float k) {
		part.xRot = Mth.lerp(k, part.xRot, x);
		part.yRot = Mth.lerp(k, part.yRot, y);
		part.zRot = Mth.lerp(k, part.zRot, z);
	}
}
