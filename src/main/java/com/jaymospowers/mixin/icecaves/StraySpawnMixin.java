package com.jaymospowers.mixin.icecaves;

import com.jaymospowers.frost.IceCaves;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Strays spawn in the ice caves without the sky over them (they need it everywhere else). */
@Mixin(Stray.class)
abstract class StraySpawnMixin {
	@Inject(method = "checkStraySpawnRules", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$iceCaves(EntityType<Stray> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
		RandomSource random, CallbackInfoReturnable<Boolean> cir) {
		if (IceCaves.straySpawnsHere(level, pos)) {
			cir.setReturnValue(Monster.checkMonsterSpawnRules(type, level, reason, pos, random));
		}
	}
}
