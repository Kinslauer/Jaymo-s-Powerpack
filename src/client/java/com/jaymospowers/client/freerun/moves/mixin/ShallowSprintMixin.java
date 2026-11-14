package com.jaymospowers.client.freerun.moves.mixin;

import com.jaymospowers.freerun.moves.ShallowSwim;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla won't let you sprint (or keep a sprint) in shallow water; in water that {@link ShallowSwim} swims in, you can. */
@Mixin(LocalPlayer.class)
abstract class ShallowSprintMixin {
	@WrapOperation(method = "isSprintingPossible(Z)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInShallowWater()Z"))
	private boolean jaymospowers$shallowSprint(LocalPlayer self, Operation<Boolean> original) {
		return original.call(self) && !ShallowSwim.allowed(self);
	}
}
