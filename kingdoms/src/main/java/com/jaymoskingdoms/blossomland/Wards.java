package com.jaymoskingdoms.blossomland;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/**
 * The ward rope (docs/BlossomLands-Plan.md, patch 3, bl_ward): a straw rope hung with folded paper, strung across a
 * gate or round a shrine. No monster comes out of the dark within {@link #REACH} blocks of one: the game's own
 * night spawns there are turned away ({@code NaturalSpawnerMixin}). It does nothing to a monster that walks in, to
 * spawners, raids or anything the Nether or the End breeds.
 */
public final class Wards {
	/** How far a ward rope keeps the dark's spawns off (blocks). */
	public static final int REACH = 12;

	private Wards() {
	}

	/** Whether {@code entity}, about to be spawned by the game's own mob spawning, is a monster too near a ward rope. */
	public static boolean turnsAway(ServerLevel level, Entity entity) {
		return entity instanceof Enemy && level.dimension() == Level.OVERWORLD
			&& level.getPoiManager().getCountInRange(type -> type.is(BlossomBlocks.WARD), entity.blockPosition(), REACH, PoiManager.Occupancy.ANY) > 0;
	}
}
