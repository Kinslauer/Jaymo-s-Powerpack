package com.jaymospowers.power.darktide;

import com.jaymospowers.core.Powers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Darkness feeds it (Andy's Awakening pick: "Darktide (and Smoke, if you like)"). Everything a Darktide or Smoke user hurts,
 * by any move or any blow, is hurt harder the darker it is where they stand and less in daylight or by a torch: from
 * {@link #DARK_BONUS} more at light {@link #DARK_LIGHT} and under, down to {@link #LIGHT_SHARE} of it at light
 * {@link #BRIGHT_LIGHT} and over (block light and the sky's light as the time of day dims it). Darktide's water half is stronger
 * wet as well ({@link #WET_BONUS} in the rain or standing in water: "Carry your water"). Applied on the hurt path
 * ({@code DarktideDarkHurtMixin}).
 */
public final class DarktideDark {
	// ---- tuning ----
	/** Light at or under which it is dark, at or over which it is bright (0-15). */
	static final int DARK_LIGHT = 3, BRIGHT_LIGHT = 12;
	/** The harm in the dark, and in the light (shares of the harm). */
	static final float DARK_BONUS = 1.25F, LIGHT_SHARE = 0.8F;
	/** Darktide's harm in the rain or standing in water. */
	static final float WET_BONUS = 1.25F;

	private DarktideDark() {
	}

	/** How much harder {@code player}'s harm lands for the light where they stand (1 between dark and bright's middle). */
	public static float factor(Player player) {
		Level level = player.level();
		int light = level.getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition()));
		if (light <= DARK_LIGHT) {
			return DARK_BONUS;
		}
		if (light >= BRIGHT_LIGHT) {
			return LIGHT_SHARE;
		}
		float t = (light - DARK_LIGHT) / (float)(BRIGHT_LIGHT - DARK_LIGHT);
		return DARK_BONUS + (LIGHT_SHARE - DARK_BONUS) * t;
	}

	/** Light 0 where {@code pos} is: the full dark. */
	static boolean fullDark(Level level, BlockPos pos) {
		return level.getMaxLocalRawBrightness(pos) <= 0;
	}

	/** From the hurt path: harm by a Darktide or Smoke user, for the light (and Darktide's for the wet). */
	public static float incoming(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0.0F || target.level().isClientSide() || !(source.getEntity() instanceof Player player) || player == target) {
			return amount;
		}
		boolean darktide = DarktidePower.has(player);
		var smoke = Powers.byName("smoke");
		if (!darktide && (smoke == null || !Powers.has(player, smoke))) {
			return amount;
		}
		float out = amount * factor(player);
		if (darktide && player.isInWaterOrRain()) {
			out *= WET_BONUS;
		}
		return out;
	}
}
