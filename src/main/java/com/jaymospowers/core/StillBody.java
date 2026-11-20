package com.jaymospowers.core;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Andy (2026-11-20): "When players are prone or sitting or crouched they should stay in their orientation, right now
 * they swivel around with the camera. Only their head and pov should do that." Lying flat (crawling by the gesture or
 * under a low ceiling, downed, on broken legs), sat (on the ground or a seat: {@link Meditate}) or crouched, a player
 * standing still keeps the way their body faces however the camera turns; only the head turns after it, as far as a
 * neck goes ({@link #HEAD_LIMIT}, drawn by the client's StillBodyHeadMixin), and the view turns freely. Moving, the body
 * swings round to the way they go at vanilla's pace, and catches up with the look only past the neck's reach, eased,
 * never the snap vanilla does. Swinging or using an item (a bow drawn, a crossbow aimed, a bite of food) turns them as
 * vanilla does, so the arms stay on what they aim at. Worked out the same on every side (mixin.StillBodyMixin), so
 * everyone sees the body the same way.
 */
public final class StillBody {
	/** How far (degrees) the head may turn from a held body toward where they look. */
	public static final float HEAD_LIMIT = 75.0F;
	/** Moved at least this far this tick (squared, blocks): vanilla's own "walking" threshold. */
	static final double MOVING_SQR = 0.0025;
	/** The share of the way to its heading the body turns each tick, moving (vanilla's own body turn). */
	static final float TURN = 0.3F;
	/** Moving with the look past {@link #HEAD_LIMIT}: the share of the overshoot the body takes up each tick. */
	static final float CATCH_UP = 0.25F;

	private StillBody() {
	}

	/** Whether this player's body keeps its facing now (prone, sat or crouched, and not turning to strike or aim). */
	public static boolean held(Player player) {
		if (player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.isSpectator()
			|| player.isUsingItem() || player.isSwinging()) {
			return false;
		}
		if (player.isVisuallyCrawling() || player.isCrouching()) {
			return true;
		}
		Meditate.State sat = Meditate.of(player);
		return sat != null && sat.kind() == Meditate.MEDITATING;
	}

	/**
	 * In place of vanilla's {@code tickHeadTurn} for a held body ({@code heading}: the way vanilla would turn it, the way
	 * they move or, still, where it already faces).
	 */
	public static void turn(Player player, float heading) {
		double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
		if (dx * dx + dz * dz <= MOVING_SQR) {
			// still: the body stays as it is
			return;
		}
		player.yBodyRot += Mth.wrapDegrees(heading - player.yBodyRot) * TURN;
		float head = Mth.wrapDegrees(player.getYRot() - player.yBodyRot);
		if (Math.abs(head) > HEAD_LIMIT) {
			player.yBodyRot += (head - Math.signum(head) * HEAD_LIMIT) * CATCH_UP;
		}
	}

	/** The head's turn from a held body ({@code yRot} of a render state), held to what a neck can do. */
	public static float head(float yRot) {
		return Mth.clamp(Mth.wrapDegrees(yRot), -HEAD_LIMIT, HEAD_LIMIT);
	}
}
