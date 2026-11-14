package com.jaymospowers.client.freerun.moves;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Moving on ladders, vines, scaffolding and ropes ({@code LadderClimbMixin} on vanilla's own climbing, so Kitchen's rope
 * and Kingdoms' climbing rope get it too). Andy's picks from Better Climbing (CC0, by Artemis; the three ideas and the
 * shape of the hooks are ported from it, the numbers are ours):
 * <ul>
 * <li><b>No slowdown:</b> moving sideways inside the climbable goes at walking pace, not vanilla's crawl.
 * Sneaking in the air still clamps it, so you can hold still on a rung.</li>
 * <li><b>Look down to drop:</b> looking steeply down (and not holding Sneak) slides you down fast instead of creeping.</li>
 * <li><b>Jump from the foot:</b> standing on the ground inside a ladder, Jump is a real jump, not the start of the climb.</li>
 * </ul>
 * Local player only (their movement is theirs; the server follows it).
 */
public final class LadderMoves {
	// ===== the knobs ================================================================================================
	/** Looking down this far (degrees; 90 is straight down) starts to quicken the slide, and this far it's at full speed. */
	public static final float LOOK_FROM = 45.0F, LOOK_FULL = 80.0F;
	/** The fastest slide (blocks/tick; vanilla's own is -0.15). */
	public static final double DROP_SPEED = -0.4;
	// ================================================================================================================

	private LadderMoves() {
	}

	/** Whether {@code player} is on a ladder, vine, rope or scaffolding off the ground (the climb's pose, and its move hint). */
	public static boolean climbing(LocalPlayer player) {
		return !player.onGround() && player.onClimbable() && !player.isPassenger() && !player.isSwimming() && !player.getAbilities().flying;
	}

	/** Whether sideways movement on a climbable goes unclamped for {@code player} (walking pace). */
	public static boolean walksFree(LocalPlayer player) {
		return player.onGround() || !player.isCrouching();
	}

	/** The fastest {@code player} may fall on a climbable this tick (negative), given vanilla's own {@code vanilla}. */
	public static double dropSpeed(LocalPlayer player, double vanilla) {
		float pitch = player.getXRot();
		if (pitch <= LOOK_FROM) {
			return vanilla;
		}
		return Mth.clampedMap((double)pitch, (double)LOOK_FROM, (double)LOOK_FULL, vanilla, DROP_SPEED);
	}
}
