package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoForms;
import com.jaymospowers.power.xenoform.XenoformState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The hologram over the band while you hold Z as yourself: the dialled alien, small and see-through, standing in a green
 * glow just above the dial, so you see who you're dialling as you scroll. It stands upright on screen whatever the wrist's
 * roll and turns to face you, swaying a little so it reads as a figure; it flickers, and goes red while the band can't
 * change you yet (recharging, or a hidden alien not yet earned). It rises with the dial's cap and sinks with it as the
 * hand comes down ({@link XenoformSlamView#pop}). Drawn from {@link XenoBand#submitOnArm}, the pose stack in the arm's space.
 */
final class XenoHologram {
	/** The figure's height (blocks) and how far above the dial its feet hover (band pixels out of the dial's face, then blocks up). */
	static final float HEIGHT = 0.3F, OUT_PX = 2.0F, LIFT = 0.02F;
	/** Its sway either side of facing you (degrees) and the sway's speed (radians a tick). */
	static final float SWAY_DEGREES = 25.0F, SWAY_SPEED = 0.08F;
	/** Its opacity (0-1), the flicker's depth and speed (radians a tick), and how often it glitches dim (every this many half-ticks). */
	static final float ALPHA = 0.55F, FLICKER = 0.15F, FLICKER_SPEED = 1.7F;
	static final int GLITCH_EVERY = 23;
	/** The colours it's tinted: the band's green, or red while the band would refuse. */
	static final int TINT = 0x7CFF9A, REFUSED_TINT = 0xFF5040;
	/** A humanoid model's feet are this far below its origin (model pixels, y down). */
	private static final float FEET_PX = 24.0F;
	/** Packed light: full block and sky light. */
	private static final int FULL_BRIGHT = 0xF000F0;

	private XenoHologram() {
	}

	/** The pose stack is in the left arm's own space (the band's dial faces +x, its middle at y 7.5). */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, float pop) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		XenoForm dialled = XenoForms.get(XenoformState.band(player).dialled());
		XenoFormModels.FormModel model = dialled == null ? null : XenoFormModels.of(dialled.id);
		if (model == null) {
			return;
		}
		float time = player.tickCount + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		// where the dial is, in the stack's base space (the eye in first person; the camera sits at its origin either way)
		Vector3f at = poseStack.last().pose().transformPosition((3.5F + OUT_PX) / 16.0F, 7.5F / 16.0F, 0.0F, new Vector3f());
		poseStack.pushPose();
		poseStack.setIdentity();
		poseStack.translate(at.x, at.y + LIFT * pop, at.z);
		// turned to face the camera (the model's front is its -z), swaying
		float face = (float)Math.toDegrees(Mth.atan2(at.x, at.z));
		poseStack.rotateDegrees(Axis.YP, face + SWAY_DEGREES * Mth.sin(time * SWAY_SPEED));
		// upright: the model is built y-down
		poseStack.rotateDegrees(Axis.ZP, 180.0F);
		float scale = HEIGHT * pop * model.renderScale / 2.0F;
		poseStack.scale(scale, scale, scale);
		poseStack.translate(0.0F, -FEET_PX / 16.0F, 0.0F);
		float flicker = 1.0F - FLICKER * (0.5F + 0.5F * Mth.sin(time * FLICKER_SPEED));
		if ((int)(time * 2.0F) % GLITCH_EVERY == 0) {
			flicker *= 0.5F;
		}
		int alpha = Mth.clamp((int)(ALPHA * flicker * Math.min(1.0F, pop * 1.5F) * 255.0F), 0, 255);
		int tint = XenoformPowerClient.refused(player) ? REFUSED_TINT : TINT;
		// submitModelPart draws the part from its rest pose (a fresh Model round it, posed at the flush), so the figure stands still
		collector.submitModelPart(model.root(), poseStack, RenderTypes.eyes(model.texture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, alpha << 24 | tint);
		poseStack.popPose();
	}
}
