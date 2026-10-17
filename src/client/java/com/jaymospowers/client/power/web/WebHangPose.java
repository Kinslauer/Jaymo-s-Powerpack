package com.jaymospowers.client.power.web;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.13.23) Hung from Wall Crawl's tendrils ({@link WebTendrilWalk#hanging}: carried between them off the ground, or on
 * a ceiling): no walking the air. The walk stops and the legs dangle, swinging slowly like a pendulum, the left a
 * little behind the right, and harder (trailing back) for a while after you were moving. A wall climb keeps its walk.
 */
public final class WebHangPose {
	// ===== the look: knobs to tweak ==================================================================================
	/** The legs at rest, hanging (radians: a touch forward, a touch apart). */
	static final float HANG_LEG_X = -0.08F, HANG_LEG_SPREAD = 0.07F;
	/** The swing: how far each way at rest and how much more just after moving (radians), and how fast (radians a tick). */
	static final float HANG_SWAY = 0.09F, HANG_SWAY_MOVING = 0.42F, HANG_SWAY_RATE = 0.11F;
	/** How far behind the right leg the left one swings (radians of the swing), and the side-to-side swing (radians). */
	static final float HANG_PHASE_GAP = 0.9F, HANG_SIDE_SWAY = 0.04F;
	/** Moving, the legs trail back this far (radians); this speed (blocks/tick) counts as moving flat out. */
	static final float HANG_TRAIL = 0.35F, HANG_SPEED_FULL = 0.25F;
	/** How much of the swing's extra a tick keeps once you stop (it dies down over a few seconds). */
	static final float HANG_SETTLE = 0.97F;
	/** Ticks the pose takes to settle in. */
	static final float HANG_EASE_IN = 4.0F;
	// ================================================================================================================

	/** {@code in}: how settled into the pose (0-1); {@code energy}: how hard it's swinging from moving (0-1). */
	public record Hang(float time, float in, float energy) {
	}

	public static final RenderStateDataKey<Hang> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_hang");

	/** Per entity id: when the hang began, the swing's energy, and when that was last worked out. */
	private static final java.util.Map<Integer, float[]> HANGS = new java.util.HashMap<>();

	private WebHangPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || !WebTendrilWalk.hanging(player)) {
			HANGS.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		if (HANGS.size() > 64) {
			HANGS.keySet().removeIf(id -> id != entity.getId() && entity.level().getEntity(id) == null);
		}
		float time = entity.level().getGameTime() + partialTicks;
		float[] hang = HANGS.computeIfAbsent(entity.getId(), id -> new float[] {time, 0.0F, time});
		float dt = Math.max(0.0F, time - hang[2]);
		hang[2] = time;
		float speed = (float)Math.sqrt(Mth.square(entity.getX() - entity.xo) + Mth.square(entity.getZ() - entity.zo));
		hang[1] = Math.max(Mth.clamp(speed / HANG_SPEED_FULL, 0.0F, 1.0F), hang[1] * (float)Math.pow(HANG_SETTLE, dt));
		float in = Mth.clamp((time - hang[0]) / HANG_EASE_IN, 0.0F, 1.0F);
		// no stepping in mid-air
		state.walkAnimationSpeed = 0.0F;
		state.setData(KEY, new Hang(time, in * in * (3.0F - 2.0F * in), hang[1]));
	}

	/** After the model animates: the legs dangle and swing. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Hang hang = living.getData(KEY);
		// (a web's swing has the legs: WebArms)
		if (hang == null || living.getData(WebArms.KEY) != null) {
			return;
		}
		float e = hang.in();
		float phase = hang.time() * HANG_SWAY_RATE;
		float amp = HANG_SWAY + HANG_SWAY_MOVING * hang.energy();
		float trail = HANG_TRAIL * hang.energy();
		float side = Mth.sin(phase * 0.7F) * HANG_SIDE_SWAY;
		h.rightLeg.xRot = Mth.lerp(e, h.rightLeg.xRot, HANG_LEG_X + trail + amp * Mth.sin(phase));
		h.leftLeg.xRot = Mth.lerp(e, h.leftLeg.xRot, HANG_LEG_X + trail + amp * Mth.sin(phase - HANG_PHASE_GAP));
		h.rightLeg.yRot = Mth.lerp(e, h.rightLeg.yRot, 0.0F);
		h.leftLeg.yRot = Mth.lerp(e, h.leftLeg.yRot, 0.0F);
		h.rightLeg.zRot = Mth.lerp(e, h.rightLeg.zRot, HANG_LEG_SPREAD + side);
		h.leftLeg.zRot = Mth.lerp(e, h.leftLeg.zRot, -HANG_LEG_SPREAD + side);
	}
}
