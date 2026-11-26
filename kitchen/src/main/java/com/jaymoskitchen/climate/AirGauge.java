package com.jaymoskitchen.climate;

import com.jaymoskitchen.Stacked;
import com.jaymoskitchen.season.Seasons;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * How cold or hot the air is at a point, in the same body heat the world pushes on a person ({@link BodyHeat#ambient}): the
 * land's own warmth with the season's chill, sun and night under the open sky, shelter, caves, height, falling snow, the
 * fires and the ice near it (through open air, never through stone) and a warmed or cooled room the point is in. It is the
 * reading of the wall gauge ({@link GaugeBlock}). Nothing here is about a person: no clothes, no wet, no meal.
 */
final class AirGauge {
	/** The air this cold gives a signal of 0 (15 in the other direction), and this hot 15: the edges of the scale, where freezing and overheating start. */
	static final float COLDEST = BodyHeat.FREEZING, HOTTEST = BodyHeat.OVERHEATING;

	private AirGauge() {
	}

	/** The signal, 0 to 15, for air of {@code air} body heat: the scale from {@link #COLDEST} to {@link #HOTTEST}. */
	static int scale(float air) {
		return Mth.clamp(Math.round((air - COLDEST) / (HOTTEST - COLDEST) * 15.0F), 0, 15);
	}

	/** The air at {@code at}, in body heat: negative is cold, positive hot. */
	static float air(ServerLevel level, BlockPos at) {
		boolean sky = level.canSeeSky(at);
		Biome.Precipitation falling = level.precipitationAt(at);
		float land;
		float base = 0.8F;
		ResourceKey<Level> realm = Stacked.realm(level, at.getY());
		if (realm == Level.NETHER) {
			Float feel = Warmth.feel(level.getBiome(at));
			land = feel != null ? feel : BodyHeat.NETHER;
		} else if (realm == Level.END) {
			land = BodyHeat.END;
		} else {
			var biome = level.getBiome(at);
			base = biome.value().getBaseTemperature();
			float t = base + Seasons.chill(base);
			Float feel = Warmth.feel(biome);
			if (feel != null) {
				land = feel;
			} else if (t < BodyHeat.COMFORT_LOW) {
				land = t >= 0.0F ? BodyHeat.COLD_AT_ZERO * (BodyHeat.COMFORT_LOW - t) / BodyHeat.COMFORT_LOW
					: BodyHeat.COLD_AT_ZERO + t * BodyHeat.FROZEN_SLOPE;
			} else if (t > BodyHeat.COMFORT_HIGH) {
				land = (t - BodyHeat.COMFORT_HIGH) * BodyHeat.HOT_SLOPE;
			} else {
				land = 0.0F;
			}
			if (sky) {
				if (level.isBrightOutside()) {
					if (falling == Biome.Precipitation.NONE) {
						land += land >= 0.0F ? BodyHeat.SUN : BodyHeat.COLD_SUN;
					}
				} else {
					land += base >= BodyHeat.DRY_LAND ? BodyHeat.DRY_NIGHT : BodyHeat.NIGHT;
				}
			} else if (at.getY() < level.getSeaLevel() - BodyHeat.CAVE_DEPTH) {
				float cave = BodyHeat.CAVE;
				if (realm == Level.OVERWORLD && at.getY() < BodyHeat.DEEP_FROM) {
					cave += (BodyHeat.DEEP_FROM - at.getY()) * BodyHeat.DEEP_WARM;
				}
				land = Mth.lerp(BodyHeat.CAVE_PULL, land * BodyHeat.SHELTER, cave);
			} else {
				land *= BodyHeat.SHELTER;
			}
			if (realm == Level.OVERWORLD && (sky || at.getY() >= level.getSeaLevel() - BodyHeat.CAVE_DEPTH)) {
				long time = level.getOverworldClockTime() % 24000L;
				land += BodyHeat.TIME_SWING * (float) Math.cos((time - 6000L) / 24000.0 * Math.PI * 2.0);
			}
			int above = at.getY() - (level.getSeaLevel() + BodyHeat.TREELINE_ABOVE_SEA);
			if (above > 0 && realm != BodyHeat.AETHER) {
				land -= above * BodyHeat.HEIGHT_COLD;
			}
			if (realm == Level.OVERWORLD && !Warmth.CLIMATE_MAP) {
				land += Warmth.north(level, at.getZ());
			}
		}
		float air = land;
		if (realm != Level.NETHER && realm != Level.END) {
			air += Warmth.damp(level, at, sky, falling != Biome.Precipitation.NONE, land);
		}
		if (falling == Biome.Precipitation.SNOW) {
			air += BodyHeat.SNOW;
		}
		// fires and lit stoves close by warm it (less out under the sky), ice and snow chill hot air
		float fire = reach(level, at, true);
		air += sky || realm == Level.NETHER ? fire * BodyHeat.OUTDOORS : fire;
		air = Warmth.roomAt(level, at, air);
		if (air >= 0.0F) {
			air -= reach(level, at, false);
		}
		return Mth.clamp(air, BodyHeat.MIN - 10.0F, BodyHeat.MAX + 10.0F);
	}

	/** The strongest heat (or chill) reaching {@code at} through open air from close by, less with distance: as {@code BodyHeat#reach} reads it for a person. */
	private static float reach(ServerLevel level, BlockPos at, boolean heat) {
		float[] best = {0.0F};
		float span = heat ? BodyHeat.FIRE_RANGE : BodyHeat.CHILL_RANGE;
		int range = heat ? BodyHeat.NEAR_RANGE : BodyHeat.CHILL_RANGE, height = heat ? BodyHeat.NEAR_HEIGHT : BodyHeat.CHILL_HEIGHT;
		int cells = heat ? BodyHeat.NEAR_CELLS : BodyHeat.CHILL_CELLS;
		Warmth.fill(level, at, List.of(at), true, new Warmth.Walk(range, height, range + height, cells, false), (pos, state) -> {
			float strength = heat ? BodyHeat.heatOf(state) : BodyHeat.chillOf(state);
			if (strength <= best[0] || heat && !BodyHeat.lit(level, pos, state)) {
				return;
			}
			double dx = pos.getX() - at.getX(), dy = pos.getY() - at.getY(), dz = pos.getZ() - at.getZ();
			float near = 1.0F - (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / span;
			best[0] = Math.max(best[0], strength * near);
		});
		return best[0];
	}
}
