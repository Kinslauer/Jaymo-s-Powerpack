package com.jaymospowers.power.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The cold slows the wind: where it would snow (the biome's temperature at the player's own height, so a mountain top
 * counts and the weather doesn't matter), every Wind ability takes {@link #EXTRA_WINDUP_TICKS} longer to wind up (charges,
 * hold thresholds, cast delays) and {@link #EXTRA_COOLDOWN_TICKS} longer to come back. Both sides: the HUD shows a frost
 * mark, and the client's own hold thresholds stretch with it.
 */
public final class WindCold {
	/** Extra wind-up in the cold (ticks, 3 s). */
	public static final int EXTRA_WINDUP_TICKS = 60;
	/** Extra cooldown in the cold (ticks, 3 s). */
	public static final int EXTRA_COOLDOWN_TICKS = 60;

	private WindCold() {
	}

	/** Whether it would snow where this player stands. */
	public static boolean isCold(Player player) {
		Level level = player.level();
		BlockPos pos = BlockPos.containing(player.getEyePosition());
		return level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
	}

	/** A wind-up (ticks), stretched in the cold. */
	public static int windup(Player player, int ticks) {
		return isCold(player) ? ticks + EXTRA_WINDUP_TICKS : ticks;
	}

	/** A cooldown (ticks), stretched in the cold. */
	public static int cooldown(Player player, int ticks) {
		return isCold(player) ? ticks + EXTRA_COOLDOWN_TICKS : ticks;
	}
}
