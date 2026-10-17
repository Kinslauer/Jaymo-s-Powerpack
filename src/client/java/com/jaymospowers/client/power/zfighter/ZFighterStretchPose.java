package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterStretchCombo;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.13.30) A Verdant's stretched combo blow ({@link ZFighterStretchCombo}), on everyone: over the combo's own move
 * ({@code ZFighterComboPose}), the striking arm (a leg for a kick, both arms for the slam) swings round to the foe and is
 * drawn out long to reach it, then snaps back. The slam's arms come down onto it from overhead.
 */
public final class ZFighterStretchPose {
	/** The limb, how far it's swung onto the line to the foe (0-1), the line against the body (radians), and how long it's drawn. */
	public record Pose(int limb, float aim, float pitch, float yaw, float scale) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_stretch");
	/** A limb's 12 pixels, in blocks; the shoulders' and hips' height and how far out to the side (blocks, at scale 1). */
	static final float LIMB = 0.75F;
	static final double SHOULDER_UP = 1.375, SHOULDER_OUT = 0.3125, HIP_UP = 0.75, HIP_OUT = 0.12;
	/** How fast the limb swings onto the line as it starts out (times its stretch). */
	static final float AIM_RATE = 2.5F;
	/** The slam: how far above the line the arms start (radians), raised overhead and brought down. */
	static final float SLAM_RAISE = 1.6F;

	private ZFighterStretchPose() {
	}

	static void extract(Avatar entity, AvatarRenderState state, float partial) {
		state.setData(KEY, entity instanceof Player player ? pose(player, state, partial) : null);
	}

	private static @Nullable Pose pose(Player player, AvatarRenderState state, float partial) {
		ZFighterStretchCombo.Reach reach = player.getAttached(ZFighterStretchCombo.REACH);
		if (reach == null) {
			return null;
		}
		long now = player.level().getGameTime();
		float out = ZFighterStretchCombo.out(reach, now, partial);
		if (out <= 0.0F) {
			return null;
		}
		int limb = reach.limb();
		boolean leg = limb == ZFighterStretchCombo.RIGHT_LEG || limb == ZFighterStretchCombo.LEFT_LEG;
		boolean left = limb == ZFighterStretchCombo.LEFT_ARM || limb == ZFighterStretchCombo.LEFT_LEG;
		double scale = player.getScale();
		float body = state.bodyRot;
		Vec3 pos = player.getPosition(partial);
		// the root of the limb (the slam's from between the shoulders)
		double side = limb == ZFighterStretchCombo.BOTH_ARMS ? 0.0 : (leg ? HIP_OUT : SHOULDER_OUT) * (left ? -1.0 : 1.0);
		double rad = body * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Math.cos(rad), 0.0, -Math.sin(rad));
		Vec3 root = pos.add(0.0, (leg ? HIP_UP : SHOULDER_UP) * scale, 0.0).add(right.scale(side * scale));
		Entity target = reach.target() < 0 ? null : player.level().getEntity(reach.target());
		Vec3 to;
		if (target != null) {
			to = target.getPosition(partial).add(0.0, target.getBbHeight() * (leg ? 0.4 : 0.6), 0.0);
		} else {
			to = player.getEyePosition(partial).add(player.getViewVector(partial).scale(reach.reach()));
		}
		Vec3 d = to.subtract(root);
		double length = d.length();
		if (length < 1.0E-3) {
			return null;
		}
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float)(Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float)(-(Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD;
		if (limb == ZFighterStretchCombo.BOTH_ARMS && ZFighterStretchCombo.outward(reach, now, partial)) {
			// brought down from overhead onto it
			pitch -= SLAM_RAISE * (1.0F - out);
		}
		float drawn = 1.0F + Math.max(0.0F, (float)(length / scale) - LIMB) / LIMB * out;
		return new Pose(limb, Math.min(1.0F, out * AIM_RATE), pitch, Mth.wrapDegrees(yaw - body) * Mth.DEG_TO_RAD, drawn);
	}

	/** After the combo's move: the stretching limb laid along the line to the foe, drawn out long. */
	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h) || avatar.deathTime > 0.0F) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		switch (pose.limb()) {
			case ZFighterStretchCombo.RIGHT_ARM -> stretch(h.rightArm, pose, 0.0F);
			case ZFighterStretchCombo.LEFT_ARM -> stretch(h.leftArm, pose, 0.0F);
			case ZFighterStretchCombo.RIGHT_LEG -> stretch(h.rightLeg, pose, 0.0F);
			case ZFighterStretchCombo.LEFT_LEG -> stretch(h.leftLeg, pose, 0.0F);
			default -> {
				// the slam: both arms, a little apart
				stretch(h.rightArm, pose, -0.12F);
				stretch(h.leftArm, pose, 0.12F);
			}
		}
	}

	private static void stretch(ModelPart part, Pose pose, float spread) {
		float a = pose.aim();
		part.xRot = Mth.lerp(a, part.xRot, -Mth.HALF_PI + pose.pitch());
		part.yRot = Mth.lerp(a, part.yRot, pose.yaw() + spread);
		part.zRot = Mth.lerp(a, part.zRot, 0.0F);
		part.yScale = pose.scale();
	}
}
