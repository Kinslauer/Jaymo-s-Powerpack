package com.jaymoskingdoms.mixin.graveyard;

import com.jaymoskingdoms.NightmareTell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Graveyard's Nightmare turns every shot away in its own hurtServer, before Fabric's damage events would hear of it,
 * so the tell ({@link NightmareTell}) is hooked there. Skipped without The Graveyard.
 */
@Pseudo
@Mixin(targets = "com.lion.graveyard.entities.NightmareEntity", remap = false)
abstract class NightmareShotMixin {
	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
		at = @At("HEAD"), require = 0)
	private void jaymoskingdoms$shotThrough(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		NightmareTell.struck((LivingEntity)(Object)this, level, source);
	}
}
