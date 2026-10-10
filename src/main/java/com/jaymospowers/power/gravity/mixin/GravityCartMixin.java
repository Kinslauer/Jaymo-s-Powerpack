package com.jaymospowers.power.gravity.mixin;

import com.jaymoskitchen.horse.CartEntity;
import com.jaymospowers.power.gravity.GravityWeight;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Jaymo's Kitchen carts weigh nothing to a Gravity user: no drag pulling one by hand, and a loaded supply cart (too
 * heavy by hand for anyone else) can be taken by the shafts and kept.
 */
@Mixin(CartEntity.class)
abstract class GravityCartMixin {
	@Inject(method = "drag", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$gravityNoDrag(Entity puller, CallbackInfoReturnable<Double> cir) {
		if (GravityWeight.lightens(puller)) {
			cir.setReturnValue(0.0);
		}
	}

	/** Each tick of a hitch: a Gravity puller isn't made to let go of a loaded cart. */
	@WrapOperation(method = "serverHitch", at = @At(value = "INVOKE", target = "Lcom/jaymoskitchen/horse/CartEntity;tooHeavyByHand()Z"))
	private boolean jaymospowers$gravityKeepsHold(CartEntity cart, Operation<Boolean> original, @Local(argsOnly = true) LivingEntity puller) {
		return !GravityWeight.lightens(puller) && original.call(cart);
	}

	/** Taking it by the shafts: a Gravity user can, loaded or not. */
	@WrapOperation(method = "interact", at = @At(value = "INVOKE", target = "Lcom/jaymoskitchen/horse/CartEntity;tooHeavyByHand()Z"))
	private boolean jaymospowers$gravityTakesHold(CartEntity cart, Operation<Boolean> original, @Local(argsOnly = true) Player player) {
		return !GravityWeight.lightens(player) && original.call(cart);
	}
}
