package com.jaymoskingdoms.mixin.wild;

import com.jaymoskingdoms.MobGearTables;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A mob is set up: its gear table's, if a datapack gives it one ({@link MobGearTables}). */
@Mixin(Mob.class)
abstract class MobGearSpawnMixin {
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void jaymoskingdoms$gearTable(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data,
		CallbackInfoReturnable<SpawnGroupData> cir) {
		MobGearTables.spawned((Mob) (Object) this, reason, difficulty.getEffectiveDifficulty());
	}
}
