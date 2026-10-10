package com.jaymoskitchen.client.corpse;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/**
 * A player carrying a corpse holds both arms out in front, under it. Set on the player's render state by
 * {@code AvatarRendererMixin}; applied (to their body and their armour alike) after the model animates.
 */
public final class CarryPose {
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:carrying");

	private CarryPose() {
	}

	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !Boolean.TRUE.equals(avatar.getData(KEY))
			|| !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		// reaching forward and down, angled in a little, with a slight strain
		float strain = Mth.sin(avatar.ageInTicks * 0.067F) * 0.03F;
		humanoid.rightArm.xRot = -0.8F + strain;
		humanoid.rightArm.yRot = -0.3F;
		humanoid.rightArm.zRot = 0.0F;
		humanoid.leftArm.xRot = -0.8F - strain;
		humanoid.leftArm.yRot = 0.3F;
		humanoid.leftArm.zRot = 0.0F;
	}
}
