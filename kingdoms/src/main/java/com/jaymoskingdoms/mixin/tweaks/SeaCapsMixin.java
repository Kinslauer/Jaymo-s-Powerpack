package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.seas.SeaLife;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Open sea chunks ask the open sea's water caps ({@link MonsterCapMixin}): the player-near cap a chunk is spawned under is
 * asked with {@link SeaLife#askingOpen(boolean)} set when the chunk is open sea; the world-wide cap (asked before any
 * chunk) by {@link SeaCapsGlobalMixin}, always as open sea's, so it never holds the open sea under the coast's numbers.
 */
@Mixin(NaturalSpawner.class)
abstract class SeaCapsMixin {
	@WrapOperation(method = "spawnForChunk", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;canSpawnForCategoryLocal(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/world/level/ChunkPos;)Z"))
	private static boolean jaymoskingdoms$openSeaCaps(NaturalSpawner.SpawnState state, MobCategory category, ChunkPos pos, Operation<Boolean> original,
		@Local(argsOnly = true) ServerLevel level) {
		if (!SeaLife.seaCategory(category) || !SeaLife.openSea(level, pos)) {
			return original.call(state, category, pos);
		}
		SeaLife.askingOpen(true);
		try {
			return original.call(state, category, pos);
		} finally {
			SeaLife.askingOpen(false);
		}
	}
}
