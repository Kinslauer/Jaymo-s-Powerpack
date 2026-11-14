package com.jaymospowers.mixin;

import com.jaymospowers.core.BlindHead;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** What a headless undead hears (see BlindHead): every game event, the same ones a sculk sensor feels, comes through here. */
@Mixin(ServerLevel.class)
abstract class DismemberGameEventMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V", at = @At("HEAD"))
	private void jaymospowers$heard(Holder<GameEvent> event, Vec3 at, GameEvent.Context context, CallbackInfo ci) {
		BlindHead.heard((ServerLevel) (Object) this, event, at, context);
	}
}
