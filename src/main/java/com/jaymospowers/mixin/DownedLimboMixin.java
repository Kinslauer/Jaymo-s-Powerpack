package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.Downed;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.15.130) A blow that left a player at no health with their death turned away (a save that forgot to give any health
 * back) is caught as it lands: they go down, or die properly ({@link Downed#limbo}). Andy (2026-10-22): Jung lay stuck
 * after a meteor, past giving up and {@code /kill}.
 */
@Mixin(LivingEntity.class)
abstract class DownedLimboMixin {
	@Shadow
	protected boolean dead;

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void jaymospowers$noLimbo(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!this.dead && self.isDeadOrDying()) {
			Downed.limbo(level, self, source);
		}
	}
}
