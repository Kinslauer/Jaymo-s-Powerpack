package com.jaymospowers.client.power.gravity;

import com.jaymospowers.power.gravity.GravityForward;
import com.jaymospowers.power.gravity.GravityWalk;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The body turned to its own "down", for everyone to see: walking on a wall it stands out from the wall, feet on it,
 * facing the way it looks along it; on a ceiling it hangs upside down, feet up; falling forward it dives head first
 * along the fall, belly to the ground. (The view stays upright: only the body turns, and not in your own first person.)
 * Since 0.11.40 Chrono's Overclock wall run stands on the wall the same way, facing straight up it, legs running.
 */
public final class GravityWalkPose {
	/** {@code up}: where the head points; {@code front}: where the chest faces; {@code feet}: where the feet go, from the entity's position. */
	public record Turn(Vec3 up, Vec3 front, Vec3 feet) {
	}

	public static final RenderStateDataKey<Turn> KEY = RenderStateDataKey.create(() -> "jaymospowers:gravity_walk");

	private GravityWalkPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		state.setData(KEY, null);
		if (!(entity instanceof Player player) || player.isPassenger()) {
			return;
		}
		// your own first-person view stays upright, so does the body First-person Model draws under it (turned, it
		// would lie across the camera)
		Minecraft minecraft = Minecraft.getInstance();
		if (entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		Integer surface = player.getAttached(GravityWalk.SURFACE);
		boolean wallRun = false;
		if (surface == null) {
			surface = player.getAttached(com.jaymospowers.power.chrono.ChronoSpeed.WALL_RUN);
			wallRun = surface != null;
		}
		Vec3 look = player.getViewVector(partialTicks);
		float height = player.getBbHeight();
		if (surface != null && surface >= 0 && surface < Direction.values().length) {
			Direction face = Direction.from3DDataValue(surface);
			Vec3 into = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
			Vec3 up = into.scale(-1.0);
			// a wall run always faces straight up the wall
			Vec3 front = wallRun ? new Vec3(0.0, 1.0, 0.0) : look.subtract(up.scale(look.dot(up)));
			if (front.lengthSqr() < 0.01) {
				front = face == Direction.UP ? look.horizontal() : new Vec3(0.0, 1.0, 0.0);
			}
			if (front.lengthSqr() < 1.0E-4) {
				front = new Vec3(0.0, 0.0, 1.0);
			}
			// feet against the surface: the ceiling over the head, or the wall beside the body's middle
			Vec3 feet = face == Direction.UP ? new Vec3(0.0, height, 0.0) : new Vec3(0.0, height * 0.5, 0.0).add(into.scale(player.getBbWidth() * 0.5));
			state.setData(KEY, new Turn(up, front.normalize(), feet));
			if (wallRun) {
				// climbing doesn't move the legs by itself (the walk animation only counts sideways movement): run
				state.walkAnimationPos = (player.tickCount + partialTicks) * com.jaymospowers.power.chrono.ChronoSpeed.WALL_STRIDE;
				state.walkAnimationSpeed = 1.0F;
			}
		} else if (player.getAttached(GravityForward.FALLING) != null) {
			Vec3 motion = player.getDeltaMovement();
			Vec3 up = motion.lengthSqr() > 0.04 ? motion.normalize() : look;
			Vec3 down = new Vec3(0.0, -1.0, 0.0);
			Vec3 front = down.subtract(up.scale(down.dot(up)));
			if (front.lengthSqr() < 0.01) {
				front = look.horizontal().lengthSqr() > 1.0E-4 ? look.horizontal() : new Vec3(0.0, 0.0, 1.0);
			}
			// turned about the body's middle
			Vec3 feet = new Vec3(0.0, height * 0.5, 0.0).subtract(up.scale(height * 0.5));
			state.setData(KEY, new Turn(up, front.normalize(), feet));
		}
	}

	/** After the body faces its way (setupRotations' tail): undo that, then stand the body on its own "down". */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		Turn turn = state.getData(KEY);
		if (turn == null || state.deathTime > 0.0F) {
			return;
		}
		// back to the world's axes (the model, unturned, faces -Z here)
		poseStack.rotateDegrees(Axis.YP, -(180.0F - bodyRot));
		Vec3 feet = turn.feet().scale(1.0 / entityScale);
		poseStack.translate(feet.x, feet.y, feet.z);
		// the model's up goes to {@code up}, its +Z (its back) to the opposite of {@code front}
		Vec3 y = turn.up();
		Vec3 z = turn.front().scale(-1.0);
		Vec3 x = y.cross(z);
		Matrix4f m = new Matrix4f(
			(float)x.x, (float)x.y, (float)x.z, 0.0F,
			(float)y.x, (float)y.y, (float)y.z, 0.0F,
			(float)z.x, (float)z.y, (float)z.z, 0.0F,
			0.0F, 0.0F, 0.0F, 1.0F);
		poseStack.mulPose(m);
	}
}
