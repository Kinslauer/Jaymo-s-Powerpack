package com.jaymospowers.mixin;

import com.jaymospowers.core.brawl.Feints;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every arm swing that starts, a player's or a mob's, is noted for the fighter brain ({@link Feints}): a swing that does no damage
 * is a whiff or a feint, and the fighters near it react.
 */
@Mixin(LivingEntity.class)
abstract class SwingNoteMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("RETURN"))
	private void jaymospowers$noteSwing(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			Feints.swung((LivingEntity) (Object) this);
		}
	}
}
