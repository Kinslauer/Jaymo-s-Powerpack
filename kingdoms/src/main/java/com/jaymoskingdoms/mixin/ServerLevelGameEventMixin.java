package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Awareness;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.155) Zombie awareness's hearing ({@link Awareness#heard}): every game event (the same ones sculk sensors feel) comes
 * through here, and the noisy ones draw the zombies that hear them.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelGameEventMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V", at = @At("HEAD"))
	private void jaymoskingdoms$heard(Holder<GameEvent> event, Vec3 at, GameEvent.Context context, CallbackInfo ci) {
		Awareness.heard((ServerLevel) (Object) this, event, at, context);
	}
}
