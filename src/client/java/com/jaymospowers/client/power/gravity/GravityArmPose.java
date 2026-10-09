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
 * head (raised high for a choke). A slam throws the arm up overhead as the creature is hauled up, then brings it down
 * hard as it plummets. Everyone's, from the synced grip and slam pose.
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
		if (self != null && slam(humanoid, avatar, self, minecraft.level.getGameTime())) {
			return;
		}
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

	/** Ticks to bring the arm up overhead, and to swing it down; how long it stays down after the hit. */
	private static final float RAISE_TICKS = 3.0F, SWING_TICKS = 3.0F, HOLD_AFTER = 6.0F;
	/** Give up on a slam that never reports landing. */
	private static final float MAX_POSE = 50.0F;
	private static final float OVERHEAD = -Mth.PI + 0.25F, DOWN = -0.35F, CHOKE = -Mth.HALF_PI - 0.6F;

	private static boolean slam(HumanoidModel<?> humanoid, AvatarRenderState avatar, Entity self, long gameTime) {
		GravityPower.SlamPose pose = self.getAttached(GravityPower.SLAM_POSE);
		if (pose == null) {
			return false;
		}
		float partial = avatar.ageInTicks - (float)Math.floor(avatar.ageInTicks);
		float t = gameTime - pose.start() + partial;
		if (t < 0.0F || t > MAX_POSE || pose.landed() >= 0 && gameTime - pose.landed() + partial > HOLD_AFTER) {
			return false;
		}
		float x;
		if (t < pose.rise()) {
			// up it goes, and the arm with it
			x = Mth.lerp(Mth.clamp(t / RAISE_TICKS, 0.0F, 1.0F), CHOKE, OVERHEAD);
		} else {
			// and down, hard (faster and faster)
			float s = Mth.clamp((t - pose.rise()) / SWING_TICKS, 0.0F, 1.0F);
			x = Mth.lerp(s * s, OVERHEAD, DOWN);
		}
		boolean right = avatar.mainArm != HumanoidArm.LEFT;
		ModelPart arm = right ? humanoid.rightArm : humanoid.leftArm;
		arm.xRot = x;
		arm.yRot = 0.0F;
		arm.zRot = right ? 0.08F : -0.08F;
		return true;
	}
}
