package com.jaymospowers.client.armory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * (0.16.2) The flail arm, for everyone: held out at chest height with the wrist turning as the ball goes round, up
 * overhead and down hard for the slam, then back to rest. LashRenderer hangs the chain off the same hand
 * ({@link LashRenderer#arm}), so arm, chain and ball move together.
 */
public final class LashPose {
	private LashPose() {
	}

	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid) || avatar.deathTime > 0.0F) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !(minecraft.level.getEntity(avatar.id) instanceof Player player)) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float[] arm = LashRenderer.arm(player, minecraft.level.getGameTime() + partialTick);
		if (arm == null) {
			return;
		}
		boolean right = avatar.mainArm != HumanoidArm.LEFT;
		ModelPart part = right ? humanoid.rightArm : humanoid.leftArm;
		part.xRot = -arm[0];
		part.yRot = 0.0F;
		part.zRot = right ? arm[1] : -arm[1];
	}
}
