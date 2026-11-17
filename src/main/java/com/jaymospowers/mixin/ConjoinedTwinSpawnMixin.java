package com.jaymospowers.mixin;

import com.jaymospowers.mutation.ConjoinedTwins;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the conjoined twin mutation when villagers and zombies spawn naturally.
 * Injected into {@link Mob#finalizeSpawn} alongside {@link YoungSpawnMixin}.
 */
@Mixin(Mob.class)
abstract class ConjoinedTwinSpawnMixin {
    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void jaymospowers$conjoinedTwin(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
            SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
        ConjoinedTwins.onNaturalSpawn((Mob)(Object)this, level, reason);
    }
}