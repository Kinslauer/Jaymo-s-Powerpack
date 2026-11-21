package com.jaymospowers.fighting;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Keep some XP (Clouds and Bits patch 6): dying with the keepInventory gamerule off, a player comes back with
 * {@link #KEPT} of the levels they had (rounded down; the progress to the next level starts again). With keepInventory on
 * the game already keeps it all, and coming back from the End is no death. The orbs a death drops are the game's own.
 */
public final class KeepXp {
	/** The share of their levels a player keeps on dying. */
	static final float KEPT = 0.4F;

	private KeepXp() {
	}

	public static void init() {
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && !oldPlayer.isSpectator() && !oldPlayer.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
				newPlayer.setExperienceLevels((int) (oldPlayer.experienceLevel * KEPT));
			}
		});
	}
}
