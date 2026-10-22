package com.jaymospowers.client.stamina;

import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.jaymospowers.client.power.wind.CombatAnimCompat;
import com.jaymospowers.stamina.Dodge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.74) The dodge's roll ({@link Dodge}), for everyone to see: the body tucks into a ball (knees to the chest,
 * arms round them, chin down), drops low and tumbles one full turn the way the dodge went: a forward roll, a back
 * roll, or over the shoulder to the side. Combat Roll used to draw this; our own dodge only hopped until now.
 * <p>Your own roll draws at once from {@link DodgeClient}; everyone else's from the synced {@link Dodge#ROLLING}. In
 * first person the view tumbles with it ({@link #applyCamera}) and dips toward the ground; your own body drawn there
 * (First-person Model) stays upright rather than swinging through the camera.
 */
public final class DodgeRollPose {
	// ===== the knobs ================================================================================================
	/** How far the body's middle drops at the low point of the roll (blocks, before the entity's scale). */
	static final float DROP = 0.45F;
	/** The tuck: legs drawn up, arms forward round them, the back rounded and the chin down (radians). */
	static final float LEG_TUCK = -1.6F, ARM_TUCK = -1.2F, ARM_IN = 0.25F, BODY_CURL = 0.45F, CHIN = 0.6F;
	/** Of the roll, the share spent tucking in at the start, and opening out at the end. */
	static final float TUCK_IN = 0.15F, TUCK_OUT = 0.3F;
	/** First person: how much of the full turn the view follows (1: all the way over), and how far it dips (blocks). */
	static final float VIEW_ROLL = 1.0F, VIEW_DIP = 0.35F;
	// ================================================================================================================

	/** {@code t}: how far through the roll (0 to 1); {@code (x, z)}: the way it goes, a unit vector on the ground. */
	public record Roll(float t, float x, float z) {
	}

	public static final RenderStateDataKey<Roll> KEY = RenderStateDataKey.create(() -> "jaymospowers:dodge_roll");

	private DodgeRollPose() {
	}

	/** From the avatar renderer's extractRenderState. */
	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		state.setData(KEY, roll(entity, partialTicks));
	}

	private static @Nullable Roll roll(Entity entity, float partialTicks) {
		if (!(entity instanceof Player player) || player.isPassenger() || player.isSwimming() || player.isFallFlying()) {
			return null;
		}
		long now = player.level().getGameTime();
		long start;
		float x, z;
		if (player == Minecraft.getInstance().player && DodgeClient.rollStart != Long.MIN_VALUE) {
			// our own roll: drawn the moment we hop, not when the server's copy comes back
			start = DodgeClient.rollStart;
			x = DodgeClient.rollX;
			z = DodgeClient.rollZ;
		} else {
			Dodge.Rolling rolling = player.getAttached(Dodge.ROLLING);
			if (rolling == null) {
				return null;
			}
			start = rolling.start();
			x = rolling.x();
			z = rolling.z();
		}
		float t = (now - start + partialTicks) / Dodge.ROLL_TICKS;
		return t < 0.0F || t >= 1.0F ? null : new Roll(t, x, z);
	}

	/** How tucked the body is (0 to 1): curling in at the start, opening out to land on its feet. */
	private static float tuck(float t) {
		return Math.min(1.0F, Math.min(t / TUCK_IN, (1.0F - t) / TUCK_OUT));
	}

	/** From setupRotations, after the body has been turned to face its way ({@code bodyRot}, degrees). */
	public static void rotate(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		Roll roll = state.getData(KEY);
		if (roll == null || CombatAnimCompat.firstPersonPass(state) || ownFirstPerson(state)) {
			return;
		}
		// the dodge's way in the body's own frame (after YP(180 - bodyRot): forward is -Z, right is +X)
		float yaw = bodyRot * Mth.DEG_TO_RAD;
		float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		float forward = -roll.x() * sin + roll.z() * cos;
		float right = -roll.x() * cos - roll.z() * sin;
		float length = Mth.sqrt(forward * forward + right * right);
		if (length < 1.0E-4F) {
			return;
		}
		float t = roll.t();
		float turn = t * t * (3.0F - 2.0F * t) * Mth.TWO_PI;
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		// down into a ball near the ground, and back up to the feet
		poseStack.translate(0.0F, -DROP * Mth.sin(Mth.PI * t), 0.0F);
		// the top tips toward the way of the dodge: about (up x way), round the body's middle
		poseStack.rotateAround(new Quaternionf().rotateAxis(turn, -forward / length, 0.0F, -right / length), 0.0F, middle, 0.0F);
	}

	/** First person, with the hurt bob (ScreenShakeMixin): the view turns over with our own roll and dips at its middle. */
	public static void applyCamera(PoseStack poseStack, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		Roll roll = roll(minecraft.player, partialTicks);
		if (roll == null) {
			return;
		}
		// the dodge's way in view space (forward is -Z, right is +X), from where we face
		float yaw = minecraft.player.getViewYRot(partialTicks) * Mth.DEG_TO_RAD;
		float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
		float forward = -roll.x() * sin + roll.z() * cos;
		float right = -roll.x() * cos - roll.z() * sin;
		float length = Mth.sqrt(forward * forward + right * right);
		if (length < 1.0E-4F) {
			return;
		}
		float t = roll.t();
		float turn = t * t * (3.0F - 2.0F * t) * Mth.TWO_PI * VIEW_ROLL;
		// (the view turns the opposite way to the body: the world swings round the other way)
		poseStack.translate(0.0F, VIEW_DIP * Mth.sin(Mth.PI * t), 0.0F);
		poseStack.rotate(new Quaternionf().rotateAxis(-turn, -forward / length, 0.0F, -right / length));
	}

	/** Our own body drawn round the camera in first person: it stays upright (the view doesn't tumble). */
	private static boolean ownFirstPerson(AvatarRenderState state) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && state.id == minecraft.player.getId() && FirstPersonCompat.bodyShown();
	}

	/** After the model animates: the tuck. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Roll roll = living.getData(KEY);
		if (roll == null) {
			return;
		}
		float w = tuck(roll.t());
		set(h.rightLeg, LEG_TUCK, 0.0F, 0.05F, w);
		set(h.leftLeg, LEG_TUCK, 0.0F, -0.05F, w);
		set(h.rightArm, ARM_TUCK, 0.0F, -ARM_IN, w);
		set(h.leftArm, ARM_TUCK, 0.0F, ARM_IN, w);
		h.body.xRot = Mth.lerp(w, h.body.xRot, BODY_CURL);
		h.head.xRot = Mth.lerp(w, h.head.xRot, CHIN);
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}
}
