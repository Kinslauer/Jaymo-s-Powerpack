package com.jaymospowers.mixin;

import com.jaymospowers.core.StillBody;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Andy, 2026-11-20) A player prone, sat or crouched keeps the way their body faces while the camera turns: only the
 * head follows it (StillBody; the head's reach is drawn by the client's StillBodyHeadMixin).
 */
@Mixin(LivingEntity.class)
abstract class StillBodyMixin {
	@Inject(method = "tickHeadTurn", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$stillBody(float yBodyRotT, CallbackInfo ci) {
		if ((Object)this instanceof Player player && StillBody.held(player)) {
			StillBody.turn(player, yBodyRotT);
			ci.cancel();
		}
	}
}
