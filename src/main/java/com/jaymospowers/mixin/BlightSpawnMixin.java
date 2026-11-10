package com.jaymospowers.mixin;

import com.jaymospowers.core.mobs.BlightMobs;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (Blight mobs) Natural spawns may become blighted elites. */
@Mixin(Mob.class)
abstract class BlightSpawnMixin {
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void jaymospowers$blight(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
		BlightMobs.spawned((Mob)(Object)this, level, reason);
	}
}