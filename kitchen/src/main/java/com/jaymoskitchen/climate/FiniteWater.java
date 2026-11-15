package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.BlockPos;
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
 * Dynamic Waters carves its rivers with the {@code minecraft:river} (or frozen river) biome along them, so they count as
 * rivers here and keep giving; it has no finite-water rule of its own.
 */
public final class FiniteWater {
	/** Biomes where water comes back by itself (rivers, oceans, swamps). */
	public static final TagKey<Biome> RENEWS = TagKey.create(Registries.BIOME, JaymosKitchen.id("renews_water"));

	private FiniteWater() {
	}

	/** Whether two water sources beside {@code pos} may make a new one there. */
	public static boolean renews(ServerLevel level, BlockPos pos) {
		// the block above: the water at pos is itself on the rain heightmap, so rain is never "at" it
		return level.getBiome(pos).is(RENEWS) || level.isRainingAt(pos.above());
	}
}
