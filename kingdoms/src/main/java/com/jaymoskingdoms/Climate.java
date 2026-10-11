package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The land a faction's seat stands in, as its people dress for it: the same reading of the land as its buildings'
 * {@link Palette} (a bell by a river counts the town's own biome). Folk carry it (synced) so the client can pick their
 * skins: sun-darkened faces in the desert, pale ones in the snow. Saved by ordinal: only add at the end.
 */
public enum Climate {
	PLAINS, TAIGA, SNOWY, SAVANNA, DESERT, BADLANDS, JUNGLE, SWAMP, CHERRY;

	/** Skin groups: the client draws {@link #TEMPERATE}, {@link #SUNNY} and {@link #COLD} faces. */
	public static final int TEMPERATE = 0, SUNNY = 1, COLD = 2;

	/** The climate of the land round {@code seat}. */
	static Climate at(ServerLevel level, BlockPos seat) {
		Palette p = Palette.at(level, seat);
		if (p == Palette.TAIGA) {
			return TAIGA;
		} else if (p == Palette.SNOWY) {
			return SNOWY;
		} else if (p == Palette.SAVANNA) {
			return SAVANNA;
		} else if (p == Palette.DESERT) {
			return DESERT;
		} else if (p == Palette.BADLANDS) {
			return BADLANDS;
		} else if (p == Palette.JUNGLE) {
			return JUNGLE;
		} else if (p == Palette.SWAMP) {
			return SWAMP;
		} else if (p == Palette.CHERRY) {
			return CHERRY;
		}
		return PLAINS;
	}

	public static Climate byId(int id) {
		return id >= 0 && id < values().length ? values()[id] : PLAINS;
	}

	/** Hot, dry land: the desert and the badlands (where the sand-coloured gear and the Slavic Armory's eastern harness go). */
	public boolean arid() {
		return this == DESERT || this == BADLANDS;
	}

	/** Which skins its people wear. */
	public int skinGroup() {
		return switch (this) {
			case DESERT, BADLANDS, SAVANNA, JUNGLE -> SUNNY;
			case SNOWY -> COLD;
			default -> TEMPERATE;
		};
	}
}
