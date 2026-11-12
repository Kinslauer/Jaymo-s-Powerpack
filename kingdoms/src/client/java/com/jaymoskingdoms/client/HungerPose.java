package com.jaymoskingdoms.client;

import com.jaymoskingdoms.hunger.HungerCurse;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * The curse of the hungry ({@link HungerCurse}) on a player's body: sprinting at night, or grown, they drop onto all fours:
 * the whole body pitches forward over the hands, the arms reach down to the ground and swing with the legs, the head lifts to
 * look ahead. Eased in and out, so the drop and the rise read as a movement. The render state carries how far down they are
 * ({@link #KEY}); the two mixins ({@code HungerPoseRendererMixin}, {@code HungerPoseModelMixin}) call {@link #extract},
 * {@link #rotate} and {@link #pose}.
 */
public final class HungerPose {
	/** How far forward the body pitches (degrees) and how far back it is slid so it stays over the feet. */
	static final float PITCH = 68.0F, SLIDE = 0.8F;
	/** How much of the way to the target it eases each frame. */
	static final float EASE = 0.22F;

	static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:hunger_pose");
	private static final Map<Avatar, float[]> EASED = new WeakHashMap<>();

	private HungerPose() {
	}

	/** From the avatar renderer's extractRenderState: how far {@code entity} is down on all fours (0 to 1; absent for most). */
	public static void extract(Avatar entity, AvatarRenderState state) {
		float target = down(entity) ? 1.0F : 0.0F;
		float[] eased = EASED.computeIfAbsent(entity, e -> new float[] {0.0F});
		eased[0] += (target - eased[0]) * EASE;
		if (eased[0] < 0.01F) {
			eased[0] = 0.0F;
		}
		state.setData(KEY, eased[0] > 0.0F ? eased[0] : null);
	}

	private static boolean down(Avatar entity) {
		return entity instanceof Player player && HungerCurse.cursed(player) && player.isSprinting() && !player.isPassenger() && !player.isSwimming()
			&& !player.isFallFlying() && !player.isSleeping() && (player.level().isDarkOutside() || player.getScale() > 1.3F);
	}

	/** From the avatar renderer's setupRotations (after the vanilla ones): the body pitched forward over its hands. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		Float down = state.getData(KEY);
		if (down == null) {
			return;
		}
		poseStack.translate(0.0F, 0.0F, SLIDE * down);
		poseStack.rotateDegrees(Axis.XP, -PITCH * down);
	}

	/** From the player model's setupAnim (after the vanilla one): the arms down to the ground and swinging, the head lifted. */
	public static void pose(HumanoidModel<?> model, AvatarRenderState state) {
		Float down = state.getData(KEY);
		if (down == null) {
			return;
		}
		float swing = state.walkAnimationPos * 0.8F;
		float amount = Math.min(1.0F, state.walkAnimationSpeed * 1.2F) * 0.7F;
		model.rightArm.xRot = Mth.lerp(down, model.rightArm.xRot, -1.2F + Mth.cos(swing) * amount);
		model.leftArm.xRot = Mth.lerp(down, model.leftArm.xRot, -1.2F + Mth.cos(swing + Mth.PI) * amount);
		model.rightArm.yRot = Mth.lerp(down, model.rightArm.yRot, 0.0F);
		model.leftArm.yRot = Mth.lerp(down, model.leftArm.yRot, 0.0F);
		model.rightArm.zRot = Mth.lerp(down, model.rightArm.zRot, 0.1F);
		model.leftArm.zRot = Mth.lerp(down, model.leftArm.zRot, -0.1F);
		model.rightLeg.xRot = Mth.lerp(down, model.rightLeg.xRot, Mth.cos(swing + Mth.PI) * amount * 1.2F);
		model.leftLeg.xRot = Mth.lerp(down, model.leftLeg.xRot, Mth.cos(swing) * amount * 1.2F);
		model.head.xRot -= 1.1F * down;
	}
}
