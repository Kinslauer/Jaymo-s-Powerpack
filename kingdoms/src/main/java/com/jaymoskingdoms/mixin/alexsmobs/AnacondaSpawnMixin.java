package com.jaymoskingdoms.mixin.alexsmobs;

import com.jaymoskingdoms.wild.Anacondas;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Alex's Mobs' anaconda keeps to its homes ({@link Anacondas#spawnRule}): anywhere on the ground in a jungle, and in a
 * river only with a jungle near. Skipped without Alex's Mobs.
 */
@Pseudo
@Mixin(targets = "com.github.alexthe666.alexsmobs.entity.EntityAnaconda", remap = false)
abstract class AnacondaSpawnMixin {
	@Inject(method = "canAnacondaSpawn", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymoskingdoms$jungleHomes(EntityType<?> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random, CallbackInfoReturnable<Boolean> cir) {
		Boolean rule = Anacondas.spawnRule(level, reason, pos);
		if (rule != null) {
			cir.setReturnValue(rule);
		}
	}
}
