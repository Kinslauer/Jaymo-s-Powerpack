package com.jaymoskitchen.season;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hot lands' year. Jungles and savannas (the biome tag {@code jaymoskitchen:tropical}, which also takes any land another
 * mod tags as a jungle or savanna) don't turn with the four seasons: they have a <b>wet season</b>, the calendar's summer
 * and autumn, and a <b>dry season</b>, its winter and spring. In the wet season the rains come often and long, tropical
 * crops (the block tag {@code jaymoskitchen:seasons/tropical}) grow, and thirst is as usual; in the dry season the rain stays
 * away, those crops wait, you thirst faster ({@link #DRY_THIRST}) and fire spreads more readily ({@link #DRY_FIRE}, see
 * {@code FireDryMixin}). No frost ever kills a crop there. Everything else about the calendar ({@link Seasons}) is untouched.
 */
public final class Tropics {
	public static final TagKey<Biome> TROPICAL = TagKey.create(Registries.BIOME, JaymosKitchen.id("tropical"));
	public static final TagKey<Block> CROPS = TagKey.create(Registries.BLOCK, JaymosKitchen.id("seasons/tropical"));
	/** How much faster a body thirsts out in the dry season (1.0 is the usual rate). */
	private static final float DRY_THIRST = 1.4F;
	/** How much likelier fire is to catch a neighbouring block in the dry season. */
	public static final float DRY_FIRE = 1.6F;

	private Tropics() {
	}

	/** Whether the calendar's season is a wet one in the tropics. */
	public static boolean wet(Season season) {
		return season == Season.SUMMER || season == Season.AUTUMN;
	}

	public static boolean at(Level level, BlockPos pos) {
		return level.getBiome(pos).is(TROPICAL);
	}

	/** Whether this spot is in the tropics and it's the dry season. */
	public static boolean dryAt(Level level, BlockPos pos) {
		return at(level, pos) && !wet(Seasons.of(level));
	}

	/** Whether a tropical crop out in the dry season waits (a roof or glass shelters it, as it does elsewhere). */
	public static boolean waits(BlockState state, ServerLevel level, BlockPos pos) {
		return state.is(CROPS) && !wet(Seasons.of(level)) && !SeasonalCrops.sheltered(level, pos);
	}

	/** How much faster this player thirsts for the weather where they stand. */
	public static float thirstFactor(Player player) {
		return dryAt(player.level(), player.blockPosition()) ? DRY_THIRST : 1.0F;
	}
}
