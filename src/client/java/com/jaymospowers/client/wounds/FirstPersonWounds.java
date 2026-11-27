package com.jaymospowers.client.wounds;

import com.jaymospowers.client.Dismembered;
import com.jaymospowers.client.blood.BloodSettings;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Prosthetics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Your own arm in first person: a lost arm is drawn as the short stump it is ({@link Dismembered#STUB} of the arm, its end
 * at the same place the hand would have been), and the marks on the arm ({@link WoundMarksLayer}) are drawn over it, the
 * stump's raw or scarred end among them. Nothing is drawn while blood is off or streamer safe is on, and the arm is whole then.
 */
public final class FirstPersonWounds {
	/** How long an arm is (model units), so a stump's pivot can be moved to keep its end where the hand was. */
	static final float ARM_LENGTH = 12.0F;
	private static float yScale, y;
	private static boolean shortened;

	private FirstPersonWounds() {
	}

	/** Before the game draws your arm: a lost arm is shortened to its stump (not one with a prosthetic fitted, which is drawn as it is). */
	public static void begin(ModelPart arm, PlayerModel model) {
		if (shortened) {
			// last frame's draw was cut short (another mod cancelled it) before end() put the arm back
			arm.yScale = yScale;
			arm.y = y;
			shortened = false;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || BloodSettings.streamerSafe() || BloodSettings.off()) {
			return;
		}
		int limb = arm == model.rightArm ? Dismember.RIGHT_ARM : Dismember.LEFT_ARM;
		if ((Dismember.missing(player) & limb) == 0 || (Prosthetics.fitted(player) & limb) != 0) {
			return;
		}
		shortened = true;
		yScale = arm.yScale;
		y = arm.y;
		arm.yScale *= Dismembered.STUB;
		// the arm hangs from its shoulder: lowered so that the stump's end is where the hand's was
		arm.y += ARM_LENGTH * (1.0F - Dismembered.STUB);
	}

	/** After the game draws your arm: it is back as it was, and its marks are drawn over it. */
	public static void end(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel model) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || BloodSettings.streamerSafe() || BloodSettings.off()) {
			return;
		}
		@Nullable Identifier marks = WoundMarksLayer.handOverlay(player);
		if (marks != null) {
			arm.visible = true;
			collector.submitModelPart(arm, poseStack, RenderTypes.entityCutout(marks), light, OverlayTexture.NO_OVERLAY, null);
		}
		if (shortened) {
			arm.yScale = yScale;
			arm.y = y;
			shortened = false;
		}
	}
}
