package com.jaymoskingdoms.mixin.wild;

import com.jaymoskingdoms.wild.SpiderKeep;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A spider can't be set on one it leaves be ({@link SpiderKeep#spares}: webbed up, or kept by a lair), whoever sets it on. */
@Mixin(Mob.class)
abstract class SpiderKeepMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$leaveTheCatch(@Nullable LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (target != null && !self.level().isClientSide() && SpiderKeep.spares(self, target)) {
			ci.cancel();
		}
	}
}
