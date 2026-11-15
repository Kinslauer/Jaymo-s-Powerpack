package com.jaymoskingdoms.mixin.wild;

import com.jaymoskingdoms.MarkedMonsters;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A spawner's mob is set up before it joins the world: {@link MarkedMonsters#spawned} keeps it unmarked. */
@Mixin(Mob.class)
abstract class MarkedSpawnMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void jaymoskingdoms$spawnerUnmarked(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data,
		CallbackInfoReturnable<SpawnGroupData> cir) {
		MarkedMonsters.spawned((Mob) (Object) this, reason);
	}
}
