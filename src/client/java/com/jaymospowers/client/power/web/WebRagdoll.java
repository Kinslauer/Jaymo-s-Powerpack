package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * A creature on the end of a web (held by Yank, or hitched to a block) goes limp like a ragdoll, the way Jaymo's
 * Kitchen's jolted corpses do: its body tips along the way it's being dragged, and every limb it has (whatever the
 * model: arms, legs, wings, tails) flops on its own beat. The faster it's dragged, the wilder it gets. A creature thrown
 * by a Time Flow blow goes limp the same way until it lands.
 */
public final class WebRagdoll {
	public static final RenderStateDataKey<Rag> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_ragdoll");

	/** Most the body tips toward the drag (degrees), and tip per block/tick of drag. */
	private static final float MAX_TIP = 55.0F;
	private static final float TIP_PER_SPEED = 90.0F;

	/** The parts that flop, wherever a model has them (on the root, or on its body). */
	private static final String[] LIMBS = {
		"head", "right_arm", "left_arm", "right_leg", "left_leg",
		"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg",
		"right_wing", "left_wing", "tail", "right_ear", "left_ear"
	};

	/** {@code pitch} > 0 tips the top forward, {@code roll} > 0 tips it right (degrees); {@code strength} 0-1 drives the flop. */
	public record Rag(float time, float pitch, float roll, float strength) {
	}

	private WebRagdoll() {
	}

	/** From the living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (entity.getAttached(WebPower.HELD) == null && entity.getAttached(WebPower.HITCH) == null
			&& entity.getAttached(com.jaymospowers.power.gravity.GravityPower.LIFTED) == null
			&& !com.jaymospowers.power.chrono.ChronoFlow.flung(entity)) {
			state.setData(KEY, null);
			return;
		}
		// how it's being dragged, from its motion this tick, in its own frame
		double vx = entity.getX() - entity.xo;
		double vy = entity.getY() - entity.yo;
		double vz = entity.getZ() - entity.zo;
		float yaw = state.bodyRot * Mth.DEG_TO_RAD;
		double forward = -Mth.sin(yaw) * vx + Mth.cos(yaw) * vz;
		double right = -Mth.cos(yaw) * vx - Mth.sin(yaw) * vz;
		double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
		float pitch = Mth.clamp((float)forward * TIP_PER_SPEED, -MAX_TIP, MAX_TIP);
		float roll = Mth.clamp((float)right * TIP_PER_SPEED, -MAX_TIP, MAX_TIP);
		float strength = Mth.clamp(0.3F + (float)speed * 2.5F, 0.0F, 1.0F);
		state.setData(KEY, new Rag(entity.tickCount + partialTicks, pitch, roll, strength));
		// no walking while it's being hauled about
		state.walkAnimationSpeed *= 1.0F - strength;
	}

	/** After the body has been turned to face its way: tip it along the drag, about its middle. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Rag rag = state.getData(KEY);
		if (rag == null || state.deathTime > 0.0F) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		float wobble = Mth.sin(rag.time() * 0.6F) * 8.0F * rag.strength();
		poseStack.translate(0.0F, middle, 0.0F);
		// local +Z is the body's back and +X its right: a negative turn about X tips the top forward
		poseStack.rotateDegrees(Axis.XP, -rag.pitch());
		poseStack.rotateDegrees(Axis.ZP, rag.roll() + wobble);
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the model animates: every limb flops on its own beat. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living)) {
			return;
		}
		Rag rag = living.getData(KEY);
		if (rag == null) {
			return;
		}
		ModelPart root = model.root();
		flop(root, rag, 0);
		if (root.hasChild("body")) {
			flop(root.getChild("body"), rag, LIMBS.length);
		}
	}

	private static void flop(ModelPart parent, Rag rag, int offset) {
		for (int i = 0; i < LIMBS.length; i++) {
			if (!parent.hasChild(LIMBS[i])) {
				continue;
			}
			ModelPart limb = parent.getChild(LIMBS[i]);
			float phase = rag.time() * 0.9F + (i + offset) * 1.7F;
			float give = rag.strength() * (i == 0 ? 0.45F : 0.95F);
			limb.xRot += Mth.sin(phase) * give;
			limb.zRot += Mth.cos(phase * 1.3F) * give * 0.6F;
		}
	}
}
