package com.jaymospowers.mixin;

import com.jaymospowers.core.MountLeap;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The one place a rider's own Sneak gets them off their mount (server side): the flying dismount ({@link MountLeap}) is only for that. */
@Mixin(Player.class)
abstract class MountLeapSneakMixin {
	@Inject(method = "rideTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;stopRiding()V"))
	private void jaymospowers$sneakedOff(CallbackInfo ci) {
		MountLeap.sneakedOff((Player)(Object)this);
	}
}
