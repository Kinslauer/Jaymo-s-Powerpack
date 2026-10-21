package com.jaymospowers.aether.mob;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

/**
 * The three climates of the Aether's animals: the highfields, the magnetic woods and the arctic. A kirrid, burrukai or
 * taegore is one entity type with a zone, chosen from the biome tag at the spot it spawns in (the dimension makes the
 * tags, see docs/aether.md).
 */
public enum Zone {
	HIGHFIELDS("highfields"), MAGNETIC("magnetic"), ARCTIC("arctic");

	public final String id;
	private final TagKey<Biome> tag;

	Zone(String id) {
		this.id = id;
		this.tag = TagKey.create(Registries.BIOME, JaymosPowers.id("is_aether_" + id));
	}

	public static Zone byIndex(int index) {
		return values()[Math.floorMod(index, values().length)];
	}

	/** The zone of the biome at {@code pos} (the highfields anywhere that is none of them, like a spawn egg used at home). */
	public static Zone at(LevelReader level, BlockPos pos) {
		var biome = level.getBiome(pos);
		for (Zone zone : values()) {
			if (biome.is(zone.tag)) {
				return zone;
			}
		}
		return HIGHFIELDS;
	}

	/** Whether the biome at {@code pos} is in any of the three (the Aether's animal climates). */
	public static boolean in(LevelReader level, BlockPos pos) {
		var biome = level.getBiome(pos);
		for (Zone zone : values()) {
			if (biome.is(zone.tag)) {
				return true;
			}
		}
		return false;
	}

	public static TagKey<Biome> tagOf(Zone zone) {
		return zone.tag;
	}
}
