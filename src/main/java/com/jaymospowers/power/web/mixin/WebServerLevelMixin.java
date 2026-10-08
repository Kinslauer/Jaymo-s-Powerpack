package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.WebSymbiote;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loud things (bells, note blocks, horns, explosions) are heard by any symbiote nearby (see WebSymbiote). */
@Mixin(ServerLevel.class)
abstract class WebServerLevelMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
		at = @At("HEAD"))
	private void jaymospowers$webSymbioteHears(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context, CallbackInfo ci) {
		WebSymbiote.onGameEvent((ServerLevel)(Object)this, gameEvent, position);
	}
}
