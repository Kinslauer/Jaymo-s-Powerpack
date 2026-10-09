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
		if (pose.heavy()) {
			heavy(humanoid, right, t, pose.rise());
			return true;
		}
		arm.xRot = x;
		arm.yRot = 0.0F;
		arm.zRot = right ? 0.08F : -0.08F;
		return true;
	}

	/** A heavy slam: the whole body (from the hip) leans back with both arms flung up, then bends double, hand to the ground. */
	private static final float LEAN_BACK = -0.3F, BEND = 1.3F;
	/** The hip, in model pixels below the neck (the body bends around it, the legs stay put). */
	private static final float HIP = 12.0F;

	private static void heavy(HumanoidModel<?> humanoid, boolean right, float t, int rise) {
		ModelPart main = right ? humanoid.rightArm : humanoid.leftArm;
		ModelPart off = right ? humanoid.leftArm : humanoid.rightArm;
		float out = right ? 1.0F : -1.0F;
		float bend;
		float mainWorld;
		float offWorld;
		float offOut;
		if (t < rise) {
			// wound up: leaning back, the throwing arm thrust high overhead, the other flung up and out behind
			float s = Mth.clamp(t / RAISE_TICKS, 0.0F, 1.0F);
			bend = Mth.lerp(s, 0.0F, LEAN_BACK);
			mainWorld = Mth.lerp(s, CHOKE, -Mth.PI + 0.35F);
			offWorld = Mth.lerp(s, 0.0F, -2.3F);
			offOut = Mth.lerp(s, 0.0F, 0.7F);
		} else {
			// and down: bent double at the hip, the hand almost to the ground, the other arm swung back
			float s = Mth.clamp((t - rise) / (SWING_TICKS + 1.0F), 0.0F, 1.0F);
			float e = s * s;
			bend = Mth.lerp(e, LEAN_BACK, BEND);
			mainWorld = Mth.lerp(e, -Mth.PI + 0.35F, 0.15F);
			offWorld = Mth.lerp(e, -2.3F, 0.9F);
			offOut = Mth.lerp(e, 0.7F, 0.35F);
		}
		// everything above the hip turns about it
		float cos = Mth.cos(bend);
		float sin = Mth.sin(bend);
		humanoid.body.xRot = bend;
		humanoid.body.y = HIP - HIP * cos;
		humanoid.body.z = -HIP * sin;
		humanoid.head.xRot = humanoid.head.xRot + bend * 0.5F;
		humanoid.head.y = humanoid.body.y;
		humanoid.head.z = humanoid.body.z;
		for (ModelPart arm : new ModelPart[] {main, off}) {
			// the shoulder sits 10 above the hip
			arm.y = HIP - (HIP - 2.0F) * cos;
			arm.z = -(HIP - 2.0F) * sin;
		}
		main.xRot = mainWorld - bend;
		main.yRot = 0.0F;
		main.zRot = out * 0.1F;
		off.xRot = offWorld - bend;
		off.yRot = 0.0F;
		off.zRot = -out * offOut;
	}
}
