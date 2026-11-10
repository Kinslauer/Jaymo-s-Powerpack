package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.ShyDeer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A natural spawn of Naturalist's deer stops at {@link ShyDeer#HERD_MOST} of them instead of vanilla's 4, so a whole herd
 * of 3 to 10 ({@link ShyDeer}) can come, and never more than 10 at once.
 */
@Mixin(NaturalSpawner.class)
abstract class DeerHerdMixin {
	@WrapOperation(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getMaxSpawnClusterSize()I"))
	private static int jaymoskingdoms$deerHerds(Mob mob, Operation<Integer> original) {
		int size = original.call(mob);
		return ShyDeer.isDeer(mob.getType()) ? ShyDeer.HERD_MOST : size;
	}
}
