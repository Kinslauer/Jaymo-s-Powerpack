package com.jaymospowers.client.freerun;

import com.jaymospowers.freerun.Freerun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * (0.14.3) Bar Swing as everyone sees it ({@link Freerun#HANG}): hanging from the bar by both hands, arms straight
 * up, the body swinging under it like a pendulum from the hands, the legs together and trailing the swing a little.
 * Read and turned the way {@code LedgePose} is (the {@code Ledge...Mixin}s), squared to the way the player faced when
 * they caught it, the head free to look about. Your own hang doesn't show in first person.
 */
public final class BarHangPose {
	// ===== the look: knobs to tweak ==================================================================================
	/** The hands' height over the feet while hanging (blocks): the swing turns about them. */
	static final float HANDS = 2.25F;
	/** The swing: how far each way (degrees) and how fast (radians a tick), and ticks to swing up to its full arc. */
	static final float SWING_DEGREES = 22.0F, SWING_SPEED = 0.16F, EASE_IN = 12.0F;
	/** The arms straight up (radians: straight up is -PI) and a little apart; the legs trailing the swing (radians). */
	static final float ARM_UP = -3.0F, ARM_SPREAD = 0.15F, LEG_TRAIL = 0.35F;
	// ================================================================================================================

	/** {@code time}: ticks, for the swing; {@code in}: how far the swing has built up (0-1). */
	public record Hang(float time, float in) {
	}

	public static final RenderStateDataKey<Hang> KEY = RenderStateDataKey.create(() -> "jaymospowers:freerun_bar_hang");

	/** Per entity id: the game time it caught the bar. */
	private static final Map<Integer, Float> STARTED = new HashMap<>();

	private BarHangPose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Integer face = entity instanceof Player player ? player.getAttached(Freerun.HANG) : null;
		Minecraft minecraft = Minecraft.getInstance();
		boolean ownView = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		if (face == null || face < 0 || face > 3 || ownView || entity.isPassenger()) {
			if (face == null) {
				STARTED.remove(entity.getId());
			}
			state.setData(KEY, null);
			return;
		}
		float time = entity.level().getGameTime() + partialTicks;
		float started = STARTED.computeIfAbsent(entity.getId(), id -> time);
		// square to the way they caught it, the head free to look about within reason
		float head = state.bodyRot + state.yRot;
		state.bodyRot = Direction.from2DDataValue(face).toYRot();
		state.yRot = Mth.clamp(Mth.wrapDegrees(head - state.bodyRot), -70.0F, 70.0F);
		state.walkAnimationSpeed = 0.0F;
		state.setData(KEY, new Hang(time - started, Mth.clamp((time - started) / EASE_IN, 0.0F, 1.0F)));
	}

	/** After the body faces its way: the pendulum swing from the hands. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		Hang hang = state.getData(KEY);
		if (hang == null) {
			return;
		}
		poseStack.translate(0.0F, HANDS, 0.0F);
		poseStack.rotateDegrees(Axis.XP, Mth.sin(hang.time() * SWING_SPEED) * SWING_DEGREES * hang.in());
		poseStack.translate(0.0F, -HANDS, 0.0F);
	}

	/** Right after the model animates: arms up on the bar, legs together trailing the swing. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Hang hang = state.getData(KEY);
		if (hang == null) {
			return;
		}
		h.body.xRot = 0.0F;
		h.body.yRot = 0.0F;
		float trail = -Mth.cos(hang.time() * SWING_SPEED) * LEG_TRAIL * hang.in();
		set(h.rightArm, ARM_UP, 0.0F, ARM_SPREAD);
		set(h.leftArm, ARM_UP, 0.0F, -ARM_SPREAD);
		set(h.rightLeg, trail + 0.05F, 0.0F, 0.03F);
		set(h.leftLeg, trail - 0.05F, 0.0F, -0.03F);
	}

	private static void set(ModelPart part, float x, float y, float z) {
		part.xRot = x;
		part.yRot = y;
		part.zRot = z;
	}
}
