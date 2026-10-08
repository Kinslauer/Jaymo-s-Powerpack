package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoHooks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stasis Field: a frozen mob skips {@code aiStep} entirely (AI, jumping, travel and gravity), but still ticks
 * otherwise, so it can be hurt and killed. Nothing is saved on the entity, so a crash can't leave a mob frozen.
 */
@Mixin(LivingEntity.class)
abstract class ChronoLivingEntityMixin {
	@Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$chronoStasis(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		// (players are held in place by the field itself; their own tick must keep running)
		if (!self.level().isClientSide() && !(self instanceof net.minecraft.world.entity.player.Player) && ChronoHooks.isFrozen(self)) {
			ci.cancel();
		}
	}
}
