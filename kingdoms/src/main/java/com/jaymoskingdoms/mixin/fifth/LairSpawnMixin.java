package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.LairLand;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Animals rarely spawn in the land of a great lair ({@link LairLand#scarce}). */
@Mixin(NaturalSpawner.class)
abstract class LairSpawnMixin {
	@Inject(method = "canSpawnMobAt", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$bareLand(ServerLevel level, net.minecraft.world.level.StructureManager structures,
		net.minecraft.world.level.chunk.ChunkGenerator generator, MobCategory category, net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData data,
		BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (category == MobCategory.CREATURE && LairLand.scarce(level, pos)) {
			cir.setReturnValue(false);
		}
	}
}
