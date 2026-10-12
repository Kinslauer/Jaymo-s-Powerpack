package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.lootgoblins.LootGoblins;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Now and then a mob vanilla spawns naturally is a loot goblin instead ({@link LootGoblins#replace}). */
@Mixin(NaturalSpawner.class)
abstract class NaturalSpawnerMixin {
	@WrapOperation(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
	private static void jaymoskingdoms$lootGoblins(ServerLevel level, Entity entity, Operation<Void> original) {
		original.call(level, LootGoblins.replace(level, entity));
	}
}
