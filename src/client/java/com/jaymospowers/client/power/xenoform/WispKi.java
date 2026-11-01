package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

/**
 * (0.18.90) Andy: "wisp should float instead of walk and be made of ki with the current texture overlaid". Wisp's body
 * is light: its skin drawn see-through and fullbright, over shells of pale ki (the Z Fighters' ki glow texture, its
 * wisps rising) that stand out from the body ({@link XenoFormModels.Wisp#shells}), and the whole of it hovers above the
 * ground, bobbing. Its first-person arm is drawn the same way.
 */
final class WispKi {
	static final Identifier KI_TEXTURE = JaymosPowers.id("textures/entity/zfighter_ki_glow.png");
	/** The ki's colour, and how strongly each shell shows (additive), inside out. */
	static final int KI_COLOR = 0x9FE6FF;
	static final float[] SHELL_LIGHT = {0.55F, 0.25F};
	/** How opaque its skin is over the ki (0-255). */
	static final int SKIN_ALPHA = 170;
	/** How high it floats (blocks), and its slow bob. */
	static final float FLOAT = 0.3F, BOB = 0.06F, BOB_SPEED = 0.09F;
	/** How fast the ki's wisps rise, and its breathing. */
	static final float RISE = 0.015F, BREATHE = 0.12F, BREATHE_SPEED = 0.13F;
	static final int FULL_BRIGHT = 0xF000F0;

	private WispKi() {
	}

	/** In the layer's pose (model space, y down): lift it off the ground, unless it's lying (swimming, sleeping). */
	static void lift(PoseStack poseStack, AvatarRenderState state) {
		if (state.pose != Pose.STANDING && state.pose != Pose.CROUCHING) {
			return;
		}
		poseStack.translate(0.0F, -(FLOAT + BOB * Mth.sin(state.ageInTicks * BOB_SPEED)), 0.0F);
	}

	static void submit(XenoFormModels.Wisp wisp, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		collector.order(1).submitModel(wisp, state, poseStack, RenderTypes.entityTranslucentEmissive(wisp.texture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
			ARGB.color(SKIN_ALPHA, 255, 255, 255), null, 0);
		float rise = (state.ageInTicks * RISE) % 1.0F;
		for (int i = 0; i < wisp.shells.length; i++) {
			// each shell takes the body's pose when it's drawn (setupAnim, then the glide's and the band's poses)
			collector.order(2 + i).submitModel(wisp.shells[i], state, poseStack, RenderTypes.energySwirl(KI_TEXTURE, i * 0.31F, -rise * (1 + i)), FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, tint(i, state.ageInTicks), null, 0);
		}
	}

	/** Its arm in first person: {@code limb} is the body's arm, posed; each shell's arm takes the same pose. */
	static void submitArm(XenoFormModels.Wisp wisp, boolean right, ModelPart limb, PoseStack poseStack, SubmitNodeCollector collector, float time) {
		collector.submitModelPart(limb, poseStack, RenderTypes.entityTranslucentEmissive(wisp.texture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
			ARGB.color(SKIN_ALPHA, 255, 255, 255));
		float rise = (time * RISE) % 1.0F;
		for (int i = 0; i < wisp.shells.length; i++) {
			ModelPart arm = right ? wisp.shells[i].rightArm : wisp.shells[i].leftArm;
			arm.loadPose(limb.storePose());
			arm.visible = true;
			collector.submitModelPart(arm, poseStack, RenderTypes.energySwirl(KI_TEXTURE, i * 0.31F, -rise * (1 + i)), FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, null, tint(i, time));
		}
	}

	/** Additive, so the colour's brightness is how strongly it shows. */
	private static int tint(int shell, float time) {
		float light = Mth.clamp(SHELL_LIGHT[shell] * (1.0F + BREATHE * Mth.sin(time * BREATHE_SPEED + shell)), 0.0F, 1.0F);
		return ARGB.color(255, (int)(ARGB.red(KI_COLOR) * light), (int)(ARGB.green(KI_COLOR) * light), (int)(ARGB.blue(KI_COLOR) * light));
	}
}
