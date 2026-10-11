package com.jaymospowers.power.resonance.mixin;

import com.jaymospowers.power.resonance.ResonanceHush;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hush and Footstep sense: vibrations that nothing (no sculk sensor, shrieker or Warden) gets to hear. */
@Mixin(ServerLevel.class)
abstract class ResonanceGameEventMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$resonanceHush(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
		if (ResonanceHush.muffles((ServerLevel) (Object) this, event, pos, context)) {
			ci.cancel();
		}
	}
}
