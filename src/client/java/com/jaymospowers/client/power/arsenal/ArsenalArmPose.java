package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalReach;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The weapon arm during Reach Through (the same render-state approach as the other powers' poses): it rises and
 * reaches into the portal while it opens, hauls back toward the chest as the weapon is drawn out, and eases back to
 * normal as the portal closes.
 */
public final class ArsenalArmPose {
	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:arsenal_reach_arm");

	/** Shoulder pivot above the feet and out to the side, and shoulder-to-hand length (model units / 16). */
	private static final double SHOULDER_HEIGHT = 22.0 / 16.0;
	private static final double SHOULDER_OUT = 5.0 / 16.0;
	private static final double ARM_LENGTH = 10.0 / 16.0;

	/** {@code reach}: 0-1 how far into the portal; {@code pull}: 0-1 how far it's hauled back; {@code amount}: blend. */
	public record Pose(float amount, float aimX, float aimY, float pull) {
	}

	private ArsenalArmPose() {
	}

	private static Vec3 shoulder(Player player, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
		double scale = player.getScale();
		return player.getPosition(partialTick).add(0.0, SHOULDER_HEIGHT * scale, 0.0)
			.add(ArsenalReach.right(bodyYaw).scale(ArsenalReach.side(player) * SHOULDER_OUT * scale));
	}

	/** Where the hand is during the pull: out toward the portal, coming back in to the chest (for the emerging weapon). */
	static Vec3 handPos(Player player, float partialTick) {
		Vec3 shoulder = shoulder(player, partialTick);
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
		Vec3 toPortal = ArsenalReach.portalCenter(player, partialTick).subtract(shoulder).normalize();
		Vec3 held = ArsenalReach.forward(bodyYaw).scale(0.8).add(0.0, -0.6, 0.0).normalize();
		float pull = pullAmount(player, partialTick);
		return shoulder.add(toPortal.lerp(held, pull).normalize().scale(ARM_LENGTH * player.getScale()));
	}

	private static float time(Player player, float partialTick) {
		ArsenalReach.Reach reach = player.getAttached(ArsenalReach.REACH);
		Minecraft minecraft = Minecraft.getInstance();
		if (reach == null || minecraft.level == null) {
			return -1.0F;
		}
		return minecraft.level.getGameTime() - reach.start() + partialTick;
	}

	private static float pullAmount(Player player, float partialTick) {
		float t = time(player, partialTick);
		float x = Mth.clamp((t - ArsenalReach.REACH_END) / (ArsenalReach.PULL_END - ArsenalReach.REACH_END), 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		if (!(entity instanceof Player player)) {
			state.setData(KEY, null);
			return;
		}
		float t = time(player, partialTicks);
		if (t < 0.0F || t > ArsenalReach.CLOSE_END) {
			state.setData(KEY, null);
			return;
		}
		// rise into it, hold through the pull, ease off as it closes
		float amount = t < ArsenalReach.OPEN_END ? t / ArsenalReach.OPEN_END
			: t < ArsenalReach.PULL_END ? 1.0F
			: 1.0F - (t - ArsenalReach.PULL_END) / (ArsenalReach.CLOSE_END - ArsenalReach.PULL_END);
		Vec3 shoulder = shoulder(player, partialTicks);
		Vec3 dir = ArsenalReach.portalCenter(player, partialTicks).subtract(shoulder);
		if (dir.lengthSqr() < 1.0E-6) {
			state.setData(KEY, null);
			return;
		}
		dir = dir.normalize();
		float bodyYaw = Mth.lerp(partialTicks, player.yBodyRotO, player.yBodyRot);
		float elevation = (float)Math.asin(Mth.clamp(dir.y, -1.0, 1.0));
		float yaw = (float)(Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
		float relative = Mth.wrapDegrees(yaw - bodyYaw) * Mth.DEG_TO_RAD;
		state.setData(KEY, new Pose(Mth.clamp(amount, 0.0F, 1.0F), -Mth.HALF_PI - elevation, relative, pullAmount(player, partialTicks)));
	}

	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		ModelPart arm = avatar.mainArm == HumanoidArm.LEFT ? humanoid.leftArm : humanoid.rightArm;
		float outward = arm == humanoid.rightArm ? 1.0F : -1.0F;
		// reaching: straight at the portal (pushing in a touch further); pulled: elbow back, weapon at the chest
		float reachX = pose.aimX() - 0.15F;
		float pulledX = -0.9F;
		float x = Mth.lerp(pose.pull(), reachX, pulledX);
		float y = Mth.lerp(pose.pull(), pose.aimY(), -outward * 0.35F);
		float z = Mth.lerp(pose.pull(), 0.0F, outward * 0.15F);
		set(arm, x, y, z, pose.amount());
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
