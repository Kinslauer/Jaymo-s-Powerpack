package com.jaymospowers.bits.mixin;

import com.jaymospowers.bits.SoulBurn;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Soul fire burns blue: a longer burn than the one an entity has is a new ignition, which notes what lit it ({@link SoulBurn}). */
@Mixin(Entity.class)
abstract class EntityIgniteMixin {
	@Inject(method = "setRemainingFireTicks", at = @At("HEAD"))
	private void jaymospowers$soulBurn(int ticks, CallbackInfo ci) {
		Entity self = (Entity)(Object)this;
		if (ticks > self.getRemainingFireTicks() && !self.level().isClientSide()) {
			SoulBurn.ignited(self);
		}
	}
}
