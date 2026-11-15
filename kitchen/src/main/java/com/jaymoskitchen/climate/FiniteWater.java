package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Water you can't copy (Andy's GT New Horizons picks, patch 3): two water sources make a third only where water comes
 * back by itself, in a river, the sea or a swamp ({@link #RENEWS}), or open to the sky while it rains there. Anywhere
 * else a pool holds the water put in it and no more, so a lone pond drunk dry by bottles and waterskins stays dry. The
 * game rule {@code waterSourceConversion} still switches it off everywhere. Hooked in {@code FiniteWaterMixin}.
 * <p>
 * Dynamic Waters paints the river biome only down the middle of its channels, so a spot counts as river water if the
 * river biome lies within {@link #BANK} blocks of it; beaches count with the sea. A pond that settled in dry weather
 * is woken by rain ({@code RainRefillMixin}). Dynamic Waters has no finite-water rule of its own.
 */
public final class FiniteWater {
	/** Biomes where water comes back by itself (rivers, oceans, swamps). */
	public static final TagKey<Biome> RENEWS = TagKey.create(Registries.BIOME, JaymosKitchen.id("renews_water"));

	/** How far (blocks) from a river's, the sea's or a swamp's biome a spot still counts as that water. */
	static final int BANK = 6;

	private FiniteWater() {
	}

	/** Whether two water sources beside {@code pos} may make a new one there. */
	public static boolean renews(ServerLevel level, BlockPos pos) {
		// the block above: the water at pos is itself on the rain heightmap, so rain is never "at" it
		if (level.getBiome(pos).is(RENEWS) || raining(level, pos)) {
			return true;
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (level.getBiome(pos.relative(side, BANK)).is(RENEWS)) {
				return true;
			}
		}
		return false;
	}

	/** Whether rain falls on {@code pos}'s water (open sky, a biome that rains, not snows). */
	public static boolean raining(ServerLevel level, BlockPos pos) {
		return level.isRaining() && level.isRainingAt(pos.above());
	}
}
