package com.jaymoskingdoms.mixin.tinker;

import com.jaymoskingdoms.tinker.MimicStudy;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An enderman that blinks is seen blinking: a Tinkerer watching studies it ({@link MimicStudy}). */
@Mixin(Enderman.class)
abstract class EndermanBlinkMixin {
	@Inject(method = "teleport(DDD)Z", at = @At("RETURN"))
	private void jaymoskingdoms$blinked(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !((Enderman)(Object)this).level().isClientSide()) {
			MimicStudy.blinked((Enderman)(Object)this);
		}
	}
}
