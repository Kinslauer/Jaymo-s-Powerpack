package com.jaymospowers.client.freerun.moves.mixin;

import com.jaymospowers.freerun.moves.Hiding;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Someone hiding in leaves or hay ({@link Hiding}) shows no name tag over the block. */
@Mixin(LivingEntityRenderer.class)
abstract class HidingNameMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$hiddenName(LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player player && Hiding.hidden(player)) {
			cir.setReturnValue(false);
		}
	}
}
