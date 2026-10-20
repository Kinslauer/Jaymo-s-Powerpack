package com.jaymospowers.client.freerun;

import com.jaymospowers.freerun.Freerun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.13.36) Sure Footing as everyone sees it ({@link Freerun#balancing}): on a narrow top, both arms held out to the
 * sides for balance, seesawing a little, the body swaying about the feet and the head held level against it. Worked
 * out from the synced ranks and where the player stands, so it shows on every player in view. Laid on early (after
 * the free fall), so any power's own pose wins over it.
 */
public final class BalancePose {
	// ===== the look: knobs to tweak ==================================================================================
	/** The arms: out to the side (radians from hanging down), a little forward, and how far they seesaw. */
	static final float ARM_OUT = 1.35F, ARM_FORWARD = -0.25F, SEESAW = 0.18F;
	/** The seesaw and the body's sway: how fast (radians a tick) and how far the body sways (degrees). */
	static final float SWAY_SPEED = 0.11F, BODY_SWAY_DEGREES = 2.5F;
	/** Ticks it takes to settle in once on the beam. */
	static final float EASE_IN = 4.0F;
	// ================================================================================================================

	/** {@code time}: ticks, for the sway; {@code in}: how settled into the pose (0-1). */
	public record Balance(float time, float in) {
	}

	public static final RenderStateDataKey<Balance> KEY = RenderStateDataKey.create(() -> "jaymospowers:freerun_balance");

	/** Per entity id: the game time it stepped onto the beam. */
	private static final Map<Integer, Float> STARTED = new HashMap<>();

	private BalancePose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || !Freerun.balancing(player)) {
			STARTED.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		float time = entity.level().getGameTime() + partialTicks;
		float started = STARTED.computeIfAbsent(entity.getId(), id -> time);
		float in = Mth.clamp((time - started) / EASE_IN, 0.0F, 1.0F);
		state.setData(KEY, new Balance(time, in * in * (3.0F - 2.0F * in)));
	}

	/** After the body faces its way: a slow sway from side to side about the feet. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Balance balance = state.getData(KEY);
		if (balance == null || state.deathTime > 0.0F) {
			return;
		}
		poseStack.rotateDegrees(Axis.ZP, Mth.sin(balance.time() * SWAY_SPEED) * BODY_SWAY_DEGREES * balance.in());
	}

	/** After the model animates: the arms out, the head level. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Balance balance = living.getData(KEY);
		if (balance == null) {
			return;
		}
		float e = balance.in();
		float seesaw = Mth.sin(balance.time() * SWAY_SPEED) * SEESAW;
		set(h.rightArm, ARM_FORWARD, 0.0F, ARM_OUT + seesaw, e);
		set(h.leftArm, ARM_FORWARD, 0.0F, -ARM_OUT + seesaw, e);
		h.head.zRot = Mth.lerp(e, h.head.zRot, -Mth.sin(balance.time() * SWAY_SPEED) * BODY_SWAY_DEGREES * Mth.DEG_TO_RAD);
	}

	private static void set(ModelPart part, float x, float y, float z, float e) {
		part.xRot = Mth.lerp(e, part.xRot, x);
		part.yRot = Mth.lerp(e, part.yRot, y);
		part.zRot = Mth.lerp(e, part.zRot, z);
	}
}
