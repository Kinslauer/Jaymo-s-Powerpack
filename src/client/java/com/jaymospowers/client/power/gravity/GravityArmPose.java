package com.jaymospowers.client.power.gravity;

import com.jaymospowers.power.gravity.GravityPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/**
 * A gravity user holding something with Lift reaches out for it: the main arm points straight at the held creature's
 * head (fully extended for a choke). Everyone's, from the synced grip.
 */
public final class GravityArmPose {
	private GravityArmPose() {
	}

	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Entity self = minecraft.level.getEntity(avatar.id);
		Integer grip = self == null ? null : self.getAttached(GravityPower.GRIP);
		Entity held = grip == null ? null : minecraft.level.getEntity(grip);
		if (held == null) {
			return;
		}
		boolean right = avatar.mainArm != HumanoidArm.LEFT;
		ModelPart arm = right ? humanoid.rightArm : humanoid.leftArm;
		Vec3 shoulder = self.position().add(0.0, self.getBbHeight() * 0.8, 0.0);
		Vec3 head = held.getEyePosition();
		Vec3 to = head.subtract(shoulder);
		double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float relative = Mth.wrapDegrees(yaw - avatar.bodyRot);
		float pitchDown = (float)-Mth.atan2(to.y, horizontal);
		arm.xRot = -Mth.HALF_PI + pitchDown;
		arm.yRot = relative * Mth.DEG_TO_RAD;
		arm.zRot = 0.0F;
	}
}
