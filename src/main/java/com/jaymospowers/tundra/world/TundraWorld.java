package com.jaymospowers.tundra.world;

import com.jaymospowers.JaymosPowers;
import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The tundra's place in the Overworld (Andy, 2026-11-12, after Klei_Wright's tundra concept; {@code docs/Tundra-Plan.md}).
 * Two biomes, not a dimension:
 * <ul>
 * <li>{@link #TUNDRA}: bare old rock. Deepslate from bedrock to the surface, crossed by greenstone belts (strips of
 * smooth basalt carrying banded iron), thin grass and yellow moss, boulders, lichen, no trees and no snowfall.</li>
 * <li>{@link #LICHEN_WOODLAND}: the tundra's warmer rim, and the dry edge of the snowy taiga where its spruce thins and
 * grows dwarfed (the plan's "band of snowy taiga cells", Andy 2026-11-12: "Yes" to moving it there).</li>
 * </ul>
 * They take over the driest part of what was Snowy Plains ({@link #placing}), so new chunks only. The ground itself is
 * the material rule {@code jaymospowers:tundra}, run from our copy of {@code minecraft:overworld}; the rest is
 * {@link TundraFeatures}. The blocks belong to {@code com.jaymospowers.tundra} and are looked up by id here.
 */
public final class TundraWorld {
	// ---- tuning ----
	/** Snowy Plains drier than this (humidity, in 1/10000) becomes tundra or woodland; vanilla's driest cell ends here. */
	static final long DRY_BELOW = -3500L;
	/** Snowy Taiga's driest edge (humidity, 1/10000): vanilla's frozen row turns from Snowy Plains to Snowy Taiga here. */
	static final long TAIGA_FROM = 1000L;
	/** This much of Snowy Taiga's dry edge is lichen woodland: the open, stunted forest where the taiga thins out. */
	static final long WOODLAND_BAND = 1000L;
	/** In the dry cell, colder than this (temperature, 1/10000) is tundra; the warmer rim is lichen woodland. */
	static final long TUNDRA_COLDER_THAN = -6000L;
	/** The frozen temperature row ends here: Snowy Plains only lives below it. */
	static final long FROZEN_BELOW = -4500L;

	public static final ResourceKey<Biome> TUNDRA = ResourceKey.create(Registries.BIOME, JaymosPowers.id("tundra"));
	public static final ResourceKey<Biome> LICHEN_WOODLAND = ResourceKey.create(Registries.BIOME, JaymosPowers.id("lichen_woodland"));
	/** Where a spruce sapling grows into a dwarfed spruce. */
	public static final TagKey<Biome> DWARFS_SPRUCE = TagKey.create(Registries.BIOME, JaymosPowers.id("dwarfs_spruce"));

	private TundraWorld() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("tundra_boulder"), TundraFeatures.Boulder.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("tundra_banded_vein"), TundraFeatures.BandedVein.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("tundra_lichen_patch"), TundraFeatures.LichenPatch.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("tundra_shrubs"), TundraFeatures.Shrubs.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("tundra_moss_patch"), TundraFeatures.MossPatch.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("tundra_white_ice"), TundraFeatures.WhiteIce.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("dwarfed_spruce"), TundraFeatures.DwarfSpruce.CODEC);
	}

	/** A tundra block by its path, or {@code fallback} while that block isn't registered. */
	static Block block(String path, Block fallback) {
		Block found = BuiltInRegistries.BLOCK.getValue(JaymosPowers.id(path));
		return found == null || found == Blocks.AIR ? fallback : found;
	}

	/**
	 * Wraps the Overworld biome list as it is built: each Snowy Plains cell in the dry end of the frozen row is handed
	 * out again as tundra (the cold, dry core) and lichen woodland (its warmer rim), and the dry edge of each Snowy Taiga
	 * cell in the frozen row becomes lichen woodland, so the woodland runs along the taiga where it thins out.
	 */
	public static Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> placing(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out) {
		return pair -> {
			Climate.ParameterPoint at = pair.getFirst();
			Climate.Parameter hum = at.humidity(), temp = at.temperature();
			if (temp.max() > FROZEN_BELOW || pair.getSecond() != Biomes.SNOWY_PLAINS && pair.getSecond() != Biomes.SNOWY_TAIGA) {
				out.accept(pair);
				return;
			}
			if (pair.getSecond() == Biomes.SNOWY_TAIGA) {
				if (hum.min() == TAIGA_FROM && hum.max() > TAIGA_FROM + WOODLAND_BAND) {
					out.accept(Pair.of(with(at, temp, new Climate.Parameter(TAIGA_FROM, TAIGA_FROM + WOODLAND_BAND)), LICHEN_WOODLAND));
					out.accept(Pair.of(with(at, temp, new Climate.Parameter(TAIGA_FROM + WOODLAND_BAND, hum.max())), Biomes.SNOWY_TAIGA));
				} else {
					out.accept(pair);
				}
				return;
			}
			if (hum.max() <= DRY_BELOW) {
				if (temp.min() < TUNDRA_COLDER_THAN && temp.max() > TUNDRA_COLDER_THAN) {
					out.accept(Pair.of(with(at, new Climate.Parameter(temp.min(), TUNDRA_COLDER_THAN), hum), TUNDRA));
					out.accept(Pair.of(with(at, new Climate.Parameter(TUNDRA_COLDER_THAN, temp.max()), hum), LICHEN_WOODLAND));
				} else {
					out.accept(Pair.of(at, temp.max() <= TUNDRA_COLDER_THAN ? TUNDRA : LICHEN_WOODLAND));
				}
			} else {
				out.accept(pair);
			}
		};
	}

	private static Climate.ParameterPoint with(Climate.ParameterPoint at, Climate.Parameter temperature, Climate.Parameter humidity) {
		return new Climate.ParameterPoint(temperature, humidity, at.continentalness(), at.erosion(), at.depth(), at.weirdness(), at.offset());
	}
}
