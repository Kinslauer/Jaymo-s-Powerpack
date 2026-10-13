package com.jaymospowers.power.darktide.mixin;

import com.jaymospowers.power.darktide.DarktidePower;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A risen shadow never takes its master (or a fellow shadow) as its target, whatever its own AI wants. */
@Mixin(Mob.class)
abstract class DarktideRisenTargetMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$risenSpareMaster(@Nullable LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (target != null && !self.level().isClientSide() && DarktidePower.risenSpares(self, target)) {
			ci.cancel();
		}
	}
}
