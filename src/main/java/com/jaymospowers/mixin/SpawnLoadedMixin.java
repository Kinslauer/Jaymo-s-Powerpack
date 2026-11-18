package com.jaymospowers.mixin;

import com.jaymospowers.core.Loaded;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Natural spawning reads the biome and the structures at each spot a group walks to, up to six blocks from where it began,
 * so at the edge of the loaded land it reached a chunk that was only scheduled, and the server thread waited for it to be
 * made (the Deck's 2026-11-18 freezes: about 330 profile samples under spawning, most of them in the stacked world). A
 * spawn attempt now stops at a spot, or in a chunk, that isn't made and loaded; the next attempt picks another.
 */
@Mixin(NaturalSpawner.class)
abstract class SpawnLoadedMixin {
	@Inject(method = "spawnCategoryForChunk", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$chunkMade(MobCategory category, ServerLevel level, LevelChunk chunk,
		NaturalSpawner.SpawnPredicate filter, NaturalSpawner.AfterSpawnCallback callback, CallbackInfo ci) {
		if (!Loaded.made(level, chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ())) {
			ci.cancel();
		}
	}

	@Inject(method = "getRandomSpawnMobAt", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$spotMade(ServerLevel level, StructureManager structures, ChunkGenerator generator,
		MobCategory category, RandomSource random, BlockPos pos, CallbackInfoReturnable<Optional<MobSpawnSettings.SpawnerData>> cir) {
		if (!Loaded.made(level, pos)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
