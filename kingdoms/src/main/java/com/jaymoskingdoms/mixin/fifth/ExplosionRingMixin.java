package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.Deafened;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Any explosion (a creeper's, TNT, a spell's) rings the ears of the players near it ({@link Deafened#blast}). */
@Mixin(ServerExplosion.class)
abstract class ExplosionRingMixin {
	@Inject(method = "explode", at = @At("HEAD"))
	private void jaymoskingdoms$ring(CallbackInfoReturnable<Integer> cir) {
		ServerExplosion self = (ServerExplosion) (Object) this;
		ServerLevel level = self.level();
		Deafened.blast(level, self.center(), self.radius());
	}
}
