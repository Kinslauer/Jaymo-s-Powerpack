package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.MobTweaks;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.103) Far-off thunder where an enderman lands ({@link MobTweaks#teleported}); every teleport goes through here. */
@Mixin(Enderman.class)
abstract class EndermanThunderMixin {
	@Inject(method = "teleport(DDD)Z", at = @At("RETURN"))
	private void jaymoskingdoms$thunder(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			MobTweaks.teleported((Enderman)(Object)this);
		}
	}
}
