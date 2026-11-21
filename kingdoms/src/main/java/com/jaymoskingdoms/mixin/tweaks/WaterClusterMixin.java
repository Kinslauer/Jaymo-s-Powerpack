package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.seas.SeaLife;
import com.jaymoskingdoms.whaling.GreatWhale;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.644) Smaller groups in the water. Andy (2026-11-07): "still too many mobs spawning at once in the water". A natural
 * spawn of a water mob (fish schools, squid, dolphins, sharks, axolotls...) stops after {@link #WATER_CLUSTER} of them,
 * where a school of fish could bring eight at once before. Fewer at a time as well as fewer in all
 * ({@link MonsterCapMixin}). Great Whales keep their pods of 1-3 (`Whaling.POD_MAX`).
 */
@Mixin(NaturalSpawner.class)
abstract class WaterClusterMixin {
	/** At most this many water mobs come in one natural spawn. */
	private static final int WATER_CLUSTER = 2;
	/** Out on the open sea ({@link SeaLife#openSea}), schools and pods come this many at most. */
	private static final int OPEN_CLUSTER = 4;

	@WrapOperation(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getMaxSpawnClusterSize()I"))
	private static int jaymoskingdoms$smallerWaterGroups(Mob mob, Operation<Integer> original) {
		int size = original.call(mob);
		if (mob instanceof GreatWhale) {
			return size;
		}
		MobCategory c = mob.getType().getCategory();
		boolean water = c == MobCategory.WATER_AMBIENT || c == MobCategory.WATER_CREATURE || c == MobCategory.UNDERGROUND_WATER_CREATURE
			|| c == MobCategory.AXOLOTLS;
		if (!water) {
			return size;
		}
		boolean open = mob.level() instanceof ServerLevel level && SeaLife.openSea(level, mob.chunkPosition());
		return Math.min(size, open ? OPEN_CLUSTER : WATER_CLUSTER);
	}
}
