package com.jaymospowers.mixin;

import com.jaymospowers.young.Younglings;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.18.115) Some natural spawns of creatures that have young bring a brood or a calf (Younglings). */
@Mixin(Mob.class)
abstract class YoungSpawnMixin {
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void jaymospowers$young(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
		Younglings.spawned((Mob)(Object)this, level, reason);
	}
}
