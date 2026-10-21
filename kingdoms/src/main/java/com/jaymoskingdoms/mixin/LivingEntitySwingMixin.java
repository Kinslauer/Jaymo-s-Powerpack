package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.BladeClash;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.105) A swing begun (at a foe or at the air), before a player's swing resets its charge: it may clash ({@link BladeClash}). */
@Mixin(LivingEntity.class)
abstract class LivingEntitySwingMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet.minecraft.world.item.component.SwingAnimation;Z)Z", at = @At("HEAD"))
	private void jaymoskingdoms$swung(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, CallbackInfoReturnable<Boolean> cir) {
		if (hand == InteractionHand.MAIN_HAND) {
			BladeClash.swung((LivingEntity) (Object) this);
		}
	}
}
