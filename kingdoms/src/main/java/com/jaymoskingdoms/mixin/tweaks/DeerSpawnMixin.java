package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.ShyDeer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.608, 0.7.633) Fewer deer, in whole herds, none in the Nether and none round a home they've been scared off
 * ({@link ShyDeer#mayGraze}). Only Naturalist's deer spawning on their own (naturally or with a new chunk); spawn eggs,
 * spawners and commands are untouched.
 */
@Mixin(SpawnPlacements.class)
abstract class DeerSpawnMixin {
	@ModifyReturnValue(method = "checkSpawnRules", at = @At("RETURN"))
	private static boolean jaymoskingdoms$fewerDeer(boolean ok, EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!ok || reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION || !ShyDeer.isDeer(type)) {
			return ok;
		}
		return ShyDeer.mayGraze(level, reason, pos);
	}
}
