package com.jaymospowers.freerun.moves;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Swimming in shallow water (Andy's pick from Moves Like Mafuyu's shallow swimming; the code is ours): vanilla only lets
 * a sprint turn into a swim once your eyes are under the water, so wading through one block of water, or running into a
 * pool, is a walk. Now sprinting while touching water starts the swim at once, in a single block of water or right as
 * you enter it ({@code ShallowSwimMixin} on {@code Entity.updateSwimming}, both sides; {@code ShallowSprintMixin} lets the
 * sprint last in the shallows on the client). The sprint swim is vanilla's, so {@code SwimStrokePose} and the
 * {@link FastSwim} come with it.
 */
public final class ShallowSwim {
	// ===== the knob =================================================================================================
	/** The water must cover this much of the body (blocks of depth in the body's box), so a trickle of flowing water isn't a pool. */
	public static final double MIN_DEPTH = 0.4;
	// ================================================================================================================

	private ShallowSwim() {
	}

	/** Whether {@code entity} is in water deep enough to swim in without its eyes under it (a player not flying). */
	public static boolean allowed(Entity entity) {
		return entity instanceof Player player && player.isInWater() && !player.getAbilities().flying && !player.isSpectator()
			&& player.getFluidHeight(FluidTags.WATER) >= MIN_DEPTH;
	}
}
