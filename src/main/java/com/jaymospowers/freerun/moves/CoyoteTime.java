package com.jaymospowers.freerun.moves;

import com.jaymospowers.core.TreeLocks;
import com.jaymospowers.freerun.Freerun;
import net.minecraft.world.entity.player.Player;

/**
 * Coyote time (Coyote Time's idea, CC0, by Artemis): run off an edge and a Jump pressed in the first few ticks of the fall
 * ({@link #GRACE_TICKS}) still jumps, as if the foot had still been on the edge. Only a fresh press counts, never one held
 * from before (so holding Jump never hops you off by itself), only off a step that wasn't a jump, and only once a fall.
 * It is learned: the Coyote Time perk in Kingdoms' Freerunning page ({@link TreeLocks#COYOTE_TIME}); without Kingdoms it's
 * everyone's. The jump is the player's own body, so their client makes it ({@code client/freerun/moves/mixin/CoyoteTimeMixin}).
 */
public final class CoyoteTime {
	// ===== the knobs ================================================================================================
	/** Ticks after leaving the ground a fresh Jump still jumps (a sprint covers about a block and a half in them). */
	public static final int GRACE_TICKS = 6;
	// ================================================================================================================

	private CoyoteTime() {
	}

	/** Whether {@code player} may take a late jump at all now (perk learnt, legs and arms sound, not on a ledge). */
	public static boolean allowed(Player player) {
		return TreeLocks.has(player, TreeLocks.COYOTE_TIME) && Freerun.canMove(player) && !player.isSpectator() && !player.isPassenger()
			&& !player.getAbilities().flying && !player.isFallFlying() && !player.onClimbable() && !player.isInWater() && !player.isInLava();
	}
}
