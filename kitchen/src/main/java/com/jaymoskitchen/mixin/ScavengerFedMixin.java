package com.jaymoskitchen.mixin;

import com.jaymoskitchen.corpse.Scavengers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A scavenger that has just eaten a part of a body ({@link Scavengers#sated}) takes up no new target for a while: a fed
 * zombie or wolf stops hunting until it is hungry again (or something hurts it).
 */
@Mixin(Mob.class)
abstract class ScavengerFedMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$fed(@Nullable LivingEntity target, CallbackInfo ci) {
		if (target != null && Scavengers.sated((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
