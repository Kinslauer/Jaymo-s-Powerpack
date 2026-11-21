package com.jaymoskingdoms.mobs;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.MonsterGear;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.level.LightLayer;

/**
 * What lives underground (the Clouds and Bits plan's Mobs patch): some of the spiders vanilla's natural spawning
 * places in the dark deep are cave spiders instead, and the hostile humanoids it places below y 0 come up in
 * better gear the deeper they are ({@link MonsterGear#armDeep}). Only mobs the natural spawner adds: spawners, eggs
 * and commands are left alone.
 */
public final class UnderMobs {
	/** Spiders spawning below this y, in no skylight at all, may be cave spiders. */
	static final int CAVE_SPIDER_BELOW_Y = 40;
	/** How many of those spiders are cave spiders (1 in 4). */
	static final float CAVE_SPIDER_CHANCE = 0.25F;

	private UnderMobs() {
	}

	/** What the natural spawner is about to add becomes (or is dressed as) what belongs where it stands. */
	public static Entity natural(ServerLevel level, Entity spawned) {
		if (!(spawned instanceof Mob mob)) {
			return spawned;
		}
		if (mob.getType() == EntityTypes.SPIDER && caveSpiderHere(level, mob)) {
			CaveSpider cave = EntityTypes.CAVE_SPIDER.create(level, EntitySpawnReason.NATURAL);
			if (cave != null) {
				cave.snapTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), 0.0F);
				cave.finalizeSpawn(level, level.getCurrentDifficultyAt(cave.blockPosition()), EntitySpawnReason.NATURAL, null);
				JaymosKingdoms.LOGGER.debug("a cave spider spawned at {} in place of a spider", cave.blockPosition());
				return cave;
			}
			return spawned;
		}
		MonsterGear.armDeep(level, mob);
		return spawned;
	}

	/** A plain spider (not a jockey's mount) low enough, in the dark, that this one is a cave spider. */
	private static boolean caveSpiderHere(ServerLevel level, Mob spider) {
		if (spider.isVehicle() || spider.isPassenger() || spider.getY() >= CAVE_SPIDER_BELOW_Y
			|| level.getRandom().nextFloat() >= CAVE_SPIDER_CHANCE) {
			return false;
		}
		BlockPos pos = spider.blockPosition();
		return level.getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(pos) == 0 && !level.canSeeSky(pos);
	}
}
