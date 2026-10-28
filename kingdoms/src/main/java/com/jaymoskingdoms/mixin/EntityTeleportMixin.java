package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Attention;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.281) A villager a player has just dealt with isn't teleported anywhere for 30 seconds ({@link Attention#held}). */
@Mixin(Entity.class)
abstract class EntityTeleportMixin {
	@Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$held(double x, double y, double z, CallbackInfo ci) {
		if (Attention.held((Entity)(Object)this)) {
			ci.cancel();
		}
	}
}
