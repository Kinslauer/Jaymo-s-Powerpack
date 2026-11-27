package com.jaymospowers.body;

import com.jaymospowers.JaymosPowers;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Wading, after Punchy Moves' idea (the code is our own): standing on the bottom with the water at the waist or higher
 * but your head still out, you walk slower and your arms come up out of it (WadePose draws that). Swimming is
 * another thing and is left alone. Applied each tick by whoever moves the player (the client at once, the server so it holds).
 */
public final class Wading {
	// ===== the knobs ================================================================================================
	/** Water this deep over the feet (blocks) is waist-deep; deeper than the eyes is swimming, not wading. */
	public static final double WAIST = 0.85, CHEST = 1.35;
	/** The slowdown (a share of walking speed taken off), at the waist and at the chest. */
	static final double SLOW_WAIST = -0.2, SLOW_CHEST = -0.4;
	// ================================================================================================================
	private static final Identifier ID = JaymosPowers.id("wading");

	private Wading() {
	}

	/** On the bottom, in water that reaches the waist, head clear (any side, any player). */
	public static boolean wading(LivingEntity player) {
		if (!player.onGround() || player.isPassenger() || player.isSwimming() || player.isUnderWater() || player.isSpectator()) {
			return false;
		}
		return player.getFluidHeight(FluidTags.WATER) >= WAIST;
	}

	/** Slows the walk while {@link #wading}, deeper water slowing more; a walk out of it gets its speed back. */
	public static void slow(Player player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (!wading(player)) {
			if (speed.hasModifier(ID)) {
				speed.removeModifier(ID);
			}
			return;
		}
		double amount = player.getFluidHeight(FluidTags.WATER) >= CHEST ? SLOW_CHEST : SLOW_WAIST;
		AttributeModifier now = speed.getModifier(ID);
		if (now == null || now.amount() != amount) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
