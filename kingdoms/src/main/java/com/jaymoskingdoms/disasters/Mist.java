package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.KitchenCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;

/**
 * Morning mist as a thing in the world, not only on the screen (Andy: "stalkers using it as cover"). The one place that says
 * how thick the mist lies at a spot, 0 to 1, read by both sides: the client draws it (banks of fog and a shortened view,
 * {@code SkiesClient}) and the server lets it hide things: mobs notice a player less far off in it ({@code Senses#factor}),
 * wolves and creepers that stalk use it for cover and come out to hunt in it by day ({@code WolfStalkers}, {@code Stalkers}),
 * and the Gaunt walks in it.
 *
 * <p>From before dawn to mid-morning, banks of mist lie on low ground and over water: thickest in swamps, then by rivers and
 * coasts, then in woods; more in autumn, less in summer; none in dry lands (where nothing rains or snows), thin high up, and
 * rain clears it. The Overworld only.
 */
public final class Mist {
	// ---- tuning ----
	/** Mist by time of day: when it gathers, when it is full, when it thins, when it is gone (ticks into the day; it wraps past 24000). */
	public static final int FROM = 22300, FULL = 23300, THINS = 1000, GONE = 2600;
	/** Mist by place: swamps, by water, wooded country, anywhere else that rains or snows; and how much of it is left high up. */
	public static final float SWAMP = 1.0F, WATER = 0.8F, WOODS = 0.5F, OPEN = 0.35F, HIGH = 0.2F;
	/** The camera counts as high up this far above the sea. */
	public static final int HIGH_FROM = 40;
	/** Mist by season. */
	public static final float AUTUMN = 1.3F, SUMMER = 0.7F;
	/** In full mist a mob notices a player at this share of its usual range. */
	public static final double SIGHT_SHARE = 0.5;
	/** In full mist nothing farther than this many blocks is seen at all (by a creature that is watching for it). */
	public static final double SEEN_FULL = 9.0;
	/** Mist this thick lets the things that hunt in the dark come out, and hides a stalker from fifteen blocks off. */
	public static final float COVER = 0.4F;
	/** How often (ticks) the season is asked of Kitchen again. */
	static final int SEASON_EVERY = 200;

	private static String season;
	private static long seasonAt = Long.MIN_VALUE;

	private Mist() {
	}

	/** The mist's thickness by the clock alone: 0 outside its hours, 1 in the middle of them. */
	public static float hour(Level level) {
		int time = (int)Math.floorMod(level.getOverworldClockTime(), 24000L);
		if (time >= FROM) {
			return Mth.clamp((time - FROM) / (float)(FULL - FROM), 0.0F, 1.0F);
		}
		return 1.0F - Mth.clamp((time - THINS) / (float)(GONE - THINS), 0.0F, 1.0F);
	}

	/**
	 * How thick the mist would lie at {@code at}, 0..1, before the rain's say ({@link #at} has it): the hour, the place, the
	 * height and the season.
	 */
	public static float base(Level level, Vec3 at) {
		if (level.dimension() != Level.OVERWORLD) {
			return 0.0F;
		}
		float hour = hour(level);
		if (hour <= 0.0F) {
			return 0.0F;
		}
		BlockPos pos = BlockPos.containing(at);
		Holder<Biome> biome = level.getBiome(pos);
		// (dry lands, where nothing rains or snows, have none)
		if (biome.value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.NONE) {
			return 0.0F;
		}
		float place = biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) ? SWAMP
			: biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH) ? WATER
			: biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE) ? WOODS : OPEN;
		if (at.y > level.getSeaLevel() + HIGH_FROM) {
			place *= HIGH;
		}
		String name = season(level);
		float year = "autumn".equals(name) ? AUTUMN : "summer".equals(name) ? SUMMER : 1.0F;
		return Mth.clamp(hour * place * year, 0.0F, 1.0F);
	}

	/** How thick the mist lies at {@code at} now, rain taken off it. On the client this is the world's own rain; the client's fog adds its own easing. */
	public static float at(Level level, Vec3 at) {
		float base = base(level, at);
		if (base <= 0.0F) {
			return 0.0F;
		}
		float rain = Math.max(level.getRainLevel(1.0F), level.isClientSide() ? Storms.clientRain : Storms.cover(level, at.x, at.z));
		return base * (1.0F - Mth.clamp(rain, 0.0F, 1.0F));
	}

	/** Whether the mist at {@code at} is thick enough to hunt in ({@link #COVER}). */
	public static boolean covers(Level level, Vec3 at) {
		return at(level, at) >= COVER;
	}

	/** What a mob's range for noticing something at {@code at} is multiplied by: 1 in clear air, {@link #SIGHT_SHARE} in full mist. */
	public static double sight(Level level, Vec3 at) {
		return 1.0 - (1.0 - SIGHT_SHARE) * at(level, at);
	}

	/**
	 * How far a creature at {@code at} can be seen from, given that in clear air it could be seen from {@code clear} blocks:
	 * all of it in clear air, {@link #SEEN_FULL} blocks (or less, if {@code clear} is shorter) in full mist.
	 */
	public static double seenWithin(Level level, Vec3 at, double clear) {
		return Mth.lerp(at(level, at), clear, Math.min(clear, SEEN_FULL));
	}

	private static String season(Level level) {
		long now = level.getGameTime();
		if (seasonAt == Long.MIN_VALUE || Math.abs(now - seasonAt) > SEASON_EVERY) {
			season = KitchenCompat.season(level);
			seasonAt = now;
		}
		return season;
	}
}
