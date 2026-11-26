package com.jaymoskitchen.season;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.5.6) Snow and ice through the year. Winter makes temperate lands cold enough to snow and to freeze still water (the
 * biome mixin lowers their temperature by {@link Season#chill}, so the game's own weather does the rest). Out of winter,
 * snow and ice left open to the sky there melt away a little at a time ({@code SnowMeltMixin}, {@code IceMeltMixin}): slowly
 * in early spring, faster each day, and fast by summer ({@link #thawPace}); under a roof they keep.
 * <p>
 * The year also sets vanilla's weather clock ({@link #stretch}, {@code ServerLevelWeatherMixin}): winter goes long between
 * thunderstorms, spring and summer storm often, autumn rains often; the hot lands follow the wet and dry seasons.
 */
public final class SeasonalWeather {
	/** The chance a random tick takes a layer off open snow, or melts open ice, at the full pace of the thaw. */
	private static final float SNOW_MELT = 0.35F, ICE_MELT = 0.2F;
	/**
	 * How fast the thaw goes, as a share of that chance: a tenth of it on the first day of spring, rising each day to the
	 * full pace on the first day of summer, then faster through summer and autumn (snow that late is the odd late fall).
	 */
	private static final float THAW_START = 0.1F, THAW_FULL = 1.0F, THAW_SUMMER = 1.4F;

	/** The four spells of vanilla's weather clock that a season stretches or shortens ({@link #stretch}). */
	public enum Spell {
		/** The wait between one rain and the next. */
		CLEAR,
		/** How long a rain lasts. */
		RAIN,
		/** The wait between thunderstorms. */
		CALM,
		/** How long thunder lasts once it starts. */
		THUNDER
	}

	/**
	 * What each season multiplies each spell by, for the calendar's seasons (spring, summer, autumn, winter). Spring and
	 * summer storm often and long; autumn rains often and for long and thunders less; winter goes four times as long between
	 * thunderstorms (vanilla's wait is 10 minutes to 2.5 hours, about 16 game days at x4 on average, and storms are short).
	 */
	private static final float[][] BY_SEASON = {
		//        spring summer autumn winter
		/* CLEAR   */ {0.8F, 1.0F, 0.45F, 1.0F},
		/* RAIN    */ {1.0F, 0.8F, 1.5F, 1.0F},
		/* CALM    */ {0.5F, 0.45F, 1.2F, 4.0F},
		/* THUNDER */ {1.2F, 1.3F, 0.8F, 0.4F}
	};
	/** The same for the hot lands' two seasons ({@link Tropics}): wet, then dry. */
	private static final float[][] BY_RAINS = {
		/* CLEAR   */ {0.35F, 2.2F},
		/* RAIN    */ {1.5F, 0.6F},
		/* CALM    */ {0.5F, 2.5F},
		/* THUNDER */ {1.3F, 0.5F}
	};

	private SeasonalWeather() {
	}

	private static boolean thaws(ServerLevel level, BlockPos pos) {
		return Seasons.of(level) != Season.WINTER && Seasons.temperate(level.getBiome(pos).value().getBaseTemperature())
			&& level.canSeeSky(pos.above());
	}

	/** How fast the thaw goes today, from {@link #THAW_START} on the first day of spring to {@link #THAW_SUMMER}. */
	public static float thawPace(ServerLevel level) {
		Season season = Seasons.of(level);
		if (season == Season.WINTER) {
			return 0.0F;
		}
		if (season != Season.SPRING) {
			return THAW_SUMMER;
		}
		return Mth.lerp(Seasons.dayInSeason(level) / (float)Seasons.DAYS_PER_SEASON, THAW_START, THAW_FULL);
	}

	/**
	 * A spell of weather as vanilla rolled it (ticks), changed for the time of year. Called when the game picks a new
	 * length for a clear wait, a rain, a calm or a thunder; a spell a player or a power sets by hand is never touched.
	 * Where the players stand decides which year it follows: in the hot lands the wet and dry seasons ({@link Tropics}),
	 * everywhere else the calendar's.
	 */
	public static int stretch(ServerLevel level, Spell spell, int rolled) {
		float by;
		if (tropicalHere(level)) {
			by = BY_RAINS[spell.ordinal()][Tropics.wet(Seasons.of(level)) ? 0 : 1];
		} else {
			by = BY_SEASON[spell.ordinal()][Seasons.of(level).ordinal()];
		}
		return Math.max(1, Math.round(rolled * by));
	}

	/** Whether most of the players in this level are in a jungle or savanna. */
	private static boolean tropicalHere(ServerLevel level) {
		List<ServerPlayer> players = level.players();
		int tropical = 0;
		for (ServerPlayer player : players) {
			if (Tropics.at(level, player.blockPosition())) {
				tropical++;
			}
		}
		return tropical * 2 > players.size();
	}

	/** A random tick of a snow layer: true when it melted a little (and the usual tick is skipped). */
	public static boolean meltSnow(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!thaws(level, pos) || random.nextFloat() >= SNOW_MELT * thawPace(level)) {
			return false;
		}
		int layers = state.getValue(SnowLayerBlock.LAYERS);
		if (layers > 1) {
			level.setBlockAndUpdate(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1));
		} else {
			level.removeBlock(pos, false);
		}
		return true;
	}

	/** A random tick of ice: true when it melted to water. */
	public static boolean meltIce(ServerLevel level, BlockPos pos, RandomSource random) {
		if (!thaws(level, pos) || random.nextFloat() >= ICE_MELT * thawPace(level)) {
			return false;
		}
		level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
		return true;
	}
}
