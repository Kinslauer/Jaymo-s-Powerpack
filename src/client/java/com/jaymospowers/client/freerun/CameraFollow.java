package com.jaymospowers.client.freerun;

import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.power.web.WebWallRun;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The first-person view follows the body (Andy's pick from Simple Parkour's camera; the code is ours). Never a change of
 * field of view. Drawn from {@code ScreenShakeMixin} with the other view turns, like them in view space (a turn about +Z
 * leans the top of the view to the right):
 * <ul>
 * <li><b>Wall run:</b> the view rolls a little away from the wall while you run along it ({@link Freerun#WALL},
 * {@code WebWallRun#WALL}), eased in and out. {@link #WALL_RUN_ROLL}.</li>
 * <li><b>Landing roll:</b> the dip and <b>dodge roll:</b> the roll-turn were already there: {@code DodgeRollPose#applyCamera}
 * turns the view over with every roll (a landing roll is the dodge), dipping at its middle; their own constants are
 * {@code DodgeRollPose.VIEW_DIP} (blocks) and {@code VIEW_ROLL} (share of the full turn), 0 for none.</li>
 * </ul>
 */
public final class CameraFollow {
	// ===== the knobs: 0 turns each off =============================================================================
	/** How far the view rolls away from the wall in a wall run (degrees). */
	public static final float WALL_RUN_ROLL = 3.0F;
	/** How fast the roll eases in and out (share of the way to where it's going, each tick). */
	static final float EASE = 0.2F;
	// ================================================================================================================

	/** The wall run's roll now and a tick ago (degrees, + to the right). */
	private static float wallRoll, lastWallRoll;

	private CameraFollow() {
	}

	/** From {@code JaymosPowersClient}. */
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(CameraFollow::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		lastWallRoll = wallRoll;
		wallRoll += (wallRunRoll(player) - wallRoll) * EASE;
		if (Math.abs(wallRoll) < 0.001F) {
			wallRoll = 0.0F;
		}
	}

	/** Where the wall run's roll is heading: away from the wall while running along one, else 0. */
	private static float wallRunRoll(LocalPlayer player) {
		if (player == null || WALL_RUN_ROLL == 0.0F || player.isPassenger()) {
			return 0.0F;
		}
		Integer face = player.getAttached(Freerun.WALL);
		if (face == null) {
			face = player.getAttached(WebWallRun.WALL);
		}
		if (face == null) {
			return 0.0F;
		}
		Direction wall = Direction.from3DDataValue(face);
		// the way it runs: its own motion, or where the view faces when it has hardly moved
		Vec3 heading = new Vec3(player.getX() - player.xo, 0.0, player.getZ() - player.zo);
		if (heading.lengthSqr() < 1.0E-4) {
			heading = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		heading = heading.normalize();
		// the right of the heading is (-z, x): positive when the wall is on the right
		double side = -wall.getStepX() * heading.z + wall.getStepZ() * heading.x;
		return side == 0.0 ? 0.0F : (side > 0.0 ? -WALL_RUN_ROLL : WALL_RUN_ROLL);
	}

	/** From {@code ScreenShakeMixin}: the view rolls away from the wall. */
	public static void applyCamera(PoseStack poseStack, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		float roll = Mth.lerp(partialTicks, lastWallRoll, wallRoll);
		if (roll != 0.0F) {
			poseStack.rotateDegrees(Axis.ZP, roll);
		}
	}
}
