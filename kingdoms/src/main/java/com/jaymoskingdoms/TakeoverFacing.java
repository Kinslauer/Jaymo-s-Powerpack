package com.jaymoskingdoms;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * (2026-11-05, Andy: "takeovers need to face your character toward its walking direction, kind of like how free rein
 * works, if you are looking forward the camera stays forward, but the model is drawn moving in whatever direction")
 * While something else drives a body (a seizure, the Grue, a werewolf player's beast), the body is drawn turning to the
 * way it's carried, never strafing or walking backwards after the camera; the head turns toward where its player looks,
 * as far as a neck can ({@link #HEAD_LIMIT}). Only the drawing changes: the camera and the aim stay the player's.
 */
public final class TakeoverFacing {
	/** The share of the way to its heading the body turns each tick (vanilla's own body turn is 0.3). */
	static final float TURN = 0.35F;
	/** Moved at least this far this tick (squared, blocks): vanilla's own "walking" threshold. */
	static final double MOVING_SQR = 0.0025;
	/** How far (degrees) the head may turn from the body toward where they look. */
	public static final float HEAD_LIMIT = 60.0F;

	private TakeoverFacing() {
	}

	/** Turns {@code entity}'s body toward the way it moved this tick (stays put when standing). Call from tickHeadTurn. */
	public static void turn(LivingEntity entity) {
		double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
		if (dx * dx + dz * dz > MOVING_SQR) {
			float heading = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			entity.yBodyRot += Mth.wrapDegrees(heading - entity.yBodyRot) * TURN;
		}
	}

	/** The head's turn from the body ({@code yRot} of a render state), held to what a neck can do. */
	public static float head(float yRot) {
		return Mth.clamp(Mth.wrapDegrees(yRot), -HEAD_LIMIT, HEAD_LIMIT);
	}
}
