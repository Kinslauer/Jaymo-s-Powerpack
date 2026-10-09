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
		if (pose.style() > 0) {
			heavy(humanoid, right, t, pose.rise(), STYLES[(pose.style() - 1) % STYLES.length]);
		} else {
			arm.xRot = x;
			arm.yRot = 0.0F;
			arm.zRot = right ? 0.08F : -0.08F;
		}
		if (pose.aerial()) {
			flip(humanoid, t, pose.rise());
		}
		return true;
	}

	/** In mid-air, the slam throws the whole body over: heels over head as it comes down (turning about the middle). */
	private static final float FLIP = Mth.PI * 0.95F, FLIP_TICKS = 5.0F, MIDDLE = 12.0F;

	private static void flip(HumanoidModel<?> humanoid, float t, int rise) {
		if (t < rise) {
			return;
		}
		float s = Mth.clamp((t - rise) / FLIP_TICKS, 0.0F, 1.0F);
		float a = FLIP * (1.0F - (1.0F - s) * (1.0F - s));
		ModelPart root = humanoid.root();
		root.xRot += a;
		root.y += MIDDLE - MIDDLE * Mth.cos(a);
		root.z += -MIDDLE * Mth.sin(a);
	}

	/**
	 * A full-body slam, from the hip: wound up ({@code wind}), then driven down ({@code down}). Each says how far the
	 * body bends forward at the hip (radians, back is negative), how far it twists (+ turns the throwing shoulder back),
	 * and where each arm points (pitch in the world, - is up/forward; and how far out to its side).
	 */
	private record Keys(float bend, float twist, float mainX, float mainOut, float offX, float offOut) {
	}

	private record Style(Keys wind, Keys down) {
	}

	private static final float UP = -Mth.PI;
	private static final Style[] STYLES = {
		// 1: one hand thrust overhead, the other flung up behind; bent double, the hand almost to the ground
		new Style(new Keys(-0.3F, 0.0F, UP + 0.35F, 0.1F, -2.3F, 0.7F), new Keys(1.3F, 0.0F, 0.15F, 0.1F, 0.9F, 0.35F)),
		// 2: a two-handed hammer: both hands together high over the head, leaning well back, then straight down
		new Style(new Keys(-0.45F, 0.0F, UP + 0.15F, -0.3F, UP + 0.15F, -0.3F), new Keys(1.35F, 0.0F, 0.1F, -0.2F, 0.1F, -0.2F)),
		// 3: one hand, twisting: wound round with the arm up and out to the side, the other pointing; then across and down
		new Style(new Keys(-0.2F, 0.6F, -2.6F, 1.0F, -1.3F, -0.2F), new Keys(1.2F, -0.35F, 0.2F, -0.35F, 0.8F, 0.5F)),
		// 4: both arms up and out in a V, then both brought down together to the ground
		new Style(new Keys(-0.35F, 0.0F, -2.9F, 0.65F, -2.9F, 0.65F), new Keys(1.3F, 0.0F, 0.05F, 0.15F, 0.05F, 0.15F)),
	};
	/** The hip, in model pixels below the neck (the body bends around it, the legs stay put). */
	private static final float HIP = 12.0F;

	private static void heavy(HumanoidModel<?> humanoid, boolean right, float t, int rise, Style style) {
		ModelPart main = right ? humanoid.rightArm : humanoid.leftArm;
		ModelPart off = right ? humanoid.leftArm : humanoid.rightArm;
		float side = right ? 1.0F : -1.0F;
		Keys from;
		Keys to;
		float s;
		if (t < rise) {
			// winding up out of the choke
			from = new Keys(0.0F, 0.0F, CHOKE, 0.0F, 0.0F, 0.0F);
			to = style.wind();
			s = Mth.clamp(t / RAISE_TICKS, 0.0F, 1.0F);
		} else {
			// and down, hard (faster and faster)
			from = style.wind();
			to = style.down();
			float u = Mth.clamp((t - rise) / (SWING_TICKS + 1.0F), 0.0F, 1.0F);
			s = u * u;
		}
		float bend = Mth.lerp(s, from.bend(), to.bend());
		float twist = Mth.lerp(s, from.twist(), to.twist()) * side;
		// everything above the hip turns about it: bent forward (about x), then twisted (about y)
		place(humanoid.body, bend, twist);
		place(humanoid.head, bend, twist);
		place(main, bend, twist);
		place(off, bend, twist);
		humanoid.body.xRot = bend;
		humanoid.body.yRot = twist;
		humanoid.head.xRot += bend * 0.5F;
		main.xRot = Mth.lerp(s, from.mainX(), to.mainX()) - bend;
		main.yRot = twist;
		main.zRot = side * Mth.lerp(s, from.mainOut(), to.mainOut());
		off.xRot = Mth.lerp(s, from.offX(), to.offX()) - bend;
		off.yRot = twist;
		off.zRot = -side * Mth.lerp(s, from.offOut(), to.offOut());
	}

	/** Moves a part's pivot as the upper body bends and twists about the hip (its rest pivot is where it is now). */
	private static void place(ModelPart part, float bend, float twist) {
		float vx = part.x;
		float vy = part.y - HIP;
		float vz = part.z;
		float cb = Mth.cos(bend), sb = Mth.sin(bend);
		float y1 = vy * cb - vz * sb;
		float z1 = vy * sb + vz * cb;
		float ct = Mth.cos(twist), st = Mth.sin(twist);
		part.x = vx * ct + z1 * st;
		part.y = HIP + y1;
		part.z = -vx * st + z1 * ct;
	}
}
