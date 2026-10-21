package com.jaymospowers.core;

import net.minecraft.world.entity.player.Player;

/**
 * (0.14.8) Fighting bare-handed, what you pick up never lands in your fist: while you've hit or been hit by a mob in the
 * last {@link #COMBAT_TICKS}, an empty selected hotbar slot is skipped when a picked-up item looks for a free slot
 * (mixin/FistGuardInventoryMixin), so the item goes to the next free slot, or stays on the ground if there is none.
 * Our own take on the one feature Andy wanted from Locked In Slots (Fuzss, MPL-2.0; nothing copied), and it works the
 * same for vanilla pickup and Interactic's right-click pickup, which both go through {@code Inventory.add}.
 */
public final class FistGuard {
	/** How long after the last blow, given or taken, you still count as fighting: 10 seconds. */
	public static final int COMBAT_TICKS = 200;

	private FistGuard() {
	}

	/** Whether {@code player}'s empty selected slot should be kept empty right now. */
	public static boolean guarding(Player player) {
		if (player.level().isClientSide() || player.isSpectator()) {
			return false;
		}
		return recent(player, player.getLastHurtMobTimestamp()) || recent(player, player.getLastHurtByMobTimestamp());
	}

	private static boolean recent(Player player, int timestamp) {
		return timestamp > 0 && player.tickCount - timestamp < COMBAT_TICKS;
	}
}
