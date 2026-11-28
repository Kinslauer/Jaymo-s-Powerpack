package com.jaymospowers.mixin;

import com.jaymospowers.core.Dismember;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mob or NPC missing both arms has no hands to swing, draw a bow or shield with, or strike with a weapon
 * ({@link Dismember#armless}).
 */
@Mixin(LivingEntity.class)
abstract class ArmlessLivingMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noArmSwing(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity,
		CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Mob mob && Dismember.armless(mob)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "startUsingItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noArmUse(InteractionHand hand, CallbackInfo ci) {
		if ((Object) this instanceof Mob mob && Dismember.armless(mob)) {
			ci.cancel();
		}
	}

	@Inject(method = "getWeaponItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noHeldWeapon(CallbackInfoReturnable<ItemStack> cir) {
		if ((Object) this instanceof Mob mob && Dismember.armless(mob)) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}
}
