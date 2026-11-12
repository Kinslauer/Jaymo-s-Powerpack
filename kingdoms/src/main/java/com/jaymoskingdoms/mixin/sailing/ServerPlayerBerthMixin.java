package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.sailing.Berths;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's berth ({@link Berths}) follows their respawn point: a bed slept in, a story's forced spawn, or none. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerBerthMixin {
	@Inject(method = "setRespawnPosition", at = @At("TAIL"))
	private void jaymoskingdoms$berth(ServerPlayer.@Nullable RespawnConfig config, boolean showMessage, CallbackInfo ci) {
		Berths.respawnSet((ServerPlayer)(Object)this, config);
	}

	@Inject(method = "copyRespawnPosition", at = @At("HEAD"))
	private void jaymoskingdoms$copyingStart(ServerPlayer from, CallbackInfo ci) {
		Berths.copying(true);
	}

	@Inject(method = "copyRespawnPosition", at = @At("RETURN"))
	private void jaymoskingdoms$copyingEnd(ServerPlayer from, CallbackInfo ci) {
		Berths.copying(false);
	}
}
