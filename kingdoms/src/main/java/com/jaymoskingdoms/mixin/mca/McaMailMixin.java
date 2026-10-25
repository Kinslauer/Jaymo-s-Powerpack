package com.jaymoskingdoms.mixin.mca;

import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.201) MCA's "You have mail! Type '/mca mail'" never shows: the post ({@link com.jaymoskingdoms.Post}) takes the
 * letters out of MCA's inbox and a pigeon or a townsman brings them.
 */
@Mixin(PlayerSaveData.class)
abstract class McaMailMixin {
	@Inject(method = "showMailNotification", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$noMailToast(ServerPlayer player, CallbackInfo ci) {
		ci.cancel();
	}
}
