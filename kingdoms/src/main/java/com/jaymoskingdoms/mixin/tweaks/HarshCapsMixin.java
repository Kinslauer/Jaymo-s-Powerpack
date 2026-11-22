package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.HarshLands;
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
 * The harsh lands' monster caps ({@link MonsterCapMixin}): the player-near monster cap a chunk is spawned under is asked
 * with {@link HarshLands#asking(int)} set to the chunk's steps; the world-wide cap by {@link HarshCapsGlobalMixin}, always as
 * the harshest land's, so it never holds the far lands under the temperate lands' numbers.
 */
@Mixin(NaturalSpawner.class)
abstract class HarshCapsMixin {
	@WrapOperation(method = "spawnForChunk", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;canSpawnForCategoryLocal(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/world/level/ChunkPos;)Z"))
	private static boolean jaymoskingdoms$harshCaps(NaturalSpawner.SpawnState state, MobCategory category, ChunkPos pos, Operation<Boolean> original,
		@Local(argsOnly = true) ServerLevel level) {
		if (category != MobCategory.MONSTER) {
			return original.call(state, category, pos);
		}
		HarshLands.asking(HarshLands.capSteps(level, pos));
		try {
			return original.call(state, category, pos);
		} finally {
			HarshLands.asking(-1);
		}
	}
}
