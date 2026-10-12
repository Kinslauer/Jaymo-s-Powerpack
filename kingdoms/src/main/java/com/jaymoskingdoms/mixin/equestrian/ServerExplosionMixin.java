package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.equestrian.Equestrian;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.34) Horses near a blast may shy ({@link Equestrian#blast}). */
@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {
	@Inject(method = "explode", at = @At("HEAD"))
	private void jaymoskingdoms$frighten(CallbackInfoReturnable<Integer> cir) {
		ServerExplosion self = (ServerExplosion)(Object)this;
		Equestrian.blast(self.level(), self.center(), self.radius());
	}
}
